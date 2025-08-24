package com.fund.stockProject.user.repository;

import com.fund.stockProject.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Boolean existsByNickname(String nickname);
    Boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
    void deleteUserByEmail(String email);
    
    // 활성 사용자 조회
    List<User> findByIsActiveTrue();
}
