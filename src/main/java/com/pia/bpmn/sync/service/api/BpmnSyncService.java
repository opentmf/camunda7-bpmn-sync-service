package com.pia.bpmn.sync.service.api;

import com.pia.bpmn.sync.model.CamundaDeploymentResponse;

/**
 * @author Gokhan Demir
 */
public interface BpmnSyncService {

  CamundaDeploymentResponse ensureBpmnConsistency();
}
