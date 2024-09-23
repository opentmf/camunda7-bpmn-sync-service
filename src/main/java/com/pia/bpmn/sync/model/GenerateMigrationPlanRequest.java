package com.pia.bpmn.sync.model;

import lombok.Getter;
import lombok.Setter;

/**
 * @author Gokhan Demir
 */
@Getter
@Setter
public class GenerateMigrationPlanRequest {

  /**
   * The id of the source process definition for the migration.
   */
  private String sourceProcessDefinitionId;

  /**
   * The id of the target process definition for the migration.
   */
  private String targetProcessDefinitionId;

  /**
   * A boolean flag indicating whether instructions between events should be configured to update
   * the event triggers.
   */
  private boolean updateEventTriggers;
}
