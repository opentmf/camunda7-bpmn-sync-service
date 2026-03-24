package org.opentmf.bpmn.sync.exception;

import org.opentmf.client.common.exception.OpenTmfClientResponseException;
import org.springframework.http.HttpStatusCode;

/**
 * @author Gokhan Demir
 */
public class CamundaResponseException extends OpenTmfClientResponseException {

  public CamundaResponseException(HttpStatusCode httpStatusCode, String message) {
    super(httpStatusCode, message);
  }

  public CamundaResponseException(OpenTmfClientResponseException cause) {
    super(cause.getStatusCode(), cause.getMessage(), cause);
  }
}
