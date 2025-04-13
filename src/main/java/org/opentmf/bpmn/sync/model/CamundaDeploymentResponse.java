package org.opentmf.bpmn.sync.model;

import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CamundaDeploymentResponse {

  private String id;
  private String name;
  private String source;
  private OffsetDateTime deploymentTime;
  private Map<String, ProcessDefinition> deployedProcessDefinitions;
}
