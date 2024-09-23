package com.pia.bpmn.sync.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * @author Gokhan Demir
 */
@Getter
@Setter
@ToString
public class ExecuteMigrationPlanAsyncResponse {

  private String id;
  private String type;
  private Integer totalJobs;
  private Integer jobsCreated;
  private Integer batchJobsPerSeed;
  private Integer invocationsPerBatchJob;
  private String seedJobDefinitionId;
  private String monitorJobDefinitionId;
  private String batchJobDefinitionId;
  private boolean suspended;
  private String tenantId;
  private String createUserId;
  private String startTime;
  private String executionStartTime;
}
