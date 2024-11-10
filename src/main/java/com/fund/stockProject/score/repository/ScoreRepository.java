package com.fund.stockProject.score.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.stereotype.Repository;

import com.fund.stockProject.score.entity.Score;

@Repository
@EnableJpaRepositories
public interface ScoreRepository extends JpaRepository<Score, Integer> {
}
