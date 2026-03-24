package org.opentmf.bpmn.sync.service.impl;

import org.opentmf.bpmn.sync.client.api.CamundaRestClient;
import org.opentmf.bpmn.sync.config.BpmnSyncProperties;
import org.opentmf.bpmn.sync.model.CamundaDeploymentResponse;
import org.opentmf.bpmn.sync.model.ExecuteMigrationPlanAsyncResponse;
import org.opentmf.bpmn.sync.model.ExecuteMigrationPlanRequest;
import org.opentmf.bpmn.sync.model.GenerateMigrationPlanRequest;
import org.opentmf.bpmn.sync.model.ObjectCount;
import org.opentmf.bpmn.sync.model.ProcessDefinition;
import org.opentmf.bpmn.sync.model.ProcessInstanceQuery;
import org.opentmf.bpmn.sync.service.api.BpmnMigrationService;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gokhan Demir
 */
@RequiredArgsConstructor
@Slf4j
public class RestBpmnMigrationServiceImpl implements BpmnMigrationService {

  private final BpmnSyncProperties bpmnSyncProperties;
  private final CamundaRestClient camundaClient;

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
    long totalJobsCreated = 0;
    for (ProcessDefinition target : deployment.getDeployedProcessDefinitions().values()) {
      if (target.getVersion() <= 1) {
        logSkipMigrationStep(target);
        continue;
      }
      var response = migrate(target);
      totalJobsCreated += response.getTotalJobs();
    }
    if (totalJobsCreated > 0) {
      log.info("BPMN Migration completed, total async jobs created: {}. "
          + "Use Camunda Cockpit for checking job completions.", totalJobsCreated);
    } else {
      log.info("BPMN Migration completed without creating any async migration jobs.");
    }
  }

  private ExecuteMigrationPlanAsyncResponse migrate(ProcessDefinition target) {
    ProcessDefinition source = camundaClient.getProcessDefinition(
        target.getKey(), target.getVersion() - 1);
    if (source == null) {
      logSkipMigrationStep(target);
      return emptyResponse(target);
    }
    log.debug("Previous version's process definition id: {}", source.getId());
    return migrate(source, target);
  }

  private void logSkipMigrationStep(ProcessDefinition target) {
    log.debug("Skipping migration of {} from version {} to {}, "
            + "because no previous process definition exists.",
        target.getKey(), target.getVersion() - 1, target.getVersion());
  }

  private ExecuteMigrationPlanAsyncResponse migrate(ProcessDefinition source,
      ProcessDefinition target) {
    log.trace("Getting process instance count for process definition id: {}", source.getId());
    ObjectCount count = camundaClient.getProcessInstanceCount(source.getId());
    return migrateIfProcessesExist(source, target, count);
  }

  private ExecuteMigrationPlanAsyncResponse migrateIfProcessesExist(
      ProcessDefinition source, ProcessDefinition target, ObjectCount count) {
    if (count.getCount() == 0L) {
      log.debug("Migration not necessary for {} version {} to {} "
              + "because no process instances exist.",
          source.getKey(), source.getVersion(), target.getVersion());
      return emptyResponse(source);
    }
    log.trace("There are {} process instances for {} version {}",
        count.getCount(), source.getKey(), source.getVersion());
    log.trace("Generating migration plan for migrating {} process instances from version {} to {}",
        source.getKey(), source.getVersion(), target.getVersion());
    var migrationPlan = camundaClient.generateMigrationPlan(migrationPlanRequest(source, target));
    log.debug("Executing migration async for migrating {} "
        + "process instances from version {} to {}", source.getKey(), source.getVersion(),
        target.getVersion());
    var request = new ExecuteMigrationPlanRequest();
    request.setMigrationPlan(migrationPlan);
    request.setProcessInstanceQuery(processInstanceQuery(source));
    var response = camundaClient.executeMigrationPlanAsync(request);
    logExecuteMigrationPlanAsyncResponse(response, source, target);
    return response;
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
