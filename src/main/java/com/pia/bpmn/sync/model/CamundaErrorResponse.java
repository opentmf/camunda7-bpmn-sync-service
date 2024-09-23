package com.pia.bpmn.sync.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Gokhan Demir
 */
@Getter
@Setter
public class CamundaErrorResponse implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  private String code;
  private String type;
  private String message;
  private List<String> details;
}
