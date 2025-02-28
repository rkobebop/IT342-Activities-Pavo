package com.pavo.GoogleIntegration.usercontroller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class UserController {

    private final OAuth2AuthorizedClientService authorizedClientService;

    public UserController(OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
    }

    @GetMapping("/")
    public String index() {
        return "index"; // Serves index.html
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
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getName()
        );

        String accessToken = client.getAccessToken().getTokenValue();

        // Call Google People API to get contacts
        String url = "https://people.googleapis.com/v1/people/me/connections"
                + "?personFields=names,emailAddresses,phoneNumbers"
                + "&access_token=" + accessToken;

        RestTemplate restTemplate = new RestTemplate();
        Map<String, Object> response = restTemplate.getForObject(url, Map.class);

        List<Map<String, String>> contactsList = new ArrayList<>();
        if (response != null && response.containsKey("connections")) {
            List<Map<String, Object>> connections = (List<Map<String, Object>>) response.get("connections");

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

                contactsList.add(Map.of("name", name, "email", email, "phone", phone));
            }
        }

        model.addAttribute("contacts", contactsList);
        return "contacts"; // Returns contacts.html
    }
}
