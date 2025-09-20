package com.example.converter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.time.Instant;
import java.math.BigDecimal;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
public class ConversionServiceTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired ConversionService conv;
    @Autowired ExchangeRateService rates;

    @Test
    void recordAndHistory() {
        rates.upsert("USD","VND", new BigDecimal("25000"), Instant.now(), "test");
        var e = conv.record(1L, "USD","VND", new BigDecimal("100"), new BigDecimal("25000"), new BigDecimal("2500000"), "test");
        var page = conv.history(1L, org.springframework.data.domain.PageRequest.of(0,10));
        assert(page.getTotalElements()>=1);
    }
}
