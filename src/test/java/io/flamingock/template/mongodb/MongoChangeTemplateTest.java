/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.flamingock.template.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.store.mongodb.sync.MongoDBSyncAuditStore;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.targetsystem.mongodb.sync.MongoDBSyncTargetSystem;
import io.flamingock.template.mongodb.exception.MongoStepExecutionException;
import io.flamingock.template.mongodb.model.MongoOperation;
import org.bson.Document;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;

import static io.flamingock.internal.util.constants.CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableFlamingock(configFile = "flamingock/pipeline.yaml")
@Testcontainers
class MongoChangeTemplateTest {

    private static final String DB_NAME = "test";


    private static MongoClient mongoClient;

    private static MongoDatabase mongoDatabase;


    @Container
    public static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:6"));

    @BeforeAll
    static void beforeAll() {
        mongoClient = MongoClients.create(MongoClientSettings
                .builder()
                .applyConnectionString(new ConnectionString(mongoDBContainer.getConnectionString()))
                .build());
        mongoDatabase = mongoClient.getDatabase(DB_NAME);
    }

    @BeforeEach
    void setupEach() {
        mongoDatabase.getCollection(DEFAULT_AUDIT_STORE_NAME).drop();
        mongoDatabase.getCollection("users").drop();
        mongoDatabase.getCollection("products").drop();
        mongoDatabase.getCollection("rollbackTestCollection").drop();
        mongoDatabase.getCollection("stepTestCollection").drop();
        mongoDatabase.getCollection("stepRollbackTest").drop();
    }


