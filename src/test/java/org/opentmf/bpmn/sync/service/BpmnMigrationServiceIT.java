package org.opentmf.bpmn.sync.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import lombok.extern.slf4j.Slf4j;
import org.opentmf.bpmn.sync.BaseIT;
import org.opentmf.bpmn.sync.model.CamundaDeploymentResponse;
import org.opentmf.bpmn.sync.util.ResourceUtil;
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
@Slf4j
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

  private static final String MAIN_BPMN_PROCESS_KEY = "Sample_BPMN";
  private static final String DUMMY_NEW_BPMN_PROCESS_KEY = "Dummy_BPMN";

  private static Resource[] getResources(String locationPattern) throws IOException {
    return new PathMatchingResourcePatternResolver().getResources(locationPattern);
  }

  @Test
  void testAutoMigration_withModifiedBpmn_migratesSuccessfully() {
    bpmnSyncProperties.setEnabled(true);
    var camundaClient = Mockito.spy(getCamundaClient());
    var bpmnSyncService = getBpmnSyncService(camundaClient);

    try (MockedStatic<ResourceUtil> mock = Mockito.mockStatic(ResourceUtil.class)) {

      mock.when(() -> ResourceUtil.getResourceNameWithFolder(any())).thenCallRealMethod();
      bpmnSyncProperties.setDeploymentName("TestDeployment");

      // initial deployment
      log.debug("\n\n// Initial Deployment");
      mock.when(ResourceUtil::getBpmnFiles).thenReturn(BPMN_V1);
      bpmnSyncProperties.setAutoMigrate(false);
      bpmnSyncProperties.setBpmnVersion("v1");
      Assertions.assertDoesNotThrow(bpmnSyncService::ensureBpmnConsistency);
      verify(camundaClient, times(0)).getProcessDefinition(anyString(), anyInt());

      // same version again, deployment not necessary
      log.debug("\n\n// Same Version, Deployment Not Necessary");
      mock.when(ResourceUtil::getBpmnFiles).thenReturn(BPMN_V1);
      bpmnSyncProperties.setAutoMigrate(false);
      bpmnSyncProperties.setBpmnVersion("v1");
      Assertions.assertDoesNotThrow(bpmnSyncService::ensureBpmnConsistency);
      verify(camundaClient, times(0)).getProcessDefinition(anyString(), anyInt());

      // attempted second deployment, but no bpmn change
      log.debug("\n\n// Attempted Deployment, But No BPMN Change");
      mock.when(ResourceUtil::getBpmnFiles).thenReturn(BPMN_V1);
      bpmnSyncProperties.setAutoMigrate(true);
      bpmnSyncProperties.setBpmnVersion("v1.1");
      Assertions.assertDoesNotThrow(bpmnSyncService::ensureBpmnConsistency);
      verify(camundaClient, times(0)).getProcessDefinition(anyString(), anyInt());

      // real second deployment, no process instance, migration not necessary
      log.debug("\n\n// Deployment, Changed BPMN, No Process Instance, Migration Not Necessary");
      mock.when(ResourceUtil::getBpmnFiles).thenReturn(BPMN_V2);
      bpmnSyncProperties.setAutoMigrate(true);
      bpmnSyncProperties.setBpmnVersion("v2");
      var deploy2 = Assertions.assertDoesNotThrow(bpmnSyncService::ensureBpmnConsistency);
      verify(camundaClient, times(1)).getProcessDefinition(anyString(), anyInt());

      // third deployment, we start one process instance, successful migration
      log.debug("\n\n// Deployment, Changed BPMN, Performs Migration");
      String processDefinitionId = processDefinitionId(deploy2, MAIN_BPMN_PROCESS_KEY);
      ProcessEngines.getDefaultProcessEngine().getRuntimeService()
          .startProcessInstanceById(processDefinitionId);

      await()
          .atMost(1500, TimeUnit.MILLISECONDS)
          .pollInterval(100, TimeUnit.MILLISECONDS)
          .until(() -> processExists(deploy2, MAIN_BPMN_PROCESS_KEY));

      mock.when(ResourceUtil::getBpmnFiles).thenReturn(BPMN_V3);
      bpmnSyncProperties.setAutoMigrate(true);
      bpmnSyncProperties.setBpmnVersion("v3");
      var deploy3 = Assertions.assertDoesNotThrow(bpmnSyncService::ensureBpmnConsistency);
      //expect to call twice as we use same spy object that was already called once for v2
      verify(camundaClient, times(2)).getProcessDefinition(anyString(), anyInt());

      await()
          .atMost(1500, TimeUnit.MILLISECONDS)
          .pollInterval(100, TimeUnit.MILLISECONDS)
          .until(() -> processExists(deploy3, MAIN_BPMN_PROCESS_KEY));

      //we verify dummy bpmn is deployed.
      Assertions.assertDoesNotThrow(() -> processDefinitionId(deploy3, DUMMY_NEW_BPMN_PROCESS_KEY));

    }
  }

  private boolean processExists(CamundaDeploymentResponse response, String bpmnProcessKey) {
    var count = getCamundaClient()
        .getProcessInstanceCount(processDefinitionId(response, bpmnProcessKey))
        .block();
    return count != null && count.getCount() > 0;
  }

  private String processDefinitionId(CamundaDeploymentResponse response, String bpmnProcessKey) {
    return response.getDeployedProcessDefinitions().values().stream()
        .filter(processDefinition -> processDefinition.getKey().equals(bpmnProcessKey))
        .findFirst()
        .orElseThrow(IllegalStateException::new)
        .getId();
  }
}
