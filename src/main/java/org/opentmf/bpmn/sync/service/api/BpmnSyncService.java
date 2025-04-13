package org.opentmf.bpmn.sync.service.api;

import org.opentmf.bpmn.sync.model.CamundaDeploymentResponse;

/**
 * @author Gokhan Demir
 */
public interface BpmnSyncService {

  CamundaDeploymentResponse ensureBpmnConsistency();
}
