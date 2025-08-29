package com.fund.stockProject.notification.repository;

import com.fund.stockProject.notification.entity.UserDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, Integer> {
    @Query("select t.token from UserDeviceToken t where t.user.id = :userId and t.isActive = true")
    List<String> findActiveTokens(@Param("userId") Integer userId);

    Optional<UserDeviceToken> findByToken(String token);

    // Explicit query for ownership check
    @Query("select t from UserDeviceToken t where t.token = :token and t.user.id = :userId")
    Optional<UserDeviceToken> findByTokenAndUserId(@Param("token") String token, @Param("userId") Integer userId);

    // 사용자의 모든 디바이스 토큰 삭제
    @Modifying
    @Transactional
    @Query("DELETE FROM UserDeviceToken t WHERE t.user.id = :userId")
    void deleteByUserId(@Param("userId") Integer userId);
}
