package com.pia.bpmn.task;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

@Slf4j
public class SampleTask implements JavaDelegate {

  @Override
  public void execute(DelegateExecution execution) {
    log.debug("SampleTask started and stopped.");
  }
}
