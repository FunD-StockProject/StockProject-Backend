package com.fund.stockProject.auth.repository;

import com.fund.stockProject.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Boolean existsByNickname(String nickname);
    Boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
    Optional<User> findByNicknameAndBirthDate(String nickname, LocalDate birthDate);

    void deleteUserByEmail(String email);
}