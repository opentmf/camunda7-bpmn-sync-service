package com.pia.bpmn.sync.model;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Gokhan Demir
 */
@Getter
@Setter
public class MigrationPlan {

  private String sourceProcessDefinitionId;
  private String targetProcessDefinitionId;
  private List<Instruction> instructions;
  private Map<String, CamundaVariable> variables;
}
