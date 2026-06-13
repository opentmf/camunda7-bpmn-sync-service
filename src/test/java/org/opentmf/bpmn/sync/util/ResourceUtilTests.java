package org.opentmf.bpmn.sync.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
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
    Assertions.assertEquals(17, resources.length);
    var set = new HashSet<String>();
    for (Resource r : resources) {
      var name = ResourceUtil.getResourceNameWithFolder(r);
      Assertions.assertFalse(name.startsWith("/"));
      Assertions.assertFalse(name.startsWith("/bpmn"));
      set.add(name);
    }
    Assertions.assertEquals(17, set.size());
  }

  @Test
  void test_getDmnFiles_returnsValidResult() {
    Resource[] resources = ResourceUtil.getDmnFiles();
    Assertions.assertNotNull(resources);
    Assertions.assertEquals(2, resources.length);
    var set = new HashSet<String>();
    for (Resource r : resources) {
      var name = ResourceUtil.getResourceNameWithFolder(r);
      Assertions.assertFalse(name.startsWith("/"));
      Assertions.assertFalse(name.startsWith("/dmn"));
      set.add(name);
    }
    Assertions.assertEquals(2, set.size());
    // sub-folder structure under /dmn/ is preserved, the root is stripped
    Assertions.assertTrue(set.contains("routing.dmn"));
    Assertions.assertTrue(set.contains("sub/bounce.dmn"));
  }

  @Test
  void test_getDeployableResources_combinesBpmnAndDmn() {
    Resource[] resources = ResourceUtil.getDeployableResources();
    Assertions.assertNotNull(resources);
    Assertions.assertEquals(
        ResourceUtil.getBpmnFiles().length + ResourceUtil.getDmnFiles().length, resources.length);
    Assertions.assertEquals(19, resources.length);
  }

  @Test
  void test_getName_withNonBpmnResource_returnsFileName() {
    var resource = new PathMatchingResourcePatternResolver().getResource(
        "classpath:json/migration_plan.json");
    assertEquals("migration_plan.json", ResourceUtil.getResourceNameWithFolder(resource));
  }

  @Test
  void test_getName_withNonFileResource_returnsResourceAsTheName() {
    var resource = new ByteArrayResource(new byte[]{1, 2, 3});
    assertEquals("Resource", ResourceUtil.getResourceNameWithFolder(resource));
  }
}
