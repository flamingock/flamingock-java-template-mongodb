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
import io.flamingock.template.mongodb.model.operator.CreateIndexOperator;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flamingock.template.mongodb.MongoTemplateExecutionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateIndexOperatorTest extends AbstractMongoOperatorTest {

    private static final String COLLECTION_NAME = "indexTestCollection";
    private static final String INDEX_NAME = "email_unique_index";

    @BeforeEach
    void setupEach() {
        mongoDatabase.getCollection(COLLECTION_NAME).drop();
    }

    @Test
    @DisplayName("WHEN createIndex operator is applied THEN index is created")
    void createIndexTest() {
        mongoDatabase.createCollection(COLLECTION_NAME);

        MongoOperation operation = new MongoOperation();
        operation.setType("createIndex");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        Map<String, Object> keys = new HashMap<>();
        keys.put("email", 1);
        params.put("keys", keys);
        operation.setParameters(params);

        CreateIndexOperator operator = new CreateIndexOperator(mongoDatabase, operation);
        operator.apply(null);

        assertTrue(indexExistsByKeys("email"), "Index should exist after creation");
    }

    @Test
    @DisplayName("WHEN createIndex operator is applied with options THEN index is created with options")
    void createIndexWithOptionsTest() {
        mongoDatabase.createCollection(COLLECTION_NAME);

        MongoOperation operation = new MongoOperation();
        operation.setType("createIndex");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        Map<String, Object> keys = new HashMap<>();
        keys.put("email", 1);
        params.put("keys", keys);

        Map<String, Object> options = new HashMap<>();
        options.put("unique", true);
        options.put("name", INDEX_NAME);
        params.put("options", options);

        operation.setParameters(params);

        CreateIndexOperator operator = new CreateIndexOperator(mongoDatabase, operation);
        operator.apply(null);

        assertTrue(indexExists(INDEX_NAME), "Index should exist with specified name");
        assertTrue(isIndexUnique(INDEX_NAME), "Index should be unique");
    }

    private boolean indexExists(String indexName) {
        List<Document> indexes = mongoDatabase.getCollection(COLLECTION_NAME)
                .listIndexes()
                .into(new ArrayList<>());
        return indexes.stream().anyMatch(idx -> indexName.equals(idx.getString("name")));
    }

    private boolean indexExistsByKeys(String keyField) {
        List<Document> indexes = mongoDatabase.getCollection(COLLECTION_NAME)
                .listIndexes()
                .into(new ArrayList<>());
        return indexes.stream().anyMatch(idx -> {
            Document key = idx.get("key", Document.class);
            return key != null && key.containsKey(keyField);
        });
    }

    @Test
    @DisplayName("WHEN identical index is created twice THEN no exception is thrown (idempotent)")
    void createIdenticalIndexIdempotentTest() {
        mongoDatabase.createCollection(COLLECTION_NAME);

        MongoOperation operation = new MongoOperation();
        operation.setType("createIndex");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        Map<String, Object> keys = new HashMap<>();
        keys.put("email", 1);
        params.put("keys", keys);

        Map<String, Object> options = new HashMap<>();
        options.put("unique", true);
        options.put("name", INDEX_NAME);
        params.put("options", options);
        operation.setParameters(params);

        // First creation
        new CreateIndexOperator(mongoDatabase, operation).apply(null);
        assertTrue(indexExists(INDEX_NAME), "Index should exist after first creation");

        // Second identical creation — should be idempotent
        assertDoesNotThrow(() -> new CreateIndexOperator(mongoDatabase, operation).apply(null),
                "Creating identical index should not throw");
        assertTrue(indexExists(INDEX_NAME), "Index should still exist after second creation");
    }

    @Test
    @DisplayName("WHEN index with same name but different keys is created THEN MongoTemplateExecutionException is thrown")
    void createConflictingIndexThrowsTest() {
        mongoDatabase.createCollection(COLLECTION_NAME);

        // First: create index on email with a specific name
        MongoOperation operation1 = new MongoOperation();
        operation1.setType("createIndex");
        operation1.setCollection(COLLECTION_NAME);

        Map<String, Object> params1 = new HashMap<>();
        Map<String, Object> keys1 = new HashMap<>();
        keys1.put("email", 1);
        params1.put("keys", keys1);

        Map<String, Object> options1 = new HashMap<>();
        options1.put("name", "my_index");
        params1.put("options", options1);
        operation1.setParameters(params1);

        new CreateIndexOperator(mongoDatabase, operation1).apply(null);

        // Second: create index on a DIFFERENT key but with the SAME name — guaranteed conflict
        MongoOperation operation2 = new MongoOperation();
        operation2.setType("createIndex");
        operation2.setCollection(COLLECTION_NAME);

        Map<String, Object> params2 = new HashMap<>();
        Map<String, Object> keys2 = new HashMap<>();
        keys2.put("name", 1);
        params2.put("keys", keys2);

        Map<String, Object> options2 = new HashMap<>();
        options2.put("name", "my_index");
        params2.put("options", options2);
        operation2.setParameters(params2);

        assertThrows(MongoTemplateExecutionException.class,
                () -> new CreateIndexOperator(mongoDatabase, operation2).apply(null),
                "Creating index with same name but different keys should throw");
    }

    private boolean isIndexUnique(String indexName) {
        List<Document> indexes = mongoDatabase.getCollection(COLLECTION_NAME)
                .listIndexes()
                .into(new ArrayList<>());
        return indexes.stream()
                .filter(idx -> indexName.equals(idx.getString("name")))
                .anyMatch(idx -> Boolean.TRUE.equals(idx.getBoolean("unique")));
    }
}
