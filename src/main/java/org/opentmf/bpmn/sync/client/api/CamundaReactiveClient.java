package org.opentmf.bpmn.sync.client.api;

import org.opentmf.bpmn.sync.model.CamundaDeploymentResponse;
import org.opentmf.bpmn.sync.model.ExecuteMigrationPlanAsyncResponse;
import org.opentmf.bpmn.sync.model.ExecuteMigrationPlanRequest;
import org.opentmf.bpmn.sync.model.GenerateMigrationPlanRequest;
import org.opentmf.bpmn.sync.model.MigrationPlan;
import org.opentmf.bpmn.sync.model.ObjectCount;
import org.opentmf.bpmn.sync.model.ProcessDefinition;
import org.springframework.core.io.Resource;
import reactor.core.publisher.Mono;

/**
 * @author Gokhan Demir
 */
public interface CamundaReactiveClient {

  Mono<CamundaDeploymentResponse> syncBpmnFiles(String deploymentName, Resource[] bpmnFiles);

  Mono<ProcessDefinition> getProcessDefinition(String key, int version);

  Mono<ObjectCount> getProcessInstanceCount(String processDefinitionId);

  Mono<MigrationPlan> generateMigrationPlan(GenerateMigrationPlanRequest request);

  Mono<ExecuteMigrationPlanAsyncResponse> executeMigrationPlanAsync(
      ExecuteMigrationPlanRequest request);
}
