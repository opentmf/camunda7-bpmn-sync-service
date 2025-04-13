package org.opentmf.bpmn.sync.exception;

import org.opentmf.bpmn.sync.model.CamundaErrorResponse;
import org.opentmf.client.common.exception.OpenTmfWebClientException;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.CollectionUtils;

/**
 * @author Gokhan Demir
 */
public class CamundaResponseException extends OpenTmfWebClientException {

  private final CamundaErrorResponse camundaErrorResponse;

  public CamundaResponseException(HttpStatusCode httpStatusCode,
      CamundaErrorResponse camundaErrorResponse) {
    super(httpStatusCode);
    this.camundaErrorResponse = camundaErrorResponse;
  }

  @Override
  public String getMessage() {
    if (camundaErrorResponse == null) {
      return super.getMessage();
    }
    // Constructs and returns a message in the following format:
    // 504 Gateway Timeout: Exceeded timeout value waiting for response from
    // the remote Camunda server. Details:  1) Request was sent 30 seconds ago. 2) etc.
    var buffer = new StringBuilder()
        .append(camundaErrorResponse.getCode()).append(' ')
        .append(camundaErrorResponse.getType()).append(": ")
        .append(camundaErrorResponse.getMessage());
    if (!CollectionUtils.isEmpty(camundaErrorResponse.getDetails())) {
      buffer.append(" Details: ");
      for (int i = 0, n = camundaErrorResponse.getDetails().size(); i < n; i++) {
        buffer.append(' ').append(i + 1).append(") ")
            .append(camundaErrorResponse.getDetails().get(i));
      }
    }
    return buffer.toString();
  }
}
