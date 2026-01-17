package com.fund.stockProject.global.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public ObjectMapper redisCacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // 타입 정보를 포함하지 않도록 설정 (클린한 JSON)
        return mapper;
    }

    @Bean
    public RedisCacheConfiguration defaultCacheConfiguration(ObjectMapper redisCacheObjectMapper) {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .disableCachingNullValues()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()
                )
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer(redisCacheObjectMapper)
                )
            );
    }

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            RedisCacheConfiguration defaultCacheConfiguration) {

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // 숏뷰 추천: 5분 캐시 (사용자별 추천 결과)
        cacheConfigurations.put("shortview",
            defaultCacheConfiguration.entryTtl(Duration.ofMinutes(5)));

        // 실시간 가격: 30초 캐시 (변동성 높음)
        cacheConfigurations.put("stockPrice",
            defaultCacheConfiguration.entryTtl(Duration.ofSeconds(30)));

        // 주식 정보: 1시간 캐시 (기본 정보, 변동 적음)
        cacheConfigurations.put("stockInfo",
            defaultCacheConfiguration.entryTtl(Duration.ofHours(1)));

        // 검색 결과: 30분 캐시
        cacheConfigurations.put("searchResult",
            defaultCacheConfiguration.entryTtl(Duration.ofMinutes(30)));

        // 유효한 주식 목록: 1시간 캐시
        cacheConfigurations.put("validStocks",
            defaultCacheConfiguration.entryTtl(Duration.ofHours(1)));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultCacheConfiguration)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build();
    }
}
