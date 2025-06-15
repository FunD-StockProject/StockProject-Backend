package com.fund.stockProject.auth.service;

import com.fund.stockProject.auth.domain.PROVIDER;
import com.fund.stockProject.auth.entity.User;
import com.fund.stockProject.auth.oauth2.OAuth2UserInfo;
import com.fund.stockProject.auth.oauth2.OAuth2UserInfoFactory;
import com.fund.stockProject.auth.repository.UserRepository;
import com.fund.stockProject.security.principle.CustomPrincipal;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import static com.fund.stockProject.auth.domain.ROLE.ROLE_TEMP;
import static com.fund.stockProject.auth.domain.ROLE.ROLE_USER;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oauth2User = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String socialAccessToken = userRequest.getAccessToken().getTokenValue();


        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(registrationId, oauth2User.getAttributes());

        String email = userInfo.getEmail();
        String nickname = userInfo.getNickname();
        String providerId = userInfo.getProviderId();

        User user = userRepository.findByEmail(email).orElse(null);

        Boolean isNewUser = false;

        if (user == null) {
            isNewUser = true; // 새 사용자 여부
            // 새 사용자라면 DB에 저장
            user = User.builder()
                    .email(email)
                    .nickname(nickname)
                    .role(ROLE_TEMP)
                    .provider(PROVIDER.fromString(registrationId))
                    .providerId(providerId)
                    .isActive(false)
                    .marketingAgreement(false)
                    .build();
        }

        if (user.getRole() == ROLE_TEMP) {
            isNewUser = true; // 임시 사용자 여부
        }

        userRepository.save(user); // DB 저장 또는 업데이트

        return new CustomPrincipal(user, oauth2User.getAttributes(), socialAccessToken, isNewUser);
    }
}