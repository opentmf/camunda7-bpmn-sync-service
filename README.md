# BPMN Synchronization Service
This service synchronizes the BPMN files under classpath:bpmn/ folder with the configured Camunda server.

Depending on the value of auto-migrate, migrates the deployed BPMN's previous version's process instances to the newly deployed version.

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
Import pia-commons-library dependencies
```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.pia.commons</groupId>
      <artifactId>pia-commons-versions</artifactId>
      <version>LATEST</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```
Depend on the latest version of pia-bpmn-sync-service: 
```xml
  <dependency>
    <groupId>com.pia.commons</groupId>
    <artifactId>pia-bpmn-sync-service</artifactId>
  </dependency>
```
### 2. Reorganize the BPMN files
1. In your microservice, the BPMN files must be under **src/main/resources/bpmn** folder. All *.bpmn files within this folder and its sub-folders will be used in the synchronization process.
    * Note: If your microservice uses embedded Camunda for its IT tests, you can benefit from this camunda configuration property: [camunda.deployment-resource-pattern](https://docs.camunda.org/manual/7.19/user-guide/spring-boot-integration/configuration/)
2. Ensure one BPMN is deployed by one microservice. Do not try to deploy the same BPMN in a different microservice.

### 3. Specify the BPMN Sync Properties
In your application.yaml, specify the BPMN Sync Properties:
```yaml
pia:
  bpmn-sync:
    enabled: true
    deployment-name: UC-SOA
    bpmn-version: 1.0
    auto-migrate: true
    client: default
```

**client:** The client id to use. This id is the prefix to the following exposed beans:

1. webClient
2. tokenService
3. clientProperties

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
pia:
  db-lock:
    create-tables: false
  bpmn-sync:
    enabled: false
```
## Sample Logs
Here are some sample log statements from a microservice's startup logs:

### Initial Deployment, Migration not necessary
```text
08:06.373 INFO  [main] c.p.b.s.s.i.BpmnSyncServiceImpl -- Starting BPMN Deployment for TestDeployment, version: v1
08:06.378 DEBUG [main] c.p.d.l.s.i.DbLockServiceImpl -- Attempting to acquire lock for lockType = BPMN, and lockVersion = v1
08:06.391 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK(lock_type, lock_version, hostname) values (('B'), ('v1'), ('gentoo.lan'))
RETURNING *
08:06.394 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: select lock_version, lock_acquired_on from DB_LOCK_LATEST where lock_type = ('B')
08:06.399 DEBUG [main] c.p.d.l.s.i.DbLockServiceImpl -- Acquired lock id = 1 for lockType = BPMN, and lockVersion = v1.
08:06.399 INFO  [main] c.p.b.s.s.i.BpmnSyncServiceImpl -- Will synchronize 1 BPMN files, for TestDeployment, bpmnVersion: v1
08:06.412 DEBUG [main] c.p.c.o.s.i.OpenidTokenClientImpl -- Will retrieve a new openid token from url: http://localhost:33095/oauth2/token, scope: [openid], username: [user]
08:08.653 INFO  [reactor-http-epoll-2] c.p.b.s.s.i.BpmnSyncServiceImpl -- BPMN deployment for TestDeployment, version v1 has been completed. Deployed BPMN count: 1
08:08.654 DEBUG [reactor-http-epoll-2] c.p.b.s.s.i.BpmnSyncServiceImpl -- Deployed BPMN Files and Their Versions follows:
08:08.654 DEBUG [reactor-http-epoll-2] c.p.b.s.s.i.BpmnSyncServiceImpl -- Version: 1, BPMN: SampleBpmn.bpmn
08:08.655 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: select count(*) from DB_LOCK_LATEST where lock_type = ('B')
08:08.657 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK_LATEST (lock_type, lock_version, hostname, lock_acquired_on) select lock_type, lock_version, hostname, created_on from DB_LOCK where id = ('1'::int4)
08:08.659 TRACE [main] c.p.d.l.s.i.DbLockServiceImpl -- Initialized LatestLock using lockId = 1
08:08.660 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK_HISTORY (lock_id, lock_type, lock_version, hostname, lock_acquired_on) select id, lock_type, lock_version, hostname, created_on from DB_LOCK where DB_LOCK.id = ('1'::int4)
08:08.663 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: delete from DB_LOCK where id = ('1'::int4) and lock_type = ('B')
08:08.665 INFO  [main] c.p.d.l.s.i.DbLockServiceImpl -- Released lock after 2 seconds. lockId = 1, and lockType = BPMN
08:08.667 TRACE [main] c.p.d.l.s.i.DbLockServiceImpl -- Cancelling auto lock release timer for lock id = 1, type = BPMN
08:08.667 INFO  [main] c.p.b.s.s.i.BpmnMigrationServiceImpl -- Skipping BPMN migration because auto-migrate is set to false.
```
### When all files are up-to-date
```text
08:08.669 INFO  [main] c.p.b.s.s.i.BpmnSyncServiceImpl -- Starting BPMN Deployment for TestDeployment, version: v1
08:08.670 DEBUG [main] c.p.d.l.s.i.DbLockServiceImpl -- Attempting to acquire lock for lockType = BPMN, and lockVersion = v1
08:08.671 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK(lock_type, lock_version, hostname) values (('B'), ('v1'), ('gentoo.lan'))
RETURNING *
08:08.673 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: select lock_version, lock_acquired_on from DB_LOCK_LATEST where lock_type = ('B')
08:08.677 DEBUG [main] c.p.d.l.s.i.DbLockServiceImpl -- Acquired lock id = 2 for lockType = BPMN, and lockVersion = v1.
08:08.678 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK_HISTORY (lock_id, lock_type, lock_version, hostname, lock_acquired_on) select id, lock_type, lock_version, hostname, created_on from DB_LOCK where DB_LOCK.id = ('2'::int4)
08:08.680 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: delete from DB_LOCK where id = ('2'::int4) and lock_type = ('B')
08:08.682 INFO  [main] c.p.d.l.s.i.DbLockServiceImpl -- Released lock after 0 seconds. lockId = 2, and lockType = BPMN
08:08.683 TRACE [main] c.p.d.l.s.i.DbLockServiceImpl -- Cancelling auto lock release timer for lock id = 2, type = BPMN
08:08.684 INFO  [main] c.p.b.s.s.i.BpmnSyncServiceImpl -- TestDeployment BPMN files are already up-to-date for version v1.
08:08.684 INFO  [main] c.p.b.s.s.i.BpmnMigrationServiceImpl -- Skipping BPMN migration because auto-migrate is set to false.
```
### When all files are up-to-date but version is increased
```text
08:08.685 INFO  [main] c.p.b.s.s.i.BpmnSyncServiceImpl -- Starting BPMN Deployment for TestDeployment, version: v1.1
08:08.686 DEBUG [main] c.p.d.l.s.i.DbLockServiceImpl -- Attempting to acquire lock for lockType = BPMN, and lockVersion = v1.1
08:08.687 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK(lock_type, lock_version, hostname) values (('B'), ('v1.1'), ('gentoo.lan'))
RETURNING *
08:08.689 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: select lock_version, lock_acquired_on from DB_LOCK_LATEST where lock_type = ('B')
08:08.692 DEBUG [main] c.p.d.l.s.i.DbLockServiceImpl -- Acquired lock id = 3 for lockType = BPMN, and lockVersion = v1.1.
08:08.693 INFO  [main] c.p.b.s.s.i.BpmnSyncServiceImpl -- Will synchronize 1 BPMN files, for TestDeployment, bpmnVersion: v1.1
08:08.700 TRACE [main] c.p.c.o.s.i.OpenidTokenServiceImpl -- Returning cached openid token for baseUrl: http://localhost:33095/oauth2/token, scope: openid, username: user
08:08.755 WARN  [reactor-http-epoll-2] c.p.b.s.s.i.BpmnSyncServiceImpl -- TestDeployment BPMN synchronization completed without deploying any BPMN. The specified bpmnVersion was: v1.1. Hint: Do not change the bpmnVersion when there are no BPMN changes.
08:08.755 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK_HISTORY (lock_id, lock_type, lock_version, hostname, lock_acquired_on) select id, lock_type, lock_version, hostname, created_on from DB_LOCK where DB_LOCK.id = ('3'::int4)
08:08.757 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: delete from DB_LOCK where id = ('3'::int4) and lock_type = ('B')
08:08.759 INFO  [main] c.p.d.l.s.i.DbLockServiceImpl -- Released lock after 0 seconds. lockId = 3, and lockType = BPMN
08:08.760 TRACE [main] c.p.d.l.s.i.DbLockServiceImpl -- Cancelling auto lock release timer for lock id = 3, type = BPMN
08:08.761 INFO  [main] c.p.b.s.s.i.BpmnMigrationServiceImpl -- Skipping BPMN migration because auto-migrate is set to false.
```
### When a single BPMN File Is Changed, auto-migrate: true, no previous version process instances exist
```text
08:08.762 INFO  [main] c.p.b.s.s.i.BpmnSyncServiceImpl -- Starting BPMN Deployment for TestDeployment, version: v2
08:08.763 DEBUG [main] c.p.d.l.s.i.DbLockServiceImpl -- Attempting to acquire lock for lockType = BPMN, and lockVersion = v2
08:08.764 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK(lock_type, lock_version, hostname) values (('B'), ('v2'), ('gentoo.lan'))
RETURNING *
08:08.765 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: select lock_version, lock_acquired_on from DB_LOCK_LATEST where lock_type = ('B')
08:08.768 DEBUG [main] c.p.d.l.s.i.DbLockServiceImpl -- Acquired lock id = 4 for lockType = BPMN, and lockVersion = v2.
08:08.768 INFO  [main] c.p.b.s.s.i.BpmnSyncServiceImpl -- Will synchronize 1 BPMN files, for TestDeployment, bpmnVersion: v2
08:08.769 TRACE [main] c.p.c.o.s.i.OpenidTokenServiceImpl -- Returning cached openid token for baseUrl: http://localhost:33095/oauth2/token, scope: openid, username: user
08:08.855 INFO  [reactor-http-epoll-2] c.p.b.s.s.i.BpmnSyncServiceImpl -- BPMN deployment for TestDeployment, version v2 has been completed. Deployed BPMN count: 1
08:08.856 DEBUG [reactor-http-epoll-2] c.p.b.s.s.i.BpmnSyncServiceImpl -- Deployed BPMN Files and Their Versions follows:
08:08.857 DEBUG [reactor-http-epoll-2] c.p.b.s.s.i.BpmnSyncServiceImpl -- Version: 2, BPMN: SampleBpmn.bpmn
08:08.857 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: select count(*) from DB_LOCK_LATEST where lock_type = ('B')
08:08.858 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: update DB_LOCK_LATEST T set lock_version = L.lock_version, hostname = L.hostname, lock_acquired_on = L.created_on from DB_LOCK L where L.id = ('4'::int4) and T.lock_type = L.lock_type
08:08.860 TRACE [main] c.p.d.l.s.i.DbLockServiceImpl -- Updated LatestLock using lockId = 4
08:08.860 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK_HISTORY (lock_id, lock_type, lock_version, hostname, lock_acquired_on) select id, lock_type, lock_version, hostname, created_on from DB_LOCK where DB_LOCK.id = ('4'::int4)
08:08.862 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: delete from DB_LOCK where id = ('4'::int4) and lock_type = ('B')
08:08.864 INFO  [main] c.p.d.l.s.i.DbLockServiceImpl -- Released lock after 0 seconds. lockId = 4, and lockType = BPMN
08:08.865 TRACE [main] c.p.d.l.s.i.DbLockServiceImpl -- Cancelling auto lock release timer for lock id = 4, type = BPMN
08:08.866 INFO  [main] c.p.b.s.s.i.BpmnMigrationServiceImpl -- Starting BPMN migration for 1 deployed BPMNs.
08:08.871 TRACE [main] c.p.c.o.s.i.OpenidTokenServiceImpl -- Returning cached openid token for baseUrl: http://localhost:33095/oauth2/token, scope: openid, username: user
08:09.222 DEBUG [reactor-http-epoll-2] c.p.b.s.s.i.BpmnMigrationServiceImpl -- Previous version's process definition id: Sample_BPMN:1:7f16265c-79a4-11ef-a96e-0a0027000000
08:09.222 TRACE [reactor-http-epoll-2] c.p.b.s.s.i.BpmnMigrationServiceImpl -- Getting process instance count for process definition id: Sample_BPMN:1:7f16265c-79a4-11ef-a96e-0a0027000000
08:09.223 TRACE [reactor-http-epoll-2] c.p.c.o.s.i.OpenidTokenServiceImpl -- Returning cached openid token for baseUrl: http://localhost:33095/oauth2/token, scope: openid, username: user
08:09.289 DEBUG [reactor-http-epoll-2] c.p.b.s.s.i.BpmnMigrationServiceImpl -- Migration not necessary for Sample_BPMN version 1 to 2 because no process instances exist.
08:09.290 INFO  [main] c.p.b.s.s.i.BpmnMigrationServiceImpl -- BPMN Migration completed without creating any async migration jobs.
```

### When a single BPMN File Is Changed, auto-migrate: true, migration jobs are created
```text
08:09.491 INFO  [main] c.p.b.s.s.i.BpmnSyncServiceImpl -- Starting BPMN Deployment for TestDeployment, version: v3
08:09.492 DEBUG [main] c.p.d.l.s.i.DbLockServiceImpl -- Attempting to acquire lock for lockType = BPMN, and lockVersion = v3
08:09.492 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK(lock_type, lock_version, hostname) values (('B'), ('v3'), ('gentoo.lan'))
RETURNING *
08:09.492 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: select lock_version, lock_acquired_on from DB_LOCK_LATEST where lock_type = ('B')
08:09.493 DEBUG [main] c.p.d.l.s.i.DbLockServiceImpl -- Acquired lock id = 5 for lockType = BPMN, and lockVersion = v3.
08:09.493 INFO  [main] c.p.b.s.s.i.BpmnSyncServiceImpl -- Will synchronize 1 BPMN files, for TestDeployment, bpmnVersion: v3
08:09.494 TRACE [main] c.p.c.o.s.i.OpenidTokenServiceImpl -- Returning cached openid token for baseUrl: http://localhost:33095/oauth2/token, scope: openid, username: user
08:09.532 INFO  [reactor-http-epoll-2] c.p.b.s.s.i.BpmnSyncServiceImpl -- BPMN deployment for TestDeployment, version v3 has been completed. Deployed BPMN count: 1
08:09.532 DEBUG [reactor-http-epoll-2] c.p.b.s.s.i.BpmnSyncServiceImpl -- Deployed BPMN Files and Their Versions follows:
08:09.532 DEBUG [reactor-http-epoll-2] c.p.b.s.s.i.BpmnSyncServiceImpl -- Version: 3, BPMN: SampleBpmn.bpmn
08:09.533 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: select count(*) from DB_LOCK_LATEST where lock_type = ('B')
08:09.533 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: update DB_LOCK_LATEST T set lock_version = L.lock_version, hostname = L.hostname, lock_acquired_on = L.created_on from DB_LOCK L where L.id = ('5'::int4) and T.lock_type = L.lock_type
08:09.534 TRACE [main] c.p.d.l.s.i.DbLockServiceImpl -- Updated LatestLock using lockId = 5
08:09.534 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK_HISTORY (lock_id, lock_type, lock_version, hostname, lock_acquired_on) select id, lock_type, lock_version, hostname, created_on from DB_LOCK where DB_LOCK.id = ('5'::int4)
08:09.535 TRACE [main] c.p.d.l.u.JdbcHelper -- Executing SQL: delete from DB_LOCK where id = ('5'::int4) and lock_type = ('B')
08:09.536 INFO  [main] c.p.d.l.s.i.DbLockServiceImpl -- Released lock after 0 seconds. lockId = 5, and lockType = BPMN
08:09.536 TRACE [main] c.p.d.l.s.i.DbLockServiceImpl -- Cancelling auto lock release timer for lock id = 5, type = BPMN
08:09.536 INFO  [main] c.p.b.s.s.i.BpmnMigrationServiceImpl -- Starting BPMN migration for 1 deployed BPMNs.
08:09.537 TRACE [main] c.p.c.o.s.i.OpenidTokenServiceImpl -- Returning cached openid token for baseUrl: http://localhost:33095/oauth2/token, scope: openid, username: user
08:09.569 DEBUG [reactor-http-epoll-2] c.p.b.s.s.i.BpmnMigrationServiceImpl -- Previous version's process definition id: Sample_BPMN:2:7f40b8cf-79a4-11ef-a96e-0a0027000000
08:09.569 TRACE [reactor-http-epoll-2] c.p.b.s.s.i.BpmnMigrationServiceImpl -- Getting process instance count for process definition id: Sample_BPMN:2:7f40b8cf-79a4-11ef-a96e-0a0027000000
08:09.570 TRACE [reactor-http-epoll-2] c.p.c.o.s.i.OpenidTokenServiceImpl -- Returning cached openid token for baseUrl: http://localhost:33095/oauth2/token, scope: openid, username: user
08:09.583 TRACE [reactor-http-epoll-2] c.p.b.s.s.i.BpmnMigrationServiceImpl -- There are 1 process instances for Sample_BPMN version 2
08:09.583 TRACE [reactor-http-epoll-2] c.p.b.s.s.i.BpmnMigrationServiceImpl -- Generating migration plan for migrating Sample_BPMN process instances from version 2 to 3
08:09.585 TRACE [reactor-http-epoll-2] c.p.c.o.s.i.OpenidTokenServiceImpl -- Returning cached openid token for baseUrl: http://localhost:33095/oauth2/token, scope: openid, username: user
08:09.634 DEBUG [reactor-http-epoll-2] c.p.b.s.s.i.BpmnMigrationServiceImpl -- Executing migration async for migrating Sample_BPMN process instances from version 2 to 3
08:09.634 TRACE [reactor-http-epoll-2] c.p.c.o.s.i.OpenidTokenServiceImpl -- Returning cached openid token for baseUrl: http://localhost:33095/oauth2/token, scope: openid, username: user
08:09.768 DEBUG [reactor-http-epoll-2] c.p.b.s.s.i.BpmnMigrationServiceImpl -- Migration Async Execution initiated for Sample_BPMN version 2 to 3. Details: ExecuteMigrationPlanAsyncResponse(id=7fc8385b-79a4-11ef-a96e-0a0027000000, type=instance-migration, totalJobs=1, jobsCreated=0, batchJobsPerSeed=100, invocationsPerBatchJob=1, seedJobDefinitionId=7fc922bc-79a4-11ef-a96e-0a0027000000, monitorJobDefinitionId=7fc922bd-79a4-11ef-a96e-0a0027000000, batchJobDefinitionId=7fc922be-79a4-11ef-a96e-0a0027000000, suspended=false, tenantId=null, createUserId=null, startTime=2024-09-23T15:08:09.676+0300, executionStartTime=null)
08:09.777 INFO  [main] c.p.b.s.s.i.BpmnMigrationServiceImpl -- BPMN Migration completed, total async jobs created: 1. Use Camunda Cockpit for checking job completions.
08:09.784 DEBUG [HikariPool-1 connection adder] o.t.c.JdbcDatabaseContainer -- Trying to create JDBC connection using org.postgresql.Driver to jdbc:postgresql://localhost:9219/test?loggerLevel=OFF with properties: {password=test, user=test}
08:09.881 TRACE [awaitility-thread] c.p.c.o.s.i.OpenidTokenServiceImpl -- Returning cached openid token for baseUrl: http://localhost:33095/oauth2/token, scope: openid, username: user
08:09.997 TRACE [awaitility-thread] c.p.c.o.s.i.OpenidTokenServiceImpl -- Returning cached openid token for baseUrl: http://localhost:33095/oauth2/token, scope: openid, username: user
```

## Version History
### 1.0.0
- Initial Release
### 1.0.1
- Simplifies configuration properties
### 1.0.2
- Updates dependency versions of pia-web-clients and pia-db-lock-service to their latest.
