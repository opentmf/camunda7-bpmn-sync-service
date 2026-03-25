package org.opentmf.bpmn.sync.config;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @author Gokhan Demir
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "opentmf.bpmn-sync")
public class BpmnSyncProperties {

  /**
   * If false, no synchronization will be attempted.
   */
  private boolean enabled = true;

  /**
   * The name of the Camunda deployment, must be unique in repetitive calls so that
   * unnecessary redeployment can be avoided. <strong>Mandatory</strong>.
   */
  private String deploymentName;

  /**
   * The current deployment version of the BPMN files of the calling project.
   * Provide a higher version even if only one BPMN changes.
   * <strong>Mandatory</strong>.
   * <p>
   *   Please note:
   *   <ul>
   *     <li>The version comparison is performed by pure string comparion (using String.compareTo
   *     method). For example version 1.9 is greater than version 1.10 in pure string comparison.
   *     Please provide version numbers accordingly to get rid of unwanted situations.</li>
   *   </ul>
   * </p>
   */
  private String bpmnVersion;

  /**
   * In terms of milliseconds, specifies the minimum time required for a downgrade decision to be
   * made, when the requested bpmnVersion is older than the last synchronized version.
   */
  private long downgradeAllowedAfter = 600000L;

  /**
   * When set to true, the deployed BPMNs' previous version instances (only the previous version)
   * will automatically be migrated to the deployed version. It is the developer's responsibility
   * to set this property to true, when the developer thinks/approves that the changed BPMNs can
   * automatically be migrated to the latest deployed version. Otherwise, a manual migration
   * procedure must be supplied by the development team, instead of setting this flag to true.
   */
  private boolean autoMigrate = false;

  /**
   * The client-ref id to use. This id is the prefix to the following exposed beans:
   * <ul>
   *   <li>WebClient (or RestClient)</li>
   *   <li>TokenService (or SyncTokenService)</li>
   *   <li>ClientProperties</li>
   * </ul>
   * <p>
   *   For example, if the client-ref value is <strong>openid</strong> then we will look up
   *   beans named <strong>openidWebClient</strong> or <strong>openidRestClient</strong>, etc.
   * </p>
   */
  @NotEmpty
  private String clientRef;
}