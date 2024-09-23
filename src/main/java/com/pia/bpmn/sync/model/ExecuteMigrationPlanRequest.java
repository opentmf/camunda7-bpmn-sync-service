package com.pia.bpmn.sync.model;

import lombok.Getter;
import lombok.Setter;

/**
 * @author Gokhan Demir
 */
@Getter
@Setter
public class ExecuteMigrationPlanRequest {

  private MigrationPlan migrationPlan;
  private ProcessInstanceQuery processInstanceQuery;
}