    @Test
    @DisplayName("WHEN mongodb template THEN runs fine IF Flamingock mongodb sync ce")
    void happyPath() {

        MongoDBSyncTargetSystem mongoDBSyncTargetSystem = new MongoDBSyncTargetSystem("mongodb", mongoClient, DB_NAME);
        FlamingockFactory.getCommunityBuilder()
                .setAuditStore(MongoDBSyncAuditStore.from(mongoDBSyncTargetSystem))
                .addTargetSystem(mongoDBSyncTargetSystem)
                .build()
                .run();


        List<Document> auditLog = mongoDatabase.getCollection(DEFAULT_AUDIT_STORE_NAME)
                .find()
                .into(new ArrayList<>());

        assertEquals(10, auditLog.size());

        assertEquals("create-users-collection-with-index", auditLog.get(0).getString("changeId"));
        assertEquals(AuditEntry.Status.STARTED.name(), auditLog.get(0).getString("state"));
        assertEquals("create-users-collection-with-index", auditLog.get(1).getString("changeId"));
        assertEquals(AuditEntry.Status.APPLIED.name(), auditLog.get(1).getString("state"));

        assertEquals("seed-users", auditLog.get(2).getString("changeId"));
        assertEquals(AuditEntry.Status.STARTED.name(), auditLog.get(2).getString("state"));
        assertEquals("seed-users", auditLog.get(3).getString("changeId"));
        assertEquals(AuditEntry.Status.APPLIED.name(), auditLog.get(3).getString("state"));

        assertEquals("multiple-operations-change", auditLog.get(4).getString("changeId"));
        assertEquals(AuditEntry.Status.STARTED.name(), auditLog.get(4).getString("state"));
        assertEquals("multiple-operations-change", auditLog.get(5).getString("changeId"));
        assertEquals(AuditEntry.Status.APPLIED.name(), auditLog.get(5).getString("state"));

        assertEquals("apply-and-rollback-test", auditLog.get(6).getString("changeId"));
        assertEquals(AuditEntry.Status.STARTED.name(), auditLog.get(6).getString("state"));
        assertEquals("apply-and-rollback-test", auditLog.get(7).getString("changeId"));
        assertEquals(AuditEntry.Status.APPLIED.name(), auditLog.get(7).getString("state"));

        assertEquals("step-based-change", auditLog.get(8).getString("changeId"));
        assertEquals(AuditEntry.Status.STARTED.name(), auditLog.get(8).getString("state"));
        assertEquals("step-based-change", auditLog.get(9).getString("changeId"));
        assertEquals(AuditEntry.Status.APPLIED.name(), auditLog.get(9).getString("state"));

        // Verify for single operation
        List<Document> users = mongoDatabase.getCollection("users")
                .find()
                .into(new ArrayList<>());

        assertEquals(2, users.size());
        assertEquals("Admin", users.get(0).getString("name"));
        assertEquals("admin@company.com", users.get(0).getString("email"));
        assertEquals("superuser", users.get(0).getList("roles", String.class).get(0));

        assertEquals("Backup", users.get(1).getString("name"));
        assertEquals("backup@company.com", users.get(1).getString("email"));
        assertEquals("readonly", users.get(1).getList("roles", String.class).get(0));

        // Verify for multiple operation
        List<Document> products = mongoDatabase.getCollection("products")
                .find()
                .into(new ArrayList<>());

        assertEquals(3, products.size(), "Should have 3 products from multiple operations");
        assertEquals("Laptop", products.get(0).getString("name"));
        assertEquals("Keyboard", products.get(1).getString("name"));
        assertEquals("Mouse", products.get(2).getString("name"));

        List<Document> indexes = mongoDatabase.getCollection("products")
                .listIndexes()
                .into(new ArrayList<>());
        boolean categoryIndexExists = indexes.stream()
                .anyMatch(idx -> "category_index".equals(idx.getString("name")));
        assertTrue(categoryIndexExists, "Category index should exist on products collection");

        List<Document> rollbackItems = mongoDatabase.getCollection("rollbackTestCollection")
                .find()
                .into(new ArrayList<>());
        assertEquals(2, rollbackItems.size(), "Should have 2 items in rollbackTestCollection");
        assertEquals("Item1", rollbackItems.get(0).getString("name"));
        assertEquals("Item2", rollbackItems.get(1).getString("name"));

        List<Document> rollbackIndexes = mongoDatabase.getCollection("rollbackTestCollection")
                .listIndexes()
                .into(new ArrayList<>());
        boolean nameIndexExists = rollbackIndexes.stream()
                .anyMatch(idx -> "name_index".equals(idx.getString("name")));
        assertTrue(nameIndexExists, "Name index should exist on rollbackTestCollection");

        // Verify step-based change
        List<Document> stepItems = mongoDatabase.getCollection("stepTestCollection")
                .find()
                .into(new ArrayList<>());
        assertEquals(2, stepItems.size(), "Should have 2 items in stepTestCollection");
        assertEquals("StepItem1", stepItems.get(0).getString("name"));
        assertEquals("StepItem2", stepItems.get(1).getString("name"));

        List<Document> stepIndexes = mongoDatabase.getCollection("stepTestCollection")
                .listIndexes()
                .into(new ArrayList<>());
        boolean stepNameIndexExists = stepIndexes.stream()
                .anyMatch(idx -> "step_name_index".equals(idx.getString("name")));
        assertTrue(stepNameIndexExists, "Name index should exist on stepTestCollection");
    }

