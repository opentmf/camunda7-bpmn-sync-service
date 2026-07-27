package org.opentmf.bpmn.sync.client.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.opentmf.bpmn.sync.config.CamundaProperties;
import org.opentmf.client.common.model.ClientProperties;
import org.springframework.core.io.Resource;

class ReactiveCamundaClientImplTest {

  private ReactiveCamundaClientImpl client(String tenantId) {
    var props = new ClientProperties();
    props.setNumRetries(0);
    props.setRetryWaitDuration(Duration.ofMillis(100L));
    var camundaProperties = new CamundaProperties();
    camundaProperties.setBaseUrl("http://localhost:8080/engine-rest");
    return new ReactiveCamundaClientImpl(null, null, props, camundaProperties, tenantId,
        "classpath:bpmn/");
  }

  @Test
  void processDefinitionsUri_withTenant_addsTenantIdInQueryParam() {
    var uri = client("tenant-a").processDefinitionsUri("key", 2);
    assertTrue(uri.getQuery().contains("tenantIdIn=tenant-a"));
  }

  @Test
  void processDefinitionsUri_withoutTenant_omitsTenantIdInQueryParam() {
    var uri = client(null).processDefinitionsUri("key", 2);
    assertFalse(uri.getQuery().contains("tenantIdIn"));
  }

  @Test
  void getMultipartRequest_withTenant_addsTenantIdPart() {
    var body = client("tenant-a").getMultipartRequest("deploy", new Resource[0]);
    var tenantPart = body.getFirst("tenant-id");
    assertNotNull(tenantPart);
    assertEquals("tenant-a", tenantPart.getBody());
  }

  @Test
  void getMultipartRequest_withoutTenant_omitsTenantIdPart() {
    var body = client(null).getMultipartRequest("deploy", new Resource[0]);
    assertFalse(body.containsKey("tenant-id"));
    assertEquals("deploy", body.getFirst("deployment-name").getBody());
  }
}
