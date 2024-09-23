package com.pia.bpmn.sync.client.api;

import com.pia.bpmn.sync.model.CamundaDeploymentResponse;
import com.pia.bpmn.sync.model.ExecuteMigrationPlanAsyncResponse;
import com.pia.bpmn.sync.model.ExecuteMigrationPlanRequest;
import com.pia.bpmn.sync.model.GenerateMigrationPlanRequest;
import com.pia.bpmn.sync.model.MigrationPlan;
import com.pia.bpmn.sync.model.ObjectCount;
import com.pia.bpmn.sync.model.ProcessDefinition;
import org.springframework.core.io.Resource;
import reactor.core.publisher.Mono;

/**
 * @author Gokhan Demir
 */
public interface CamundaClient {

  Mono<CamundaDeploymentResponse> syncBpmnFiles(String deploymentName, Resource[] bpmnFiles);

  Mono<ProcessDefinition> getProcessDefinition(String key, int version);

  Mono<ObjectCount> getProcessInstanceCount(String processDefinitionId);

  Mono<MigrationPlan> generateMigrationPlan(GenerateMigrationPlanRequest request);

  Mono<ExecuteMigrationPlanAsyncResponse> executeMigrationPlanAsync(
      ExecuteMigrationPlanRequest request);
}
