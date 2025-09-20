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
public class ExchangeRateServiceTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired ExchangeRateService svc;

    @Test
    void upsertAndLatest() {
        svc.upsert("USD","VND", new BigDecimal("25000"), Instant.now(), "test");
        var opt = svc.latest("USD","VND");
        assert(opt.isPresent());
        assert(opt.get().getRate().compareTo(new BigDecimal("25000"))==0);
    }
}
