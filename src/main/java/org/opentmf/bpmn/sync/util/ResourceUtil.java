package org.opentmf.bpmn.sync.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Generated;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.lang.NonNull;

/**
 * @author Gokhan Demir
 */
@Slf4j
public final class ResourceUtil {

  /**
   * The classpath roots that hold deployable Camunda resources. The order is also the order in
   * which the resources are handed to Camunda's deployment endpoint. Each root is stripped from a
   * resource's URI when computing its deployment (multipart part) name, so that sub-folder
   * structure under the root is preserved while the root itself is not.
   */
  private static final String[] DEPLOYABLE_ROOTS = {"/bpmn/", "/dmn/"};

  @Generated
  private ResourceUtil() {
  }

  public static Resource[] getBpmnFiles() {
    return getResources("classpath:bpmn/**/*.bpmn", "BPMN");
  }

  public static Resource[] getDmnFiles() {
    return getResources("classpath:dmn/**/*.dmn", "DMN");
  }

  /**
   * Returns every deployable resource (BPMN first, then DMN) in a single array, ready to be sent to
   * Camunda's {@code /deployment/create} endpoint in one multipart call. Camunda distinguishes BPMN
   * from DMN by file extension, so both kinds can travel together.
   */
  public static Resource[] getDeployableResources() {
    Resource[] bpmnFiles = getBpmnFiles();
    Resource[] dmnFiles = getDmnFiles();
    List<Resource> all = new ArrayList<>(bpmnFiles.length + dmnFiles.length);
    all.addAll(List.of(bpmnFiles));
    all.addAll(List.of(dmnFiles));
    return all.toArray(new Resource[0]);
  }

  private static Resource[] getResources(String locationPattern, String kind) {
    try {
      return new PathMatchingResourcePatternResolver().getResources(locationPattern);
    } catch (IOException e) {
      throw new IllegalArgumentException(
          kind + " files could not be resolved for pattern " + locationPattern, e);
    }
  }

  @NonNull
  public static String getResourceNameWithFolder(@NonNull Resource r) {
    try {
      String uri = r.getURI().toString().replace('\\', '/'); // class path form
      for (String root : DEPLOYABLE_ROOTS) {
        int idx = uri.lastIndexOf(root);
        if (idx >= 0) {
          return uri.substring(idx + root.length());
        }
      }
      return Objects.requireNonNull(r.getFilename());
    } catch (IOException e) {
      log.warn("Could not extract resource name with folder for {}", r, e);
      return Objects.requireNonNullElse(r.getFilename(), "Resource");
    }
  }
}
