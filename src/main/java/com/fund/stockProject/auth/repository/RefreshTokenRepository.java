package com.fund.stockProject.auth.repository;

import com.fund.stockProject.auth.entity.RefreshToken;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    @Transactional
    void deleteByRefreshToken(String refreshToken);
    Optional<RefreshToken> findByRefreshToken(String refreshToken);

    // 이메일 기반 삭제
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken r WHERE r.email = :email")
    void deleteByEmail(@Param("email") String email);
}
