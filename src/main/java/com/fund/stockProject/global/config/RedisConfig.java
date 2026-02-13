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
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fund.stockProject.stock.dto.response.StockInfoResponse;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public ObjectMapper redisCacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // 명시 타입 serializer에서 사용할 ObjectMapper
        return mapper;
    }

    @Bean
    public RedisCacheConfiguration defaultCacheConfiguration() {
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
                    // 기본 캐시도 타입 정보를 유지해 역직렬화 시 Map으로 깨지지 않도록 설정
                    new GenericJackson2JsonRedisSerializer()
                )
            );
    }

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            RedisCacheConfiguration defaultCacheConfiguration,
            ObjectMapper redisCacheObjectMapper) {

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        Jackson2JsonRedisSerializer<StockInfoResponse> stockInfoSerializer =
            new Jackson2JsonRedisSerializer<>(redisCacheObjectMapper, StockInfoResponse.class);
        RedisSerializationContext.SerializationPair<StockInfoResponse> stockInfoSerializationPair =
            RedisSerializationContext.SerializationPair.fromSerializer(stockInfoSerializer);

        // 숏뷰 추천: 5분 캐시 (사용자별 추천 결과)
        cacheConfigurations.put("shortview",
            defaultCacheConfiguration.entryTtl(Duration.ofMinutes(5)));

        // 실시간 가격: 30초 캐시 (변동성 높음)
        cacheConfigurations.put("stockPrice",
            defaultCacheConfiguration.entryTtl(Duration.ofSeconds(30))
                .serializeValuesWith(castToObjectPair(stockInfoSerializationPair)));

        // 주식 정보: 1시간 캐시 (기본 정보, 변동 적음)
        cacheConfigurations.put("stockInfo",
            defaultCacheConfiguration.entryTtl(Duration.ofHours(1)));

        // 검색 결과: 30분 캐시
        cacheConfigurations.put("searchResult",
            defaultCacheConfiguration.entryTtl(Duration.ofMinutes(30))
                .serializeValuesWith(castToObjectPair(stockInfoSerializationPair)));

        // 유효한 주식 목록: 1시간 캐시
        cacheConfigurations.put("validStocks",
            defaultCacheConfiguration.entryTtl(Duration.ofHours(1)));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultCacheConfiguration)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build();
    }

    @SuppressWarnings("unchecked")
    private RedisSerializationContext.SerializationPair<Object> castToObjectPair(
        RedisSerializationContext.SerializationPair<?> pair) {
        return (RedisSerializationContext.SerializationPair<Object>) pair;
    }
}
