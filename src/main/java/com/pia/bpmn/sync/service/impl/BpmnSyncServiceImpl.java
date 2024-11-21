package com.pia.bpmn.sync.service.impl;

import com.pia.bpmn.sync.client.api.CamundaClient;
import com.pia.bpmn.sync.config.BpmnSyncProperties;
import com.pia.bpmn.sync.model.CamundaDeploymentResponse;
import com.pia.bpmn.sync.model.ProcessDefinition;
import com.pia.bpmn.sync.service.api.BpmnMigrationService;
import com.pia.bpmn.sync.service.api.BpmnSyncService;
import com.pia.bpmn.sync.util.ResourceUtil;
import com.pia.db.lock.exception.DbLockException;
import com.pia.db.lock.model.AcquiredLock;
import com.pia.db.lock.model.LockType;
import com.pia.db.lock.service.api.DbLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * @author Gokhan Demir
 */
@Slf4j
@RequiredArgsConstructor
public class BpmnSyncServiceImpl implements BpmnSyncService {

  private final BpmnSyncProperties bpmnSyncProperties;
  private final DbLockService dbLockService;
  private final CamundaClient camundaClient;
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

  private CamundaDeploymentResponse doEnsureBpmnConsistency()
      throws DbLockException {
    Resource[] bpmnFiles = ensurePropertiesProvided();
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
        deploymentResponse = syncBpmnFiles(bpmnFiles);
        deployedCount = (deploymentResponse == null || deploymentResponse
            .getDeployedProcessDefinitions() == null)
            ? 0 : deploymentResponse.getDeployedProcessDefinitions().size();
        releaseLock(lock, deployedCount);
        lockReleased = true;
      } else {
        dbLockService.releaseLock(lock, false);
        lockReleased = true;
        log.info("{} BPMN files are already up-to-date for version {}.",
            bpmnSyncProperties.getDeploymentName(), lock.getLockVersion());
      }
    } catch (Exception e) {
      dbLockService.releaseLock(lock, false);
      lockReleased = true;
      throw new IllegalStateException("Could not synchronize BPMN files because of exception", e);
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
    Resource[] bpmnFiles = ResourceUtil.getBpmnFiles();
    Assert.notEmpty(bpmnFiles, "No BPMN files found in classpath:bpmn folder.");
    return bpmnFiles;
  }

  private CamundaDeploymentResponse syncBpmnFiles(Resource[] bpmnFiles) {
    log.info("Will synchronize {} BPMN files, for {}, bpmnVersion: {}", bpmnFiles.length,
        bpmnSyncProperties.getDeploymentName(), bpmnSyncProperties.getBpmnVersion());

    return camundaClient
        .syncBpmnFiles(bpmnSyncProperties.getDeploymentName(), bpmnFiles)
        .doOnNext(this::logDeploymentResponse)
        .block();
  }

  private void logDeploymentResponse(CamundaDeploymentResponse response) {
    int deployedCount = response.getDeployedProcessDefinitions() == null ? 0 :
        response.getDeployedProcessDefinitions().size();
    if (deployedCount == 0) {
      log.warn("{} BPMN synchronization completed without deploying any BPMN. "
          + "The specified bpmnVersion was: {}. "
          + "Hint: Do not change the bpmnVersion when there are no BPMN changes.",
          bpmnSyncProperties.getDeploymentName(), bpmnSyncProperties.getBpmnVersion());
    } else {
      log.info("BPMN deployment for {}, version {} has been completed. Deployed BPMN count: {}",
          bpmnSyncProperties.getDeploymentName(), bpmnSyncProperties.getBpmnVersion(),
          deployedCount);
      log.debug("Deployed BPMN Files and Their Versions follows:");
      for (ProcessDefinition bpmn : response.getDeployedProcessDefinitions().values()) {
        String format = String.format("Version: %d, BPMN: %s", bpmn.getVersion(),
            bpmn.getResource());
        log.debug(format);
      }
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