    @Test
    @DisplayName("WHEN rollback is invoked THEN multiple rollback operations execute in defined order")
    void rollbackWithMultipleOperations() {
        // First, set up the state by creating the collection and inserting data
        mongoDatabase.createCollection("rollbackTestCollection");
        mongoDatabase.getCollection("rollbackTestCollection").insertMany(Arrays.asList(
                new Document("name", "Item1").append("value", 100),
                new Document("name", "Item2").append("value", 200)
        ));
        mongoDatabase.getCollection("rollbackTestCollection").createIndex(
                new Document("name", 1),
                new com.mongodb.client.model.IndexOptions().name("name_index").unique(true)
        );

        assertTrue(collectionExists("rollbackTestCollection"), "Collection should exist before rollback");
        assertEquals(2, mongoDatabase.getCollection("rollbackTestCollection").countDocuments(),
                "Should have 2 documents before rollback");

        MongoChangeTemplate template = new MongoChangeTemplate();
        template.setChangeId("apply-and-rollback-test");
        template.setTransactional(false);

        // Set rollback payload - should execute in order:
        // 1. Drop index
        // 2. Delete all documents
        // 3. Drop collection
        List<MongoOperation> rollbackOps = new ArrayList<>();

        MongoOperation dropIndexOp = new MongoOperation();
        dropIndexOp.setType("dropIndex");
        dropIndexOp.setCollection("rollbackTestCollection");
        HashMap<String, Object> dropIndexParams = new HashMap<>();
        dropIndexParams.put("indexName", "name_index");
        dropIndexOp.setParameters(dropIndexParams);
        rollbackOps.add(dropIndexOp);

        MongoOperation deleteOp = new MongoOperation();
        deleteOp.setType("delete");
        deleteOp.setCollection("rollbackTestCollection");
        HashMap<String, Object> deleteParams = new HashMap<>();
        deleteParams.put("filter", new HashMap<>());
        deleteOp.setParameters(deleteParams);
        rollbackOps.add(deleteOp);

        MongoOperation dropCollectionOp = new MongoOperation();
        dropCollectionOp.setType("dropCollection");
        dropCollectionOp.setCollection("rollbackTestCollection");
        rollbackOps.add(dropCollectionOp);

        template.setRollbackPayload(rollbackOps);

        template.rollback(mongoDatabase, null);

        assertFalse(collectionExists("rollbackTestCollection"),
                "Collection should not exist after rollback");
    }

    private boolean collectionExists(String collectionName) {
        return mongoDatabase.listCollectionNames()
                .into(new ArrayList<>())
                .contains(collectionName);
    }

    @Test
    @DisplayName("WHEN step-based apply succeeds THEN all steps are executed")
    void stepBasedApplySuccess() throws Exception {
        MongoChangeTemplate template = new MongoChangeTemplate();
        template.setChangeId("step-test");
        template.setTransactional(false);

        // Create step payload programmatically
        List<Map<String, Object>> steps = new ArrayList<>();

        // Step 1: Create collection
        Map<String, Object> step1 = new HashMap<>();
        Map<String, Object> step1Apply = new HashMap<>();
        step1Apply.put("type", "createCollection");
        step1Apply.put("collection", "stepRollbackTest");
        Map<String, Object> step1Rollback = new HashMap<>();
        step1Rollback.put("type", "dropCollection");
        step1Rollback.put("collection", "stepRollbackTest");
        step1.put("apply", step1Apply);
        step1.put("rollback", step1Rollback);
        steps.add(step1);

        // Step 2: Insert documents
        Map<String, Object> step2 = new HashMap<>();
        Map<String, Object> step2Apply = new HashMap<>();
        step2Apply.put("type", "insert");
        step2Apply.put("collection", "stepRollbackTest");
        Map<String, Object> step2Params = new HashMap<>();
        List<Map<String, Object>> docs = new ArrayList<>();
        Map<String, Object> doc1 = new HashMap<>();
        doc1.put("name", "Test1");
        doc1.put("value", 100);
        docs.add(doc1);
        step2Params.put("documents", docs);
        step2Apply.put("parameters", step2Params);
        Map<String, Object> step2Rollback = new HashMap<>();
        step2Rollback.put("type", "delete");
        step2Rollback.put("collection", "stepRollbackTest");
        Map<String, Object> step2RollbackParams = new HashMap<>();
        step2RollbackParams.put("filter", new HashMap<>());
        step2Rollback.put("parameters", step2RollbackParams);
        step2.put("apply", step2Apply);
        step2.put("rollback", step2Rollback);
        steps.add(step2);

        setRawApplyPayload(template, steps);

        template.apply(mongoDatabase, null);

        assertTrue(collectionExists("stepRollbackTest"), "Collection should exist after apply");
        assertEquals(1, mongoDatabase.getCollection("stepRollbackTest").countDocuments(),
                "Should have 1 document after apply");
    }

