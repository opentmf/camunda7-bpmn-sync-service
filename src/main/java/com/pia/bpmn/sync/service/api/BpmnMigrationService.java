package com.pia.bpmn.sync.service.api;

import com.pia.bpmn.sync.model.CamundaDeploymentResponse;

public interface BpmnMigrationService {

  void performAutoMigration(CamundaDeploymentResponse deployment);
}
