package com.pia.bpmn.sync.service;

import static org.mockito.ArgumentMatchers.any;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import com.pia.bpmn.sync.BaseIT;
import com.pia.bpmn.sync.model.CamundaDeploymentResponse;
import com.pia.bpmn.sync.util.ResourceUtil;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.camunda.bpm.engine.ProcessEngines;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("embedded-camunda")
@DirtiesContext
class BpmnMigrationServiceIT extends BaseIT {

  private static final Resource[] BPMN_V1;
  private static final Resource[] BPMN_V2;
  private static final Resource[] BPMN_V3;

  static {
    System.setProperty("desired.port", "8882");
    try {
      BPMN_V1 = getResources("classpath:modified_bpmn/v1/**/*.bpmn");
      BPMN_V2 = getResources("classpath:modified_bpmn/v2/**/*.bpmn");
      BPMN_V3 = getResources("classpath:modified_bpmn/v3/**/*.bpmn");
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static Resource[] getResources(String locationPattern) throws IOException {
    return new PathMatchingResourcePatternResolver().getResources(locationPattern);
  }

  @Test
  void testAutoMigration_withModifiedBpmn_migratesSuccessfully() {
    bpmnSyncProperties.setEnabled(true);
    var bpmnSyncService = getBpmnSyncService();

    try (MockedStatic<ResourceUtil> mock = Mockito.mockStatic(ResourceUtil.class)) {

      mock.when(() -> ResourceUtil.getName(any())).thenCallRealMethod();
      bpmnSyncProperties.setDeploymentName("TestDeployment");

      // initial deployment
      mock.when(ResourceUtil::getBpmnFiles).thenReturn(BPMN_V1);
      bpmnSyncProperties.setAutoMigrate(false);
      bpmnSyncProperties.setBpmnVersion("v1");
      Assertions.assertDoesNotThrow(bpmnSyncService::ensureBpmnConsistency);

      // same version again, deployment not necessary
      mock.when(ResourceUtil::getBpmnFiles).thenReturn(BPMN_V1);
      bpmnSyncProperties.setAutoMigrate(false);
      bpmnSyncProperties.setBpmnVersion("v1");
      Assertions.assertDoesNotThrow(bpmnSyncService::ensureBpmnConsistency);

      // attempted second deployment, but no bpmn change
      mock.when(ResourceUtil::getBpmnFiles).thenReturn(BPMN_V1);
      bpmnSyncProperties.setAutoMigrate(false);
      bpmnSyncProperties.setBpmnVersion("v1.1");
      Assertions.assertDoesNotThrow(bpmnSyncService::ensureBpmnConsistency);

      // real second deployment, no process instance, migration not necessary
      mock.when(ResourceUtil::getBpmnFiles).thenReturn(BPMN_V2);
      bpmnSyncProperties.setAutoMigrate(true);
      bpmnSyncProperties.setBpmnVersion("v2");
      var deploy2 = Assertions.assertDoesNotThrow(bpmnSyncService::ensureBpmnConsistency);

      // third deployment, we start one process instance, successful migration
      String processDefinitionId = processDefinitionId(deploy2);
      ProcessEngines.getDefaultProcessEngine().getRuntimeService()
          .startProcessInstanceById(processDefinitionId);

      await()
          .atMost(1500, TimeUnit.MILLISECONDS)
          .pollInterval(100, TimeUnit.MILLISECONDS)
          .until(() -> processExists(deploy2));

      mock.when(ResourceUtil::getBpmnFiles).thenReturn(BPMN_V3);
      bpmnSyncProperties.setAutoMigrate(true);
      bpmnSyncProperties.setBpmnVersion("v3");
      var deploy3 = Assertions.assertDoesNotThrow(bpmnSyncService::ensureBpmnConsistency);

      await()
          .atMost(1500, TimeUnit.MILLISECONDS)
          .pollInterval(100, TimeUnit.MILLISECONDS)
          .until(() -> processExists(deploy3));
    }
  }

  private boolean processExists(CamundaDeploymentResponse response) {
    var count = getCamundaClient().getProcessInstanceCount(processDefinitionId(response)).block();
    return count != null && count.getCount() > 0;
  }

  private String processDefinitionId(CamundaDeploymentResponse response) {
    return response.getDeployedProcessDefinitions().values().stream()
        .findFirst()
        .orElseThrow(IllegalStateException::new)
        .getId();
  }
}
