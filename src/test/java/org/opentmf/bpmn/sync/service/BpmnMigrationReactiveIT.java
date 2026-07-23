package org.opentmf.bpmn.sync.service;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import lombok.extern.slf4j.Slf4j;
import org.opentmf.bpmn.sync.CamundaTestContainers;
import org.opentmf.bpmn.sync.DockerCamundaBaseIT;
import org.opentmf.bpmn.sync.client.api.ReactiveCamundaClient;
import org.opentmf.bpmn.sync.client.impl.ReactiveCamundaClientImpl;
import org.opentmf.bpmn.sync.model.CamundaDeploymentResponse;
import org.opentmf.bpmn.sync.service.api.BpmnSyncService;
import org.opentmf.bpmn.sync.service.impl.ReactiveBpmnMigrationServiceImpl;
import org.opentmf.bpmn.sync.service.impl.ReactiveBpmnSyncServiceImpl;
import org.opentmf.bpmn.sync.util.ResourceUtil;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.reactive.service.api.TokenService;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@ActiveProfiles("it")
class BpmnMigrationReactiveIT extends DockerCamundaBaseIT {

  static final CamundaTestContainers CONTAINERS = new CamundaTestContainers();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    CONTAINERS.registerProperties(r, "reactive");
  }

  private static final Resource[] BPMN_V1;
  private static final Resource[] BPMN_V2;
  private static final Resource[] BPMN_V3;

  static {
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

  private ReactiveCamundaClient buildSpiedClient() {
    String ref = bpmnSyncProperties.getClientRef();
    return Mockito.spy(new ReactiveCamundaClientImpl(
        (WebClient) ctx.getBean(ref + "WebClient"),
        (TokenService) ctx.getBean(ref + "TokenService"),
        (ClientProperties) ctx.getBean(ref + "ClientProperties"),
        camundaProperties,
        bpmnSyncProperties.getTenantId()));
  }

  private BpmnSyncService buildSyncService(ReactiveCamundaClient client) {
    var migration = new ReactiveBpmnMigrationServiceImpl(bpmnSyncProperties, client);
    return new ReactiveBpmnSyncServiceImpl(
        bpmnSyncProperties, dbLockService, client, migration);
  }

  @Test
  void testAutoMigration_withModifiedBpmn_migratesSuccessfully() {
    bpmnSyncProperties.setEnabled(true);
    var camundaClient = buildSpiedClient();
    var localSyncService = buildSyncService(camundaClient);

    try (MockedStatic<ResourceUtil> mock = Mockito.mockStatic(ResourceUtil.class)) {

      mock.when(() -> ResourceUtil.getResourceNameWithFolder(any())).thenCallRealMethod();
      // This test exercises BPMN migration only; no DMN files are involved. getDeployableResources
      // calls the real implementation, which combines the (stubbed) BPMN files with the (empty,
      // stubbed) DMN files - so the deployment tracks whatever getBpmnFiles is stubbed to below.
      mock.when(ResourceUtil::getDmnFiles).thenReturn(new Resource[0]);
      mock.when(ResourceUtil::getDeployableResources).thenCallRealMethod();
      bpmnSyncProperties.setDeploymentName("TestDeployment");

      log.debug("\n\n// Initial Deployment");
      mock.when(ResourceUtil::getBpmnFiles).thenReturn(BPMN_V1);
      bpmnSyncProperties.setAutoMigrate(false);
      bpmnSyncProperties.setBpmnVersion("v1");
      Assertions.assertDoesNotThrow(localSyncService::ensureBpmnConsistency);
      verify(camundaClient, times(0)).getProcessDefinition(anyString(), anyInt());

      log.debug("\n\n// Same Version, Deployment Not Necessary");
      mock.when(ResourceUtil::getBpmnFiles).thenReturn(BPMN_V1);
      bpmnSyncProperties.setAutoMigrate(false);
      bpmnSyncProperties.setBpmnVersion("v1");
      Assertions.assertDoesNotThrow(localSyncService::ensureBpmnConsistency);
      verify(camundaClient, times(0)).getProcessDefinition(anyString(), anyInt());

      log.debug("\n\n// Attempted Deployment, But No BPMN Change");
      mock.when(ResourceUtil::getBpmnFiles).thenReturn(BPMN_V1);
      bpmnSyncProperties.setAutoMigrate(true);
      bpmnSyncProperties.setBpmnVersion("v1.1");
      Assertions.assertDoesNotThrow(localSyncService::ensureBpmnConsistency);
      verify(camundaClient, times(0)).getProcessDefinition(anyString(), anyInt());

      log.debug("\n\n// Deployment, Changed BPMN, No Process Instance, Migration Not Necessary");
      mock.when(ResourceUtil::getBpmnFiles).thenReturn(BPMN_V2);
      bpmnSyncProperties.setAutoMigrate(true);
      bpmnSyncProperties.setBpmnVersion("v2");
      var deploy2 = Assertions.assertDoesNotThrow(localSyncService::ensureBpmnConsistency);
      verify(camundaClient, times(1)).getProcessDefinition(anyString(), anyInt());

      log.debug("\n\n// Deployment, Changed BPMN, Performs Migration");
      String processDefinitionId = processDefinitionId(deploy2, MAIN_BPMN_PROCESS_KEY);
      startProcessInstanceById(processDefinitionId);

      await()
          .atMost(10, TimeUnit.SECONDS)
          .pollInterval(500, TimeUnit.MILLISECONDS)
          .until(() -> processExists(camundaClient, deploy2, MAIN_BPMN_PROCESS_KEY));

      mock.when(ResourceUtil::getBpmnFiles).thenReturn(BPMN_V3);
      bpmnSyncProperties.setAutoMigrate(true);
      bpmnSyncProperties.setBpmnVersion("v3");
      var deploy3 = Assertions.assertDoesNotThrow(localSyncService::ensureBpmnConsistency);
      verify(camundaClient, times(2)).getProcessDefinition(anyString(), anyInt());

      await()
          .atMost(10, TimeUnit.SECONDS)
          .pollInterval(500, TimeUnit.MILLISECONDS)
          .until(() -> processExists(camundaClient, deploy3, MAIN_BPMN_PROCESS_KEY));

      Assertions.assertDoesNotThrow(() -> processDefinitionId(deploy3, DUMMY_NEW_BPMN_PROCESS_KEY));
    }
  }

  private boolean processExists(ReactiveCamundaClient client,
                                CamundaDeploymentResponse response, String bpmnProcessKey) {
    var count = client.getProcessInstanceCount(processDefinitionId(response, bpmnProcessKey))
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
