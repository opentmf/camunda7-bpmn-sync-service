package org.opentmf.bpmn.sync.service;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.opentmf.bpmn.sync.CamundaTestContainers;
import org.opentmf.bpmn.sync.DockerCamundaBaseIT;
import org.opentmf.bpmn.sync.client.api.RestCamundaClient;
import org.opentmf.bpmn.sync.client.impl.RestCamundaClientImpl;
import org.opentmf.bpmn.sync.model.CamundaDeploymentResponse;
import org.opentmf.bpmn.sync.model.ProcessDefinition;
import org.opentmf.bpmn.sync.service.impl.RestBpmnMigrationServiceImpl;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.rest.service.api.SyncTokenService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/**
 * Verifies Camunda 7 multi-tenant deployment isolation: the same BPMN (same process id, same
 * deployment name) deployed under two tenants yields two independent definitions with independent
 * version counters, per-tenant duplicate filtering, tenant-scoped definition lookup, and
 * auto-migration that never crosses tenants.
 */
@Slf4j
@ActiveProfiles("it")
class TenantIsolationRestIT extends DockerCamundaBaseIT {

  static final CamundaTestContainers CONTAINERS = new CamundaTestContainers();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    CONTAINERS.registerProperties(r, "rest");
  }

  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";
  private static final String DEPLOYMENT_NAME = "TenantDeployment";
  private static final String PROCESS_KEY = "Sample_BPMN";

  private static final Resource[] BPMN_V1;
  private static final Resource[] BPMN_V2;

  static {
    try {
      BPMN_V1 = getResources("classpath:modified_bpmn/v1/**/*.bpmn");
      BPMN_V2 = getResources("classpath:modified_bpmn/v2/**/*.bpmn");
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static Resource[] getResources(String locationPattern) throws IOException {
    return new PathMatchingResourcePatternResolver().getResources(locationPattern);
  }

  private RestCamundaClient buildClient(String tenantId) {
    String ref = bpmnSyncProperties.getClientRef();
    return new RestCamundaClientImpl(
        (RestClient) ctx.getBean(ref + "RestClient"),
        (SyncTokenService) ctx.getBean(ref + "TokenService"),
        (ClientProperties) ctx.getBean(ref + "ClientProperties"),
        camundaProperties,
        tenantId,
        bpmnSyncProperties.getResourceLocation());
  }

  @Test
  void testTenantIsolation_sameBpmnUnderTwoTenants_deploysAndMigratesIndependently() {
    var clientA = buildClient(TENANT_A);
    var clientB = buildClient(TENANT_B);

    log.debug("\n\n// Initial deployment under tenant A");
    var deployA1 = clientA.syncResources(DEPLOYMENT_NAME, BPMN_V1);
    var definitionA1 = singleDefinition(deployA1);
    assertEquals(TENANT_A, definitionA1.getTenantId());
    assertEquals(1, definitionA1.getVersion());

    log.debug("\n\n// Same BPMN, same deployment name under tenant B: independent definition");
    var deployB1 = clientB.syncResources(DEPLOYMENT_NAME, BPMN_V1);
    var definitionB1 = singleDefinition(deployB1);
    assertEquals(TENANT_B, definitionB1.getTenantId());
    // B's version counter is independent of A's: still an initial version 1.
    assertEquals(1, definitionB1.getVersion());
    assertEquals(definitionA1.getKey(), definitionB1.getKey());
    assertNotEquals(definitionA1.getId(), definitionB1.getId());

    log.debug("\n\n// Redeploy under tenant A: duplicate filtering works per tenant");
    var redeployA = clientA.syncResources(DEPLOYMENT_NAME, BPMN_V1);
    assertEquals(0, redeployA.totalDeployedCount());

    log.debug("\n\n// Definition lookup is tenant-scoped");
    assertEquals(definitionA1.getId(), clientA.getProcessDefinition(PROCESS_KEY, 1).getId());
    assertEquals(definitionB1.getId(), clientB.getProcessDefinition(PROCESS_KEY, 1).getId());

    log.debug("\n\n// Start one instance per tenant");
    startProcessInstanceById(definitionA1.getId());
    startProcessInstanceById(definitionB1.getId());
    awaitInstanceCount(clientA, definitionA1.getId(), 1);
    awaitInstanceCount(clientB, definitionB1.getId(), 1);

    log.debug("\n\n// Deploy v2 under tenant A only");
    var deployA2 = clientA.syncResources(DEPLOYMENT_NAME, BPMN_V2);
    var definitionA2 = singleDefinition(deployA2);
    assertEquals(TENANT_A, definitionA2.getTenantId());
    assertEquals(2, definitionA2.getVersion());
    assertNull(clientB.getProcessDefinition(PROCESS_KEY, 2));

    log.debug("\n\n// Auto-migration migrates tenant A's instance only");
    bpmnSyncProperties.setAutoMigrate(true);
    new RestBpmnMigrationServiceImpl(bpmnSyncProperties, clientA).performAutoMigration(deployA2);
    awaitInstanceCount(clientA, definitionA2.getId(), 1);
    assertEquals(0, clientA.getProcessInstanceCount(definitionA1.getId()).getCount());
    assertEquals(1, clientB.getProcessInstanceCount(definitionB1.getId()).getCount());
  }

  private ProcessDefinition singleDefinition(CamundaDeploymentResponse deployment) {
    assertNotNull(deployment.getDeployedProcessDefinitions());
    assertEquals(1, deployment.getDeployedProcessDefinitions().size());
    return deployment.getDeployedProcessDefinitions().values().iterator().next();
  }

  private void awaitInstanceCount(RestCamundaClient client, String processDefinitionId,
      long expected) {
    await()
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(500, TimeUnit.MILLISECONDS)
        .until(() -> client.getProcessInstanceCount(processDefinitionId).getCount() == expected);
  }
}
