# Camunda7 BPMN Synchronization Service
This service synchronizes the BPMN files under `classpath:bpmn/` **and the DMN files under
`classpath:dmn/`** with the configured Camunda server, in a single, version-guarded deployment.

Depending on the value of auto-migrate, migrates the deployed BPMN's previous version's process instances to the newly deployed version. DMN decision definitions have no process instances and are never migrated.

## Auto Migration
BPMN Sync Service supports automatically migrating the process instances of the deployed BPMNs' previous version to the new version. To enable auto migration, set auto-migrate to true.

The potential failure of auto-migration, will not affect application startup.

The result of the auto-migration can be checked using Camunda Cockpit.

## How the Service works
- Obtains a db lock for lock type BPMN.
- Reads the latest synchronized BPMN version from db_lock_latest table.
- If the new BPMN version is the same as the latest synchonized version, then the service just releases the db lock and silently returns.
- If either a downgrade or an upgrade is necessary:
    - Deploys the BPMN files under src/main/resources/bpmn to Camunda in a single API call, using Camunda's /deployment/create endpoint.
    - Obtains the deployment result. If no BPMN files are deployed, logs a warning to the developer to not increase the BPMN version when no changes to the BPMNs are present, releases the db lock and returns.
    - If the auto-migrate is set to true, for each deployed BPMN file:
        - Retrieves the process definition for the previous version.
        - If no process definition exists with the previous version, skips that BPMN and continues the loop.
        - Retrieves the process instance count for the previous process definition.
        - If the instance count is zero, skips that BPMN and continues the loop.
        - Generates a migration plan for migrating the process instances from previous version to the newly deployed version.
        - Triggers the execution of the generated migration plan in an async manner, and continues the loop.

## Using the Service
To use this service from a microservice, the following six small steps are necessary:

### 1. pom.xml Additions
Import opentmf-versions for managing the opentmf library dependencies
```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.opentmf</groupId>
      <artifactId>opentmf-versions</artifactId>
      <version>LATEST</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```
