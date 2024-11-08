package com.fund.stockProject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;

@SpringBootApplication
@EnableJpaAuditing
@EnableEncryptableProperties
public class StockProjectApplication {
	// push test
	public static void main(String[] args) {
		SpringApplication.run(StockProjectApplication.class, args);
	}

}
