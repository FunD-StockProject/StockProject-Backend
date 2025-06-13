package com.fund.stockProject.global.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.domain")
public class DomainConfig {
    private String prod;
    private String test;
}
