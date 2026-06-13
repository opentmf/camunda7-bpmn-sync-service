package org.opentmf.bpmn.sync.service.impl;

import java.util.Map;
import org.opentmf.bpmn.sync.client.api.RestCamundaClient;
import org.opentmf.bpmn.sync.config.BpmnSyncProperties;
import org.opentmf.bpmn.sync.model.CamundaDeploymentResponse;
import org.opentmf.bpmn.sync.model.DecisionDefinition;
import org.opentmf.bpmn.sync.model.ProcessDefinition;
import org.opentmf.bpmn.sync.service.api.BpmnMigrationService;
import org.opentmf.bpmn.sync.service.api.BpmnSyncService;
import org.opentmf.bpmn.sync.util.ResourceUtil;
import org.opentmf.db.lock.exception.DbLockException;
import org.opentmf.db.lock.model.AcquiredLock;
import org.opentmf.db.lock.model.LockType;
import org.opentmf.db.lock.service.api.DbLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * @author Gokhan Demir
 */
@Slf4j
@RequiredArgsConstructor
public class RestBpmnSyncServiceImpl implements BpmnSyncService {

  private final BpmnSyncProperties bpmnSyncProperties;
  private final DbLockService dbLockService;
  private final RestCamundaClient camundaClient;
  private final BpmnMigrationService bpmnMigrationService;

  @Override
  public CamundaDeploymentResponse ensureBpmnConsistency() {
    CamundaDeploymentResponse deploymentResponse;
    log.info("Starting BPMN Deployment for {}, version: {}",
        bpmnSyncProperties.getDeploymentName(), bpmnSyncProperties.getBpmnVersion());
    try {
      deploymentResponse = doEnsureBpmnConsistency();
    } catch (Exception e) {
      throw new IllegalStateException("", e);
    }
    bpmnMigrationService.performAutoMigration(deploymentResponse);
    return deploymentResponse;
  }

  private CamundaDeploymentResponse doEnsureBpmnConsistency() throws DbLockException {
    Resource[] deployableResources = ensurePropertiesProvided();
    CamundaDeploymentResponse deploymentResponse = null;
    boolean lockReleased = false;
    AcquiredLock lock = null;
    int deployedCount = 0;
    try {
      String requestedVersion = bpmnSyncProperties.getBpmnVersion();
      lock = dbLockService.acquireLock(LockType.BPMN, requestedVersion);
      if (lock.isUpgrade() ||
          (lock.isDowngrade() &&
              lock.isDowngradeAllowed(bpmnSyncProperties.getDowngradeAllowedAfter())))
      {
        deploymentResponse = syncDeployableResources(deployableResources);
        deployedCount = deploymentResponse == null ? 0 : deploymentResponse.totalDeployedCount();
        releaseLock(lock, deployedCount);
        lockReleased = true;
      } else {
        dbLockService.releaseLock(lock, false);
        lockReleased = true;
        log.info("{} BPMN/DMN resources are already up-to-date for version {}.",
            bpmnSyncProperties.getDeploymentName(), lock.getLockVersion());
      }
    } catch (Exception e) {
      dbLockService.releaseLock(lock, false);
      lockReleased = true;
      throw new IllegalStateException("Could not synchronize BPMN/DMN files because of exception", e);
    } finally {
      if (!lockReleased) {
        releaseLock(lock, deployedCount);
      }
    }
    return deploymentResponse;
  }

  private Resource[] ensurePropertiesProvided() {
    Assert.notNull(bpmnSyncProperties.getDeploymentName(), "Application name must be provided.");
    Assert.notNull(bpmnSyncProperties.getBpmnVersion(), "BPMN version must be provided.");
    Resource[] deployableResources = ResourceUtil.getDeployableResources();
    Assert.notEmpty(deployableResources,
        "No deployable resources found in classpath:bpmn or classpath:dmn folders.");
    return deployableResources;
  }

  private CamundaDeploymentResponse syncDeployableResources(Resource[] deployableResources) {
    log.info("Will synchronize {} BPMN/DMN files, for {}, bpmnVersion: {}",
        deployableResources.length,
        bpmnSyncProperties.getDeploymentName(), bpmnSyncProperties.getBpmnVersion());

    var response =
        camundaClient.syncBpmnFiles(bpmnSyncProperties.getDeploymentName(), deployableResources);
    logDeploymentResponse(response);
    return response;
  }

  private void logDeploymentResponse(CamundaDeploymentResponse response) {
    if (response.totalDeployedCount() == 0) {
      log.warn("{} synchronization completed without deploying any BPMN or DMN. "
          + "The specified bpmnVersion was: {}. "
          + "Hint: Do not change the bpmnVersion when there are no BPMN/DMN changes.",
          bpmnSyncProperties.getDeploymentName(), bpmnSyncProperties.getBpmnVersion());
      return;
    }
    log.info("Deployment for {}, version {} has been completed. "
            + "Deployed artifacts - BPMN: {}, DMN: {}, DRD: {}.",
        bpmnSyncProperties.getDeploymentName(), bpmnSyncProperties.getBpmnVersion(),
        response.getDeployedProcessDefinitionCount(),
        response.getDeployedDecisionDefinitionCount(),
        response.getDeployedDecisionRequirementsDefinitionCount());
    logDeployedProcessDefinitions(response.getDeployedProcessDefinitions());
    logDeployedDecisionDefinitions(response.getDeployedDecisionDefinitions());
  }

  private void logDeployedProcessDefinitions(Map<String, ProcessDefinition> definitions) {
    if (definitions == null || definitions.isEmpty()) {
      return;
    }
    log.debug("Deployed BPMN files and their versions follow:");
    for (ProcessDefinition bpmn : definitions.values()) {
      log.debug("Version: {}, BPMN: {}", bpmn.getVersion(), bpmn.getResource());
    }
  }

  private void logDeployedDecisionDefinitions(Map<String, DecisionDefinition> definitions) {
    if (definitions == null || definitions.isEmpty()) {
      return;
    }
    log.debug("Deployed DMN files and their versions follow:");
    for (DecisionDefinition dmn : definitions.values()) {
      log.debug("Version: {}, DMN: {}", dmn.getVersion(), dmn.getResource());
    }
  }

  private void releaseLock(AcquiredLock lock, int deployedCount) {
    try {
      dbLockService.releaseLock(lock, deployedCount > 0);
    } catch (DbLockException e) {
      throw new IllegalStateException("Unexpected error during lock release.", e);
    }
  }
}
