package com.walkmate.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ForceCompleteOverdueSessionWorker {
  private final ForceCompleteOverdueSessionService service;

  @Scheduled(fixedDelay = 60000)
  public void sweep() {
    int affected = service.execute(100);
    if (affected > 0) {
      log.info("Force-completed {} overdue ACTIVE sessions", affected);
    }
  }
}
