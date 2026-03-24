package org.opentmf.bpmn.sync.client.api;

import org.opentmf.bpmn.sync.model.CamundaDeploymentResponse;
import org.opentmf.bpmn.sync.model.ExecuteMigrationPlanAsyncResponse;
import org.opentmf.bpmn.sync.model.ExecuteMigrationPlanRequest;
import org.opentmf.bpmn.sync.model.GenerateMigrationPlanRequest;
import org.opentmf.bpmn.sync.model.MigrationPlan;
import org.opentmf.bpmn.sync.model.ObjectCount;
import org.opentmf.bpmn.sync.model.ProcessDefinition;
import org.springframework.core.io.Resource;

/**
 * @author Gokhan Demir
 */
public interface CamundaRestClient {

  CamundaDeploymentResponse syncBpmnFiles(String deploymentName, Resource[] bpmnFiles);

  ProcessDefinition getProcessDefinition(String key, int version);

  ObjectCount getProcessInstanceCount(String processDefinitionId);

  MigrationPlan generateMigrationPlan(GenerateMigrationPlanRequest request);

  ExecuteMigrationPlanAsyncResponse executeMigrationPlanAsync(
      ExecuteMigrationPlanRequest request);
}
