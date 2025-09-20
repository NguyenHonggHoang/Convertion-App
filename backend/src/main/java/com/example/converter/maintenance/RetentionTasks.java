package com.example.converter.maintenance;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RetentionTasks {
  @Scheduled(cron = "0 0 3 * * *") public void noop() { /* add Flyway-based purge if needed */ }
}
