package org.opentmf.bpmn.sync;

import org.opentmf.bpmn.sync.config.BpmnSyncAutoConfiguration;
import org.opentmf.bpmn.sync.config.BpmnSyncProperties;
import org.opentmf.bpmn.sync.config.CamundaProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

/**
 * @author Gokhan Demir
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@DirtiesContext
class BpmnSyncApplicationIT {

  @Test
  void testBpmnSyncAutoConfiguration_withMissingBeans_throwsException() {
    var ctx = Mockito.mock(ApplicationContext.class);
    var props = new BpmnSyncProperties();
    props.setClientRef("missing");
    Mockito.when(ctx.getBean("missingClientProperties"))
        .thenThrow(new NoSuchBeanDefinitionException("missingClientProperties"));

    Assertions.assertThrows(
        NoSuchBeanDefinitionException.class,
        () -> new BpmnSyncAutoConfiguration(
            ctx, props, new CamundaProperties(), null));
  }
}
