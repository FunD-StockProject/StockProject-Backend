package com.fund.stockProject.user.service;

import com.fund.stockProject.auth.service.AuthService;
import com.fund.stockProject.global.service.S3Service;
import com.fund.stockProject.user.dto.UserProfileResponse;
import com.fund.stockProject.user.dto.UserUpdateRequest;
import com.fund.stockProject.user.entity.User;
import com.fund.stockProject.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final S3Service s3Service;

    @Transactional
    public UserProfileResponse updateMyProfile(UserUpdateRequest request) {
        String email = AuthService.getCurrentUserEmail();
        if (email == null) {
            throw new IllegalStateException("Authentication required");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + email));

        user.updateProfile(request.getNickname(), request.getBirthDate(), request.getMarketingAgreement());
        userRepository.save(user);

        return new UserProfileResponse(
                user.getEmail(),
                user.getNickname(),
                user.getBirthDate(),
                user.getMarketingAgreement(),
                user.getProfileImageUrl()
        );
    }

    @Transactional
    public UserProfileResponse updateProfileImage(MultipartFile image) {
        String email = AuthService.getCurrentUserEmail();
        if (email == null) {
            throw new IllegalStateException("Authentication required");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + email));

        if (image != null && !image.isEmpty()) {
            String imageUrl = s3Service.uploadUserImage(image, "users");
            user.updateProfileImage(imageUrl);
            userRepository.save(user);
        }

        return new UserProfileResponse(
                user.getEmail(),
                user.getNickname(),
                user.getBirthDate(),
                user.getMarketingAgreement(),
                user.getProfileImageUrl()
        );
    }
}
