package com.app.newsapp.service;

import com.app.newsapp.model.AuthProvider;
import com.app.newsapp.model.User;
import com.app.newsapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest)
            throws OAuth2AuthenticationException {

        OAuth2User oAuth2User = super.loadUser(userRequest);

        System.out.println(
                "🔥 OAuth Attributes: " + oAuth2User.getAttributes()
        );

        String registrationId = userRequest
                .getClientRegistration()
                .getRegistrationId();

        String providerStr = registrationId.toUpperCase();

        AuthProvider provider = AuthProvider.valueOf(providerStr);

        String email = oAuth2User.getAttribute("email");

        String name = oAuth2User.getAttribute("name");


        if (name == null || name.isBlank()) {
            name = oAuth2User.getAttribute("login");
        }

        if ("github".equalsIgnoreCase(registrationId)
                && (email == null || email.isBlank())) {

            System.out.println(
                    "⚠️ GitHub profile email missing. Fetching /user/emails..."
            );

            email = fetchGitHubEmail(userRequest);
        }

        if (email == null || email.isBlank()) {

            throw new OAuth2AuthenticationException(
                    "Email not found from OAuth2 provider"
            );
        }

        System.out.println(
                "✅ Resolved OAuth Email: " + email
        );

        Optional<User> userOptional =
                userRepository.findByEmail(email);

        User user;

        if (userOptional.isPresent()) {

            user = userOptional.get();

            boolean needsSave = false;

            if (user.getProvider() == null
                    || user.getProvider() != provider) {

                user.setProvider(provider);
                needsSave = true;
            }

            if (!user.isEnabled()) {

                user.setEnabled(true);
                needsSave = true;
            }

            if (needsSave) {
                userRepository.save(user);
            }

        } else {

            user = new User();

            user.setEmail(email);
            user.setName(name);
            user.setProvider(provider);
            user.setPassword("");
            user.setEnabled(true);

            userRepository.save(user);
        }




        Map<String, Object> attributes =
                new HashMap<>(oAuth2User.getAttributes());

        attributes.put("email", email);

        return new DefaultOAuth2User(
                oAuth2User.getAuthorities(),
                attributes,
                userRequest
                        .getClientRegistration()
                        .getProviderDetails()
                        .getUserInfoEndpoint()
                        .getUserNameAttributeName()
        );
    }


    private String fetchGitHubEmail(
            OAuth2UserRequest userRequest) {

        try {

            String accessToken = userRequest
                    .getAccessToken()
                    .getTokenValue();

            HttpHeaders headers = new HttpHeaders();

            headers.setBearerAuth(accessToken);

            headers.setAccept(
                    List.of(MediaType.APPLICATION_JSON)
            );

            HttpEntity<Void> entity =
                    new HttpEntity<>(headers);

            RestTemplate restTemplate =
                    new RestTemplate();

            ResponseEntity<List> response =
                    restTemplate.exchange(
                            "https://api.github.com/user/emails",
                            HttpMethod.GET,
                            entity,
                            List.class
                    );

            List<Map<String, Object>> emails =
                    response.getBody();

            System.out.println(
                    "🔥 GitHub Email Response: " + emails
            );

            if (emails == null || emails.isEmpty()) {
                return null;
            }



            for (Map<String, Object> emailData : emails) {

                Boolean primary =
                        (Boolean) emailData.get("primary");

                Boolean verified =
                        (Boolean) emailData.get("verified");

                if (Boolean.TRUE.equals(primary)
                        && Boolean.TRUE.equals(verified)) {

                    return (String) emailData.get("email");
                }
            }



            for (Map<String, Object> emailData : emails) {

                Boolean verified =
                        (Boolean) emailData.get("verified");

                if (Boolean.TRUE.equals(verified)) {

                    return (String) emailData.get("email");
                }
            }

        } catch (Exception e) {

            System.err.println(
                    "❌ GitHub email fetch failed: "
                            + e.getMessage()
            );
        }

        return null;
    }
}