package org.opentmf.bpmn.sync.model;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Gokhan Demir
 */
@Getter
@Setter
public class CamundaVariable {

  private String type;
  private Object value;
  private Map<String, Object> valueInfo;
}
