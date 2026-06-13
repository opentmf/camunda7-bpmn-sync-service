package org.opentmf.bpmn.sync.model;

import lombok.Getter;
import lombok.Setter;

/**
 * A Camunda DMN decision definition, as returned in the {@code deployedDecisionDefinitions} map of
 * a deployment response. Unlike a {@link ProcessDefinition}, a decision definition has no process
 * instances and is therefore never subject to migration.
 */
@Getter
@Setter
public class DecisionDefinition {

  private String id;
  private String key;
  private String category;
  private String name;
  private int version;
  private String resource;
  private String deploymentId;
  private String decisionRequirementsDefinitionId;
  private String decisionRequirementsDefinitionKey;
  private String tenantId;
  private String versionTag;
  private Integer historyTimeToLive;
}