    @Test
    @DisplayName("WHEN step 2 fails THEN step 1 is rolled back")
    void stepBasedRollbackOnFailure() throws Exception {
        MongoChangeTemplate template = new MongoChangeTemplate();
        template.setChangeId("step-rollback-test");
        template.setTransactional(false);

        // Create step payload that will fail on step 2
        List<Map<String, Object>> steps = new ArrayList<>();

        // Step 1: Create collection (will succeed)
        Map<String, Object> step1 = new HashMap<>();
        Map<String, Object> step1Apply = new HashMap<>();
        step1Apply.put("type", "createCollection");
        step1Apply.put("collection", "stepRollbackTest");
        Map<String, Object> step1Rollback = new HashMap<>();
        step1Rollback.put("type", "dropCollection");
        step1Rollback.put("collection", "stepRollbackTest");
        step1.put("apply", step1Apply);
        step1.put("rollback", step1Rollback);
        steps.add(step1);

        // Step 2: Try to create a collection that already exists (will fail)
        // First create the collection to cause conflict
        mongoDatabase.createCollection("conflictCollection");

        Map<String, Object> step2 = new HashMap<>();
        Map<String, Object> step2Apply = new HashMap<>();
        step2Apply.put("type", "createCollection");
        step2Apply.put("collection", "conflictCollection"); // Already exists, will fail
        step2.put("apply", step2Apply);
        steps.add(step2);

        setRawApplyPayload(template, steps);

        MongoStepExecutionException exception = assertThrows(MongoStepExecutionException.class,
                () -> template.apply(mongoDatabase, null));

        assertEquals(2, exception.getStepNumber(), "Should fail at step 2");
        assertEquals(1, exception.getCompletedStepCount(), "Should have 1 completed step before failure");

        // Collection should be dropped due to rollback of step 1
        assertFalse(collectionExists("stepRollbackTest"),
                "Collection should not exist after rollback");

        // Clean up
        mongoDatabase.getCollection("conflictCollection").drop();
    }

    @Test
    @DisplayName("WHEN step has no rollback THEN it is skipped during rollback")
    void stepWithNoRollbackIsSkipped() throws Exception {
        // First create the collection and insert data
        mongoDatabase.createCollection("stepRollbackTest");
        mongoDatabase.getCollection("stepRollbackTest").insertOne(new Document("name", "Existing"));

        MongoChangeTemplate template = new MongoChangeTemplate();
        template.setChangeId("step-no-rollback-test");
        template.setTransactional(false);

        // Create step payload where step 1 has no rollback
        List<Map<String, Object>> steps = new ArrayList<>();

        // Step 1: Insert (no rollback defined)
        Map<String, Object> step1 = new HashMap<>();
        Map<String, Object> step1Apply = new HashMap<>();
        step1Apply.put("type", "insert");
        step1Apply.put("collection", "stepRollbackTest");
        Map<String, Object> step1Params = new HashMap<>();
        List<Map<String, Object>> docs = new ArrayList<>();
        Map<String, Object> doc1 = new HashMap<>();
        doc1.put("name", "NoRollback");
        docs.add(doc1);
        step1Params.put("documents", docs);
        step1Apply.put("parameters", step1Params);
        step1.put("apply", step1Apply);
        // Note: no rollback defined for step 1
        steps.add(step1);

        // Step 2: Create collection that already exists (will fail)
        mongoDatabase.createCollection("conflictCollection2");

        Map<String, Object> step2 = new HashMap<>();
        Map<String, Object> step2Apply = new HashMap<>();
        step2Apply.put("type", "createCollection");
        step2Apply.put("collection", "conflictCollection2"); // Already exists, will fail
        step2.put("apply", step2Apply);
        steps.add(step2);

        setRawApplyPayload(template, steps);

        assertThrows(MongoStepExecutionException.class,
                () -> template.apply(mongoDatabase, null));

        // Data from step 1 should still exist since it had no rollback
        List<Document> docs2 = mongoDatabase.getCollection("stepRollbackTest")
                .find()
                .into(new ArrayList<>());
        assertEquals(2, docs2.size(), "Should have original + step 1 data (step 1 has no rollback)");

        // Clean up
        mongoDatabase.getCollection("conflictCollection2").drop();
    }

