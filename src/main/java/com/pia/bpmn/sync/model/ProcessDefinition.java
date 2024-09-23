package com.pia.bpmn.sync.model;

import lombok.Getter;
import lombok.Setter;

/**
 * @author Gokhan Demir
 */
@Getter
@Setter
public class ProcessDefinition {

    private String id;
    private String key;
    private String category;
    private String description;
    private String name;
    private int version;
    private String resource;
    private String deploymentId;
    private String diagram;
    private boolean suspended;
    private String tenantId;
    private String versionTag;
    private Integer historyTimeToLive;
    private boolean isStartableInTasklist;
}
