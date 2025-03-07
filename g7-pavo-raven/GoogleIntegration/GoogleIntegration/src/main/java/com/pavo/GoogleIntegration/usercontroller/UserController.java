package com.pavo.GoogleIntegration.usercontroller;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Controller
public class UserController {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final RestTemplate restTemplate;

    public UserController(OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
        this.restTemplate = createRestTemplate(); // Initialize RestTemplate with PATCH support
    }

    // Configure RestTemplate to support PATCH
    private RestTemplate createRestTemplate() {
        HttpClient httpClient = HttpClients.createDefault();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        return new RestTemplate(requestFactory);
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/user-info")
    public String getUser(@AuthenticationPrincipal OAuth2User oAuth2User, Model model) {
        if (oAuth2User != null) {
            model.addAttribute("name", oAuth2User.getAttribute("name"));
            model.addAttribute("firstName", oAuth2User.getAttribute("given_name"));
            model.addAttribute("lastName", oAuth2User.getAttribute("family_name"));
            model.addAttribute("email", oAuth2User.getAttribute("email"));
        }
        return "user-info";
    }

    @GetMapping("/contacts")
    public String getContacts(Model model, OAuth2AuthenticationToken authentication) {
        if (authentication == null) {
            model.addAttribute("error", "User not authenticated.");
            return "contacts";
        }

        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getName()
        );

        if (client == null || client.getAccessToken() == null) {
            model.addAttribute("error", "OAuth2 client not found or access token is missing.");
            return "contacts";
        }

        String accessToken = client.getAccessToken().getTokenValue();
        String url = "https://people.googleapis.com/v1/people/me/connections?personFields=names,emailAddresses,phoneNumbers";
        List<Map<String, String>> contactsList = new ArrayList<>();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getBody() != null && response.getBody().containsKey("connections")) {
                List<Map<String, Object>> connections = (List<Map<String, Object>>) response.getBody().get("connections");

                for (Map<String, Object> person : connections) {
                    String name = person.containsKey("names") ?
                            ((List<Map<String, Object>>) person.get("names")).get(0).get("displayName").toString() :
                            "Unknown";
                    String email = person.containsKey("emailAddresses") ?
                            ((List<Map<String, Object>>) person.get("emailAddresses")).get(0).get("value").toString() :
                            "N/A";
                    String phone = person.containsKey("phoneNumbers") ?
                            ((List<Map<String, Object>>) person.get("phoneNumbers")).get(0).get("value").toString() :
                            "N/A";
                    String resourceName = person.containsKey("resourceName") ?
                            person.get("resourceName").toString() :
                            null;

                    if (resourceName != null) {
                        contactsList.add(Map.of("name", name, "email", email, "phone", phone, "resourceName", resourceName));
                    }
                }
            } else {
                model.addAttribute("error", "No contacts found.");
            }
        } catch (Exception e) {
            model.addAttribute("error", "Failed to retrieve contacts: " + e.getMessage());
        }

        model.addAttribute("contacts", contactsList);
        return "contacts";
    }

    @GetMapping("/contacts/add-form")
    public String showAddContactForm() {
        return "add-contact";
    }

    @PostMapping("/contacts/add")
    public String addContact(@RequestParam String name, @RequestParam String email, @RequestParam String phone, OAuth2AuthenticationToken authentication) {
        if (authentication == null) {
            return "redirect:/contacts?error=User not authenticated";
        }

        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getName()
        );

        if (client == null || client.getAccessToken() == null) {
            return "redirect:/contacts?error=OAuth2 client not found or access token is missing";
        }

        String accessToken = client.getAccessToken().getTokenValue();
        String url = "https://people.googleapis.com/v1/people:createContact";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("names", List.of(Map.of("givenName", name)));
        requestBody.put("emailAddresses", List.of(Map.of("value", email)));
        requestBody.put("phoneNumbers", List.of(Map.of("value", phone)));

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return "redirect:/contacts?success=Contact added successfully";
            } else {
                return "redirect:/contacts?error=Failed to add contact";
            }
        } catch (HttpClientErrorException e) {
            return "redirect:/contacts?error=" + URLEncoder.encode(e.getResponseBodyAsString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "redirect:/contacts?error=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
        }
    }

    @PostMapping("/contacts/delete")
    public String deleteContact(@RequestParam String resourceName, OAuth2AuthenticationToken authentication) {
        if (authentication == null) {
            return "redirect:/contacts?error=User not authenticated";
        }

        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getName()
        );

        if (client == null || client.getAccessToken() == null) {
            return "redirect:/contacts?error=OAuth2 client not found or access token is missing";
        }

        String accessToken = client.getAccessToken().getTokenValue();
        String url = "https://people.googleapis.com/v1/" + resourceName + ":deleteContact";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
            return "redirect:/contacts?success=Contact deleted successfully";
        } catch (HttpClientErrorException e) {
            return "redirect:/contacts?error=Failed to delete contact: " + e.getStatusCode();
        } catch (Exception e) {
            return "redirect:/contacts?error=Failed to delete contact: " + e.getMessage();
        }
    }

    @PostMapping("/contacts/update")
    public String updateContact(@RequestParam String resourceName,
                                @RequestParam String name,
                                @RequestParam String email,
                                @RequestParam String phone,
                                OAuth2AuthenticationToken authentication) {
        if (authentication == null) {
            return "redirect:/contacts?error=User not authenticated";
        }

        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getName()
        );

        if (client == null || client.getAccessToken() == null) {
            return "redirect:/contacts?error=OAuth2 client not found or access token is missing";
        }

        String accessToken = client.getAccessToken().getTokenValue();

        if (!resourceName.startsWith("people/")) {
            return "redirect:/contacts?error=Invalid resource name";
        }

        String contactUrl = "https://people.googleapis.com/v1/" + resourceName + "?personFields=names,emailAddresses,phoneNumbers";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        try {
            // Fetch current contact details to get the `etag`
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(contactUrl, HttpMethod.GET, entity, Map.class);
            Map<String, Object> contactData = response.getBody();

            if (contactData == null || !contactData.containsKey("etag")) {
                return "redirect:/contacts?error=Contact not found or missing etag";
            }

            String etag = (String) contactData.get("etag");

            // Construct the update request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("etag", etag);
            requestBody.put("names", List.of(Map.of("givenName", name)));
            requestBody.put("emailAddresses", List.of(Map.of("value", email)));
            requestBody.put("phoneNumbers", List.of(Map.of("value", phone)));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // Use PATCH with the correct URL
            String updateUrl = "https://people.googleapis.com/v1/" + resourceName + ":updateContact?updatePersonFields=names,emailAddresses,phoneNumbers";

            restTemplate.exchange(updateUrl, HttpMethod.PATCH, request, String.class);

            return "redirect:/contacts?success=Contact updated successfully";
        } catch (HttpClientErrorException e) {
            return "redirect:/contacts?error=" + URLEncoder.encode(e.getResponseBodyAsString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "redirect:/contacts?error=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
        }
    }
}