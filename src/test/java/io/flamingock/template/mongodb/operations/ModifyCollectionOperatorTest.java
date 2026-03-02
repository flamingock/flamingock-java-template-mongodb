/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.template.mongodb.operations;

import io.flamingock.template.mongodb.model.MongoOperation;
import io.flamingock.template.mongodb.model.operator.ModifyCollectionOperator;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import io.flamingock.template.mongodb.MongoTemplateExecutionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModifyCollectionOperatorTest extends AbstractMongoOperatorTest {

    private static final String COLLECTION_NAME = "modifyTestCollection";

    @BeforeEach
    void setupEach() {
        mongoDatabase.getCollection(COLLECTION_NAME).drop();
    }

    @Test
    @DisplayName("WHEN modifyCollection operator is applied THEN collection validation is set")
    void modifyCollectionTest() {
        mongoDatabase.createCollection(COLLECTION_NAME);

        MongoOperation operation = new MongoOperation();
        operation.setType("modifyCollection");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        Map<String, Object> validator = new HashMap<>();
        Map<String, Object> jsonSchema = new HashMap<>();
        jsonSchema.put("bsonType", "object");
        validator.put("$jsonSchema", jsonSchema);
        params.put("validator", validator);
        params.put("validationLevel", "moderate");
        params.put("validationAction", "warn");
        operation.setParameters(params);

        ModifyCollectionOperator operator = new ModifyCollectionOperator(mongoDatabase, operation);
        operator.apply(null);

        Document collectionInfo = mongoDatabase.listCollections()
                .filter(new Document("name", COLLECTION_NAME))
                .first();
        assertNotNull(collectionInfo, "Collection should exist");
        Document options = collectionInfo.get("options", Document.class);
        assertNotNull(options, "Collection should have options");
        assertNotNull(options.get("validator"), "Collection should have validator");
    }

    @Test
    @DisplayName("WHEN modifyCollection with validationAction=error THEN non-conforming inserts are rejected by MongoDB")
    void modifyCollectionEnforcesValidationTest() {
        mongoDatabase.createCollection(COLLECTION_NAME);

        MongoOperation operation = new MongoOperation();
        operation.setType("modifyCollection");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        Map<String, Object> validator = new HashMap<>();
        Map<String, Object> jsonSchema = new HashMap<>();
        jsonSchema.put("bsonType", "object");
        jsonSchema.put("required", java.util.Arrays.asList("name", "email"));
        Map<String, Object> properties = new HashMap<>();
        properties.put("name", singletonMap("bsonType", "string"));
        properties.put("email", singletonMap("bsonType", "string"));
        jsonSchema.put("properties", properties);
        validator.put("$jsonSchema", jsonSchema);
        params.put("validator", validator);
        params.put("validationLevel", "strict");
        params.put("validationAction", "error");
        operation.setParameters(params);

        new ModifyCollectionOperator(mongoDatabase, operation).apply(null);

        // Attempt to insert a non-conforming document directly via driver
        try {
            mongoDatabase.getCollection(COLLECTION_NAME)
                    .insertOne(new Document("name", "Alice"));  // Missing 'email'
            throw new AssertionError("Insert should have been rejected by validator");
        } catch (com.mongodb.MongoWriteException e) {
            // Expected: MongoDB rejects the insert due to schema validation
        }
    }

    @Test
    @DisplayName("WHEN modifyCollection is applied THEN validationLevel and validationAction are set in metadata")
    void modifyCollectionAssertionsTest() {
        mongoDatabase.createCollection(COLLECTION_NAME);

        MongoOperation operation = new MongoOperation();
        operation.setType("modifyCollection");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        Map<String, Object> validator = new HashMap<>();
        Map<String, Object> jsonSchema = new HashMap<>();
        jsonSchema.put("bsonType", "object");
        validator.put("$jsonSchema", jsonSchema);
        params.put("validator", validator);
        params.put("validationLevel", "moderate");
        params.put("validationAction", "warn");
        operation.setParameters(params);

        new ModifyCollectionOperator(mongoDatabase, operation).apply(null);

        Document collectionInfo = mongoDatabase.listCollections()
                .filter(new Document("name", COLLECTION_NAME))
                .first();
        assertNotNull(collectionInfo, "Collection should exist");
        Document options = collectionInfo.get("options", Document.class);
        assertNotNull(options, "Collection should have options");
        assertNotNull(options.get("validator"), "Collection should have validator");
        assertEquals("moderate", options.getString("validationLevel"),
                "validationLevel should be 'moderate'");
        assertEquals("warn", options.getString("validationAction"),
                "validationAction should be 'warn'");
    }

    @Test
    @DisplayName("WHEN modifyCollection with only validationLevel THEN modification succeeds")
    void modifyCollectionWithPartialParametersTest() {
        mongoDatabase.createCollection(COLLECTION_NAME);

        MongoOperation operation = new MongoOperation();
        operation.setType("modifyCollection");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        params.put("validationLevel", "moderate");
        operation.setParameters(params);

        // Should succeed with only validationLevel (no validator, no validationAction)
        assertDoesNotThrow(() -> new ModifyCollectionOperator(mongoDatabase, operation).apply(null),
                "Modification with only validationLevel should succeed");

        Document collectionInfo = mongoDatabase.listCollections()
                .filter(new Document("name", COLLECTION_NAME))
                .first();
        assertNotNull(collectionInfo, "Collection should exist");
        Document options = collectionInfo.get("options", Document.class);
        assertNotNull(options, "Collection should have options");
        assertEquals("moderate", options.getString("validationLevel"),
                "validationLevel should be set to 'moderate'");
    }

    @Test
    @DisplayName("WHEN same modification is applied twice THEN no exception is thrown (idempotent)")
    void modifyCollectionIdempotentTest() {
        mongoDatabase.createCollection(COLLECTION_NAME);

        MongoOperation operation = new MongoOperation();
        operation.setType("modifyCollection");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        Map<String, Object> validator = new HashMap<>();
        Map<String, Object> jsonSchema = new HashMap<>();
        jsonSchema.put("bsonType", "object");
        validator.put("$jsonSchema", jsonSchema);
        params.put("validator", validator);
        params.put("validationLevel", "moderate");
        params.put("validationAction", "warn");
        operation.setParameters(params);

        // First application
        new ModifyCollectionOperator(mongoDatabase, operation).apply(null);

        // Second identical application — should be idempotent
        assertDoesNotThrow(() -> new ModifyCollectionOperator(mongoDatabase, operation).apply(null),
                "Applying the same modification twice should not throw");
    }

    private Map<String, Object> singletonMap(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }
}
