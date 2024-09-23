package com.pia.bpmn.sync.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Gokhan Demir
 */
@Getter
@Setter
public class Instruction {

  private List<String> sourceActivityIds;
  private List<String> targetActivityIds;
  private Boolean updateEventTrigger;
}
