package com.pia.bpmn.sync.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Gokhan Demir
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "camunda.bpm.client")
public class CamundaProperties {

  private String baseUrl;
}
