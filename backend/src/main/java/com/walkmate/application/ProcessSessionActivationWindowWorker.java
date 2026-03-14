package com.walkmate.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessSessionActivationWindowWorker {
  private final ProcessSessionActivationWindowService service;

  @Scheduled(fixedDelay = 10000)
  public void sweep() {
    int affected = service.execute(100);
    if (affected > 0) {
      log.info("Processed {} expired PENDING sessions", affected);
    }
  }
}
