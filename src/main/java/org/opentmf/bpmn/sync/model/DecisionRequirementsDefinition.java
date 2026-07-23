package org.opentmf.bpmn.sync.model;

import lombok.Getter;
import lombok.Setter;

/**
 * A Camunda DMN decision requirements definition (DRD), as returned in the
 * {@code deployedDecisionRequirementsDefinitions} map of a deployment response. Present when a
 * deployed DMN file contains more than one decision.
 */
@Getter
@Setter
public class DecisionRequirementsDefinition {

  private String id;
  private String key;
  private String category;
  private String name;
  private int version;
  private String resource;
  private String tenantId;
  private String deploymentId;
}
