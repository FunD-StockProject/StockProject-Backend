package com.fund.stockProject.auth.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class PasswordResetToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    private String token;

    private LocalDateTime expiresAt;

    private Boolean isUsed;

    @Builder
    public PasswordResetToken(Long id, String email, String token, LocalDateTime expiresAt, Boolean isUsed) {
        this.id = id;
        this.email = email;
        this.token = token;
        this.expiresAt = expiresAt;
        this.isUsed = isUsed;
    }

    public void setIsUsed() {
        this.isUsed = true;
    }
}