    @Test
    @DisplayName("WHEN framework triggers rollback for step-based change THEN all steps are rolled back")
    void frameworkTriggeredRollbackForSteps() throws Exception {
        // Set up the state as if steps had been applied
        mongoDatabase.createCollection("stepRollbackTest");
        mongoDatabase.getCollection("stepRollbackTest").insertMany(Arrays.asList(
                new Document("name", "Item1"),
                new Document("name", "Item2")
        ));
        mongoDatabase.getCollection("stepRollbackTest").createIndex(
                new Document("name", 1),
                new com.mongodb.client.model.IndexOptions().name("step_index")
        );

        MongoChangeTemplate template = new MongoChangeTemplate();
        template.setChangeId("framework-rollback-test");
        template.setTransactional(false);

        // Create step payload
        List<Map<String, Object>> steps = new ArrayList<>();

        // Step 1: Create collection
        Map<String, Object> step1 = new HashMap<>();
        Map<String, Object> step1Apply = new HashMap<>();
        step1Apply.put("type", "createCollection");
        step1Apply.put("collection", "stepRollbackTest");
        Map<String, Object> step1Rollback = new HashMap<>();
        step1Rollback.put("type", "dropCollection");
        step1Rollback.put("collection", "stepRollbackTest");
        step1.put("apply", step1Apply);
        step1.put("rollback", step1Rollback);
        steps.add(step1);

        // Step 2: Insert documents
        Map<String, Object> step2 = new HashMap<>();
        Map<String, Object> step2Apply = new HashMap<>();
        step2Apply.put("type", "insert");
        step2Apply.put("collection", "stepRollbackTest");
        Map<String, Object> step2Params = new HashMap<>();
        List<Map<String, Object>> docs = new ArrayList<>();
        docs.add(new HashMap<String, Object>() {{ put("name", "Item1"); }});
        step2Params.put("documents", docs);
        step2Apply.put("parameters", step2Params);
        Map<String, Object> step2Rollback = new HashMap<>();
        step2Rollback.put("type", "delete");
        step2Rollback.put("collection", "stepRollbackTest");
        Map<String, Object> step2RollbackParams = new HashMap<>();
        step2RollbackParams.put("filter", new HashMap<>());
        step2Rollback.put("parameters", step2RollbackParams);
        step2.put("apply", step2Apply);
        step2.put("rollback", step2Rollback);
        steps.add(step2);

        // Step 3: Create index
        Map<String, Object> step3 = new HashMap<>();
        Map<String, Object> step3Apply = new HashMap<>();
        step3Apply.put("type", "createIndex");
        step3Apply.put("collection", "stepRollbackTest");
        Map<String, Object> step3Params = new HashMap<>();
        step3Params.put("keys", new HashMap<String, Object>() {{ put("name", 1); }});
        Map<String, Object> step3Options = new HashMap<>();
        step3Options.put("name", "step_index");
        step3Params.put("options", step3Options);
        step3Apply.put("parameters", step3Params);
        Map<String, Object> step3Rollback = new HashMap<>();
        step3Rollback.put("type", "dropIndex");
        step3Rollback.put("collection", "stepRollbackTest");
        Map<String, Object> step3RollbackParams = new HashMap<>();
        step3RollbackParams.put("indexName", "step_index");
        step3Rollback.put("parameters", step3RollbackParams);
        step3.put("apply", step3Apply);
        step3.put("rollback", step3Rollback);
        steps.add(step3);

        setRawApplyPayload(template, steps);

        assertTrue(collectionExists("stepRollbackTest"), "Collection should exist before rollback");

        template.rollback(mongoDatabase, null);

        // Step 3 rollback drops index, Step 2 rollback deletes docs, Step 1 rollback drops collection
        assertFalse(collectionExists("stepRollbackTest"),
                "Collection should not exist after framework-triggered rollback");
    }

    /**
     * Helper method to set raw apply payload via reflection.
     * This simulates how the framework sets the payload when loading from YAML.
     */
    private void setRawApplyPayload(MongoChangeTemplate template, Object payload) throws Exception {
        java.lang.reflect.Field field = findField(template.getClass(), "applyPayload");
        if (field != null) {
            field.setAccessible(true);
            field.set(template, payload);
        }
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

}
