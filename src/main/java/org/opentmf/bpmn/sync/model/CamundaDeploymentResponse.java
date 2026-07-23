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
  private Map<String, DecisionDefinition> deployedDecisionDefinitions;
  private Map<String, DecisionRequirementsDefinition> deployedDecisionRequirementsDefinitions;

  public int getDeployedProcessDefinitionCount() {
    return size(deployedProcessDefinitions);
  }

  public int getDeployedDecisionDefinitionCount() {
    return size(deployedDecisionDefinitions);
  }

  public int getDeployedDecisionRequirementsDefinitionCount() {
    return size(deployedDecisionRequirementsDefinitions);
  }

  /**
   * The total number of artifacts (process + decision + decision-requirements definitions) actually
   * deployed by Camunda. Drives both the "did we deploy anything?" lock decision and the log
   * wording, so that a deployment which changes <em>only</em> a DMN is correctly recognised as a
   * real deployment.
   */
  public int totalDeployedCount() {
    return getDeployedProcessDefinitionCount()
        + getDeployedDecisionDefinitionCount()
        + getDeployedDecisionRequirementsDefinitionCount();
  }

  private static int size(Map<?, ?> map) {
    return map == null ? 0 : map.size();
  }
}
