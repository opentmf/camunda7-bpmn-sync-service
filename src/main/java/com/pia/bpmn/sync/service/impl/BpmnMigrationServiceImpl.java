package com.pia.bpmn.sync.service.impl;

import com.pia.bpmn.sync.client.api.CamundaClient;
import com.pia.bpmn.sync.config.BpmnSyncProperties;
import com.pia.bpmn.sync.model.CamundaDeploymentResponse;
import com.pia.bpmn.sync.model.ExecuteMigrationPlanAsyncResponse;
import com.pia.bpmn.sync.model.ExecuteMigrationPlanRequest;
import com.pia.bpmn.sync.model.GenerateMigrationPlanRequest;
import com.pia.bpmn.sync.model.ObjectCount;
import com.pia.bpmn.sync.model.ProcessDefinition;
import com.pia.bpmn.sync.model.ProcessInstanceQuery;
import com.pia.bpmn.sync.service.api.BpmnMigrationService;
import java.util.Collection;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Gokhan Demir
 */
@RequiredArgsConstructor
@Slf4j
public class BpmnMigrationServiceImpl implements BpmnMigrationService {

  private final BpmnSyncProperties bpmnSyncProperties;
  private final CamundaClient camundaClient;

  @Override
  public void performAutoMigration(CamundaDeploymentResponse deployment) {
    try {
      if (isMigrationNecessary(deployment)) {
        migrate(deployment);
      }
    } catch (Exception e) {
      log.warn("Ignoring exception caught during auto-migration.", e);
    }
  }

  private void migrate(CamundaDeploymentResponse deployment) {
    log.info("Starting BPMN migration for {} deployed BPMNs.",
        deployment.getDeployedProcessDefinitions().size());
    ObjectCount count = new ObjectCount();
    Flux.fromIterable(deployment.getDeployedProcessDefinitions().values())
        .filter(processDefinition ->
            !isInitialDeployment(processDefinition, this::logSkipMigrationStep))
        .flatMap(this::migrate)
        .doOnNext(signal -> accumulateJobsCreated(count, signal))
        .blockLast();
    if (count.getCount() > 0) {
      log.info("BPMN Migration completed, total async jobs created: {}. "
          + "Use Camunda Cockpit for checking job completions.", count.getCount());
    } else {
      log.info("BPMN Migration completed without creating any async migration jobs.");
    }
  }

  private void accumulateJobsCreated(ObjectCount count,
      ExecuteMigrationPlanAsyncResponse response) {
    count.setCount(count.getCount() + response.getTotalJobs());
  }

  private Mono<ExecuteMigrationPlanAsyncResponse> migrate(ProcessDefinition target) {
    return camundaClient.getProcessDefinition(target.getKey(), target.getVersion() - 1)
        .doOnNext(processDefinition -> log.debug("Previous version's process definition id: {}",
            processDefinition.getId()))
        .flatMap(source -> migrate(source, target))
        .switchIfEmpty(Mono.defer(() -> noPrevVersionExists(target)));
  }

  private Mono<ExecuteMigrationPlanAsyncResponse> noPrevVersionExists(ProcessDefinition target) {
    logSkipMigrationStep(target);
    return Mono.just(emptyResponse(target));
  }

  private void logSkipMigrationStep(ProcessDefinition target) {
    log.debug("Skipping migration of {} from version {} to {}, "
            + "because no previous process definition exists.",
        target.getKey(), target.getVersion() - 1, target.getVersion());
  }

  private Mono<ExecuteMigrationPlanAsyncResponse> migrate(ProcessDefinition source,
      ProcessDefinition target) {
    log.trace("Getting process instance count for process definition id: {}", source.getId());
    return camundaClient.getProcessInstanceCount(source.getId())
        .flatMap(count -> migrateIfProcessesExist(source, target, count));
  }

  private boolean isInitialDeployment(
      ProcessDefinition processDefinition, Consumer<ProcessDefinition> actionIfTrue) {
    if (processDefinition.getVersion() > 1) {
      return false;
    } else {
      actionIfTrue.accept(processDefinition);
      return true;
    }
  }

  private Mono<ExecuteMigrationPlanAsyncResponse> migrateIfProcessesExist(
      ProcessDefinition source, ProcessDefinition target, ObjectCount count) {
    if (count.getCount() == 0L) {
      log.debug("Migration not necessary for {} version {} to {} "
              + "because no process instances exist.",
          source.getKey(), source.getVersion(), target.getVersion());
      return Mono.just(emptyResponse(source));
    }
    log.trace("There are {} process instances for {} version {}",
        count.getCount(), source.getKey(), source.getVersion());
    log.trace("Generating migration plan for migrating {} process instances from version {} to {}",
        source.getKey(), source.getVersion(), target.getVersion());
    var request = new ExecuteMigrationPlanRequest();
    request.setProcessInstanceQuery(processInstanceQuery(source));
    return camundaClient.generateMigrationPlan(migrationPlanRequest(source, target))
        .doOnNext(request::setMigrationPlan)
        .doOnNext(migrationPlan -> log.debug("Executing migration async for migrating {} "
            + "process instances from version {} to {}", source.getKey(), source.getVersion(), target.getVersion()))
        .flatMap(migrationPlan -> camundaClient.executeMigrationPlanAsync(request))
        .doOnNext(r -> logExecuteMigrationPlanAsyncResponse(r, source, target));
  }

  private void logExecuteMigrationPlanAsyncResponse(ExecuteMigrationPlanAsyncResponse response,
      ProcessDefinition source, ProcessDefinition target) {
    log.debug("Migration Async Execution initiated for {} version {} to {}. Details: {}",
        source.getKey(), source.getVersion(), target.getVersion(), response);
  }

  private GenerateMigrationPlanRequest migrationPlanRequest(ProcessDefinition source,
      ProcessDefinition target) {
    var request = new GenerateMigrationPlanRequest();
    request.setSourceProcessDefinitionId(source.getId());
    request.setTargetProcessDefinitionId(target.getId());
    request.setUpdateEventTriggers(false);
    return request;
  }

  private ProcessInstanceQuery processInstanceQuery(ProcessDefinition processDefinition) {
    var processInstanceQuery = new ProcessInstanceQuery();
    processInstanceQuery.setProcessDefinitionId(processDefinition.getId());
    return processInstanceQuery;
  }

  private ExecuteMigrationPlanAsyncResponse emptyResponse(ProcessDefinition processDefinition) {
    var response = new ExecuteMigrationPlanAsyncResponse();
    response.setId(processDefinition.getKey());
    response.setJobsCreated(0);
    response.setTotalJobs(0);
    return response;
  }

  private boolean isMigrationNecessary(CamundaDeploymentResponse deployment) {
    if (bpmnSyncProperties.isAutoMigrate()) {
      if (deployment == null ||
          deployment.getDeployedProcessDefinitions() == null ||
          deployment.getDeployedProcessDefinitions().isEmpty()) {
        log.info("Auto-migration not necessary because no new BPMN has been deployed.");
        return false;
      }
      if (overriddenProcessDefinitionCount(deployment.getDeployedProcessDefinitions().values()) == 0) {
        log.info("Auto-migration not necessary because all deployed BPMNs are initial versions.");
        return false;
      }
      return true;
    } else {
      log.info("Skipping BPMN migration because auto-migrate is set to false.");
      return false;
    }
  }

  private int overriddenProcessDefinitionCount(Collection<ProcessDefinition> processDefinitions) {
    return (int) processDefinitions.stream()
        .filter(processDefinition -> processDefinition.getVersion() > 1).count();
  }
}