Depend on the camunda7-bpmn-sync-service:
```xml
  <dependency>
    <groupId>org.opentmf.camunda</groupId>
    <artifactId>camunda7-bpmn-sync-service</artifactId>
  </dependency>
```
### 2. Reorganize the BPMN and DMN files
1. In your microservice, the BPMN files must be under **src/main/resources/bpmn** folder. All *.bpmn files within this folder and its sub-folders will be used in the synchronization process.
2. DMN files (decision tables / DRDs) must be under **src/main/resources/dmn** folder. All *.dmn files within this folder and its sub-folders are deployed together with the BPMN files, in the **same** Camunda deployment. A single `opentmf.bpmn-sync.bpmn-version` governs the whole bundle — bump it whenever **any** BPMN *or* DMN changes.
    * Note: If your microservice uses embedded Camunda for its IT tests, you can benefit from this camunda configuration property: [camunda.deployment-resource-pattern](https://docs.camunda.org/manual/7.19/user-guide/spring-boot-integration/configuration/)
3. Ensure one BPMN/DMN is deployed by one microservice. Do not try to deploy the same resource in a different microservice.

### 3. Specify the BPMN Sync Properties
In your application.yaml, specify the BPMN Sync Properties:
```yaml
opentmf:
  bpmn-sync:
    enabled: true
    deployment-name: MyApp
    bpmn-version: 1.0.0
    auto-migrate: true
    client: default
```

**client:** The client id to use. This id is the prefix to the following exposed beans:

1. `${client}WebClient`
2. `${client}TokenService`
3. `${client}ClientProperties`

The BPMN Sync Service remembers the latest deployed BPMN versions. If the specified bpmnVersion is already the latest deployed version, then no synchronization will take place. Therefore, it is the developers' responsibility to increase the bpmn-version when any of the BPMN files changes, to enforce the BPMN synchronization.

### 4. Disable JDBC Repositories
JDBC template is used only to obtain the DB connections by the db lock service and the rest is performed by pure JDBC calls by the DB Lock service. However, Spring Boot does not know this beforehand and checks if JDBC repositories can also be used as the repository implementations. In order to let Spring Boot know that we don't want to use JDBC repositories, the following should be added to application.yml file:

```yaml
spring:
  data:
    jdbc:
      repositories:
        enabled: false
```

### 5. Skip BPMN Sync in IT Tests
In order to skip the BPMN Sync in the IT tests, first disable the BPMN Sync Service in your application-it.yml file:
```yaml
opentmf:
  db-lock:
    create-tables: false
  bpmn-sync:
    enabled: false
```

### 6. Expose `objectMapper` Bean
The BPMN Sync Service requires the `objectMapper` bean to serialize and deserialize the Camunda API exchanges. Therefore it is required to expose a bean of type `com.fasterxml.jackson.databind.ObjectMapper` with the bean name `objectMapper`.

## Sample Logs
Here are some sample log statements produced by the integration test that walks the deployment/migration scenarios (`BpmnMigrationRestIT`):

### Initial Deployment, Migration not necessary
```text
33:00.160 INFO  [main] o.o.b.s.s.i.RestBpmnSyncServiceImpl -- Starting BPMN Deployment for TestDeployment, version: v1
33:00.162 DEBUG [main] o.o.d.l.s.i.DbLockServiceImpl -- Attempting to acquire lock for lockType = BPMN, and lockVersion = v1
33:00.174 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK(lock_type, lock_version, hostname) values (('B'), ('v1'), ('957523cc-b664-475b-81de-4daf2c6d0ee2.fritz.box'))
RETURNING *
33:00.178 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: select lock_version, lock_acquired_on from DB_LOCK_LATEST where lock_type = ('B')
33:00.182 DEBUG [main] o.o.d.l.s.i.DbLockServiceImpl -- Acquired lock id = 1 for lockType = BPMN, and lockVersion = v1.
33:00.182 INFO  [main] o.o.b.s.s.i.RestBpmnSyncServiceImpl -- Will synchronize 1 BPMN/DMN files, for TestDeployment, bpmnVersion: v1
33:00.187 DEBUG [main] o.o.c.b.s.SyncTokenClientImpl -- Will retrieve a new bearer token (sync) from url: http://localhost:54921/realms/opentmf/protocol/openid-connect/token, scope: null, username: null
33:00.672 INFO  [main] o.o.b.s.s.i.RestBpmnSyncServiceImpl -- Deployment for TestDeployment, version v1 has been completed. Deployed artifacts - BPMN: 1, DMN: 0, DRD: 0.
33:00.672 DEBUG [main] o.o.b.s.s.i.RestBpmnSyncServiceImpl -- Deployed BPMN files and their versions follow:
33:00.672 DEBUG [main] o.o.b.s.s.i.RestBpmnSyncServiceImpl -- Version: 1, BPMN: SampleBpmn.bpmn
33:00.672 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: select count(*) from DB_LOCK_LATEST where lock_type = ('B')
33:00.673 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK_LATEST (lock_type, lock_version, hostname, lock_acquired_on) select lock_type, lock_version, hostname, created_on from DB_LOCK where id = ('1'::int4)
33:00.673 TRACE [main] o.o.d.l.s.i.DbLockServiceImpl -- Initialized LatestLock using lockId = 1
33:00.674 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK_HISTORY (lock_id, lock_type, lock_version, hostname, lock_acquired_on) select id, lock_type, lock_version, hostname, created_on from DB_LOCK where DB_LOCK.id = ('1'::int4)
33:00.674 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: delete from DB_LOCK where id = ('1'::int4) and lock_type = ('B')
33:00.675 INFO  [main] o.o.d.l.s.i.DbLockServiceImpl -- Released lock after 0 seconds. lockId = 1, and lockType = BPMN
33:00.675 TRACE [main] o.o.d.l.s.i.DbLockServiceImpl -- Cancelling auto lock release timer for lock id = 1, type = BPMN
33:00.675 INFO  [main] o.o.b.s.s.i.RestBpmnMigrationServiceImpl -- Skipping BPMN migration because auto-migrate is set to false.
```

### When all files are up-to-date
```text
33:00.677 INFO  [main] o.o.b.s.s.i.RestBpmnSyncServiceImpl -- Starting BPMN Deployment for TestDeployment, version: v1
33:00.677 DEBUG [main] o.o.d.l.s.i.DbLockServiceImpl -- Attempting to acquire lock for lockType = BPMN, and lockVersion = v1
33:00.677 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK(lock_type, lock_version, hostname) values (('B'), ('v1'), ('957523cc-b664-475b-81de-4daf2c6d0ee2.fritz.box'))
RETURNING *
33:00.678 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: select lock_version, lock_acquired_on from DB_LOCK_LATEST where lock_type = ('B')
33:00.679 DEBUG [main] o.o.d.l.s.i.DbLockServiceImpl -- Acquired lock id = 2 for lockType = BPMN, and lockVersion = v1.
33:00.679 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK_HISTORY (lock_id, lock_type, lock_version, hostname, lock_acquired_on) select id, lock_type, lock_version, hostname, created_on from DB_LOCK where DB_LOCK.id = ('2'::int4)
33:00.680 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: delete from DB_LOCK where id = ('2'::int4) and lock_type = ('B')
33:00.680 INFO  [main] o.o.d.l.s.i.DbLockServiceImpl -- Released lock after 0 seconds. lockId = 2, and lockType = BPMN
33:00.681 TRACE [main] o.o.d.l.s.i.DbLockServiceImpl -- Cancelling auto lock release timer for lock id = 2, type = BPMN
33:00.681 INFO  [main] o.o.b.s.s.i.RestBpmnSyncServiceImpl -- TestDeployment BPMN/DMN resources are already up-to-date for version v1.
33:00.681 INFO  [main] o.o.b.s.s.i.RestBpmnMigrationServiceImpl -- Skipping BPMN migration because auto-migrate is set to false.
```

### When all files are up-to-date but version is increased
```text
33:00.685 INFO  [main] o.o.b.s.s.i.RestBpmnSyncServiceImpl -- Starting BPMN Deployment for TestDeployment, version: v1.1
33:00.686 DEBUG [main] o.o.d.l.s.i.DbLockServiceImpl -- Attempting to acquire lock for lockType = BPMN, and lockVersion = v1.1
33:00.686 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK(lock_type, lock_version, hostname) values (('B'), ('v1.1'), ('957523cc-b664-475b-81de-4daf2c6d0ee2.fritz.box'))
RETURNING *
33:00.687 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: select lock_version, lock_acquired_on from DB_LOCK_LATEST where lock_type = ('B')
33:00.688 DEBUG [main] o.o.d.l.s.i.DbLockServiceImpl -- Acquired lock id = 3 for lockType = BPMN, and lockVersion = v1.1.
33:00.688 INFO  [main] o.o.b.s.s.i.RestBpmnSyncServiceImpl -- Will synchronize 1 BPMN/DMN files, for TestDeployment, bpmnVersion: v1.1
33:00.689 TRACE [main] o.o.c.b.s.SyncBearerTokenServiceImpl -- Returning cached bearer token (sync) for scope: , username: null
33:00.701 WARN  [main] o.o.b.s.s.i.RestBpmnSyncServiceImpl -- TestDeployment synchronization completed without deploying any BPMN or DMN. The specified bpmnVersion was: v1.1. Hint: Do not change the bpmnVersion when there are no BPMN/DMN changes.
33:00.701 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK_HISTORY (lock_id, lock_type, lock_version, hostname, lock_acquired_on) select id, lock_type, lock_version, hostname, created_on from DB_LOCK where DB_LOCK.id = ('3'::int4)
33:00.702 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: delete from DB_LOCK where id = ('3'::int4) and lock_type = ('B')
33:00.702 INFO  [main] o.o.d.l.s.i.DbLockServiceImpl -- Released lock after 0 seconds. lockId = 3, and lockType = BPMN
33:00.703 TRACE [main] o.o.d.l.s.i.DbLockServiceImpl -- Cancelling auto lock release timer for lock id = 3, type = BPMN
33:00.703 INFO  [main] o.o.b.s.s.i.RestBpmnMigrationServiceImpl -- Auto-migration not necessary because no new BPMN has been deployed.
```

### When a single BPMN File Is Changed, auto-migrate: true, no previous version process instances exist
```text
33:00.704 INFO  [main] o.o.b.s.s.i.RestBpmnSyncServiceImpl -- Starting BPMN Deployment for TestDeployment, version: v2
33:00.704 DEBUG [main] o.o.d.l.s.i.DbLockServiceImpl -- Attempting to acquire lock for lockType = BPMN, and lockVersion = v2
33:00.704 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK(lock_type, lock_version, hostname) values (('B'), ('v2'), ('957523cc-b664-475b-81de-4daf2c6d0ee2.fritz.box'))
RETURNING *
33:00.704 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: select lock_version, lock_acquired_on from DB_LOCK_LATEST where lock_type = ('B')
33:00.705 DEBUG [main] o.o.d.l.s.i.DbLockServiceImpl -- Acquired lock id = 4 for lockType = BPMN, and lockVersion = v2.
33:00.705 INFO  [main] o.o.b.s.s.i.RestBpmnSyncServiceImpl -- Will synchronize 1 BPMN/DMN files, for TestDeployment, bpmnVersion: v2
33:00.706 TRACE [main] o.o.c.b.s.SyncBearerTokenServiceImpl -- Returning cached bearer token (sync) for scope: , username: null
33:00.728 INFO  [main] o.o.b.s.s.i.RestBpmnSyncServiceImpl -- Deployment for TestDeployment, version v2 has been completed. Deployed artifacts - BPMN: 1, DMN: 0, DRD: 0.
33:00.728 DEBUG [main] o.o.b.s.s.i.RestBpmnSyncServiceImpl -- Deployed BPMN files and their versions follow:
33:00.728 DEBUG [main] o.o.b.s.s.i.RestBpmnSyncServiceImpl -- Version: 2, BPMN: SampleBpmn.bpmn
33:00.728 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: select count(*) from DB_LOCK_LATEST where lock_type = ('B')
33:00.729 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: update DB_LOCK_LATEST T set lock_version = L.lock_version, hostname = L.hostname, lock_acquired_on = L.created_on from DB_LOCK L where L.id = ('4'::int4) and T.lock_type = L.lock_type
33:00.729 TRACE [main] o.o.d.l.s.i.DbLockServiceImpl -- Updated LatestLock using lockId = 4
33:00.730 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK_HISTORY (lock_id, lock_type, lock_version, hostname, lock_acquired_on) select id, lock_type, lock_version, hostname, created_on from DB_LOCK where DB_LOCK.id = ('4'::int4)
33:00.730 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: delete from DB_LOCK where id = ('4'::int4) and lock_type = ('B')
33:00.730 INFO  [main] o.o.d.l.s.i.DbLockServiceImpl -- Released lock after 0 seconds. lockId = 4, and lockType = BPMN
33:00.731 TRACE [main] o.o.d.l.s.i.DbLockServiceImpl -- Cancelling auto lock release timer for lock id = 4, type = BPMN
33:00.731 INFO  [main] o.o.b.s.s.i.RestBpmnMigrationServiceImpl -- Starting BPMN migration for 1 deployed BPMNs.
33:00.737 TRACE [main] o.o.c.b.s.SyncBearerTokenServiceImpl -- Returning cached bearer token (sync) for scope: , username: null
33:00.850 DEBUG [main] o.o.b.s.s.i.RestBpmnMigrationServiceImpl -- Previous version's process definition id: Sample_BPMN:1:335f17a4-689d-11f1-8aae-5a250421cbe0
33:00.850 TRACE [main] o.o.b.s.s.i.RestBpmnMigrationServiceImpl -- Getting process instance count for process definition id: Sample_BPMN:1:335f17a4-689d-11f1-8aae-5a250421cbe0
33:00.850 TRACE [main] o.o.c.b.s.SyncBearerTokenServiceImpl -- Returning cached bearer token (sync) for scope: , username: null
33:00.875 DEBUG [main] o.o.b.s.s.i.RestBpmnMigrationServiceImpl -- Migration not necessary for Sample_BPMN version 1 to 2 because no process instances exist.
33:00.875 INFO  [main] o.o.b.s.s.i.RestBpmnMigrationServiceImpl -- BPMN Migration completed without creating any async migration jobs.
```

### When a single BPMN File Is Changed, auto-migrate: true, migration jobs are created
```text
33:00.876 TRACE [main] o.o.c.b.s.SyncBearerTokenServiceImpl -- Returning cached bearer token (sync) for scope: , username: null
33:01.574 INFO  [main] o.o.b.s.s.i.RestBpmnSyncServiceImpl -- Starting BPMN Deployment for TestDeployment, version: v3
33:01.575 DEBUG [main] o.o.d.l.s.i.DbLockServiceImpl -- Attempting to acquire lock for lockType = BPMN, and lockVersion = v3
33:01.576 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK(lock_type, lock_version, hostname) values (('B'), ('v3'), ('957523cc-b664-475b-81de-4daf2c6d0ee2.fritz.box'))
RETURNING *
33:01.577 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: select lock_version, lock_acquired_on from DB_LOCK_LATEST where lock_type = ('B')
33:01.579 DEBUG [main] o.o.d.l.s.i.DbLockServiceImpl -- Acquired lock id = 5 for lockType = BPMN, and lockVersion = v3.
33:01.579 INFO  [main] o.o.b.s.s.i.RestBpmnSyncServiceImpl -- Will synchronize 2 BPMN/DMN files, for TestDeployment, bpmnVersion: v3
33:01.580 TRACE [main] o.o.c.b.s.SyncBearerTokenServiceImpl -- Returning cached bearer token (sync) for scope: , username: null
33:01.616 INFO  [main] o.o.b.s.s.i.RestBpmnSyncServiceImpl -- Deployment for TestDeployment, version v3 has been completed. Deployed artifacts - BPMN: 2, DMN: 0, DRD: 0.
33:01.616 DEBUG [main] o.o.b.s.s.i.RestBpmnSyncServiceImpl -- Deployed BPMN files and their versions follow:
33:01.616 DEBUG [main] o.o.b.s.s.i.RestBpmnSyncServiceImpl -- Version: 1, BPMN: DummyBpmn.bpmn
33:01.616 DEBUG [main] o.o.b.s.s.i.RestBpmnSyncServiceImpl -- Version: 3, BPMN: SampleBpmn.bpmn
33:01.616 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: select count(*) from DB_LOCK_LATEST where lock_type = ('B')
33:01.617 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: update DB_LOCK_LATEST T set lock_version = L.lock_version, hostname = L.hostname, lock_acquired_on = L.created_on from DB_LOCK L where L.id = ('5'::int4) and T.lock_type = L.lock_type
33:01.618 TRACE [main] o.o.d.l.s.i.DbLockServiceImpl -- Updated LatestLock using lockId = 5
33:01.618 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK_HISTORY (lock_id, lock_type, lock_version, hostname, lock_acquired_on) select id, lock_type, lock_version, hostname, created_on from DB_LOCK where DB_LOCK.id = ('5'::int4)
33:01.619 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: delete from DB_LOCK where id = ('5'::int4) and lock_type = ('B')
33:01.619 INFO  [main] o.o.d.l.s.i.DbLockServiceImpl -- Released lock after 0 seconds. lockId = 5, and lockType = BPMN
33:01.620 TRACE [main] o.o.d.l.s.i.DbLockServiceImpl -- Cancelling auto lock release timer for lock id = 5, type = BPMN
33:01.620 INFO  [main] o.o.b.s.s.i.RestBpmnMigrationServiceImpl -- Starting BPMN migration for 2 deployed BPMNs.
33:01.620 DEBUG [main] o.o.b.s.s.i.RestBpmnMigrationServiceImpl -- Skipping migration of Dummy_BPMN from version 0 to 1, because no previous process definition exists.
33:01.621 TRACE [main] o.o.c.b.s.SyncBearerTokenServiceImpl -- Returning cached bearer token (sync) for scope: , username: null
33:01.635 DEBUG [main] o.o.b.s.s.i.RestBpmnMigrationServiceImpl -- Previous version's process definition id: Sample_BPMN:2:336e80f7-689d-11f1-8aae-5a250421cbe0
33:01.635 TRACE [main] o.o.b.s.s.i.RestBpmnMigrationServiceImpl -- Getting process instance count for process definition id: Sample_BPMN:2:336e80f7-689d-11f1-8aae-5a250421cbe0
33:01.635 TRACE [main] o.o.c.b.s.SyncBearerTokenServiceImpl -- Returning cached bearer token (sync) for scope: , username: null
33:01.643 TRACE [main] o.o.b.s.s.i.RestBpmnMigrationServiceImpl -- There are 1 process instances for Sample_BPMN version 2
33:01.643 TRACE [main] o.o.b.s.s.i.RestBpmnMigrationServiceImpl -- Generating migration plan for migrating Sample_BPMN process instances from version 2 to 3
33:01.643 TRACE [main] o.o.c.b.s.SyncBearerTokenServiceImpl -- Returning cached bearer token (sync) for scope: , username: null
33:01.676 DEBUG [main] o.o.b.s.s.i.RestBpmnMigrationServiceImpl -- Executing migration async for migrating Sample_BPMN process instances from version 2 to 3
33:01.676 TRACE [main] o.o.c.b.s.SyncBearerTokenServiceImpl -- Returning cached bearer token (sync) for scope: , username: null
33:01.714 DEBUG [main] o.o.b.s.s.i.RestBpmnMigrationServiceImpl -- Migration Async Execution initiated for Sample_BPMN version 2 to 3. Details: ExecuteMigrationPlanAsyncResponse(id=34039514-689d-11f1-8aae-5a250421cbe0, type=instance-migration, totalJobs=1, jobsCreated=0, batchJobsPerSeed=100, invocationsPerBatchJob=1, seedJobDefinitionId=3403e335-689d-11f1-8aae-5a250421cbe0, monitorJobDefinitionId=3403e336-689d-11f1-8aae-5a250421cbe0, batchJobDefinitionId=3403e337-689d-11f1-8aae-5a250421cbe0, suspended=false, tenantId=null, createUserId=null, startTime=2026-06-15T09:33:01.695+0000, executionStartTime=null)
33:01.719 INFO  [main] o.o.b.s.s.i.RestBpmnMigrationServiceImpl -- BPMN Migration completed, total async jobs created: 1. Use Camunda Cockpit for checking job completions.
```

## Changelog
See [CHANGELOG.md](CHANGELOG.md) for the full version history.
