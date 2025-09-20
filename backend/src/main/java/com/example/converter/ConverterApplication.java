package com.example.converter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot Application class for the Converter application.
 * This application provides currency conversion, unit conversion, news analysis,
 * and alerting functionality.
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties
public class ConverterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConverterApplication.class, args);
    }
} 