package org.opentmf.bpmn.sync.service.api;

import org.opentmf.bpmn.sync.model.CamundaDeploymentResponse;

public interface BpmnMigrationService {

  void performAutoMigration(CamundaDeploymentResponse deployment);
}
