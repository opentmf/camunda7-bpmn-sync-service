package org.opentmf.bpmn.sync.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * @author Gokhan Demir
 */
class ResourceUtilTests {

  @Test
  void test_getBpmnFiles_returnsValidResult() {
    Resource[] resources = ResourceUtil.getBpmnFiles();
    Assertions.assertNotNull(resources);
  }

  @Test
  void test_getName_withNonBpmnResource_returnsFileName() {
    var resource = new PathMatchingResourcePatternResolver().getResource(
        "classpath:json/migration_plan.json");
    assertEquals("migration_plan.json", ResourceUtil.getName(resource));
  }

  @Test
  void test_getName_withNonFileResource_returnsResourceAsTheName() {
    var resource = new ByteArrayResource(new byte[]{1, 2, 3});
    assertEquals("Resource", ResourceUtil.getName(resource));
  }
}
