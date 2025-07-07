package org.opentmf.bpmn.sync.util;

import java.io.IOException;
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

  @Generated
  private ResourceUtil() {
  }

  public static Resource[] getBpmnFiles() {
    try {
      return new PathMatchingResourcePatternResolver().getResources("classpath:bpmn/**/*.bpmn");
    } catch (IOException e) {
      throw new IllegalArgumentException("BPMN Files not found under bpmn/* on the classpath.", e);
    }
  }

  @NonNull
  public static String getResourceNameWithFolder(@NonNull Resource r) {
    try {
      String uri = r.getURI().toString().replace('\\', '/');   // class path form
      int idx = uri.lastIndexOf("/bpmn/");
      return (idx >= 0) ? uri.substring(idx + "/bpmn/".length())
          : Objects.requireNonNull(r.getFilename());
    } catch (IOException e) {
      log.warn("Could not extract resource name with folder for {}", r, e);
      return Objects.requireNonNullElse(r.getFilename(), "Resource");
    }
  }
}
