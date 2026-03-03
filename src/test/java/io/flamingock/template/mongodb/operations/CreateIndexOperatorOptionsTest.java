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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateIndexOperatorOptionsTest extends AbstractMongoOperatorTest {

    private static final String COLLECTION_NAME = "indexOptionsTestCollection";

    @BeforeEach
    void setupEach() {
        mongoDatabase.getCollection(COLLECTION_NAME).drop();
        mongoDatabase.createCollection(COLLECTION_NAME);
    }

    @Test
    @DisplayName("WHEN createIndex with expireAfterSeconds THEN TTL index is created")
    void createTTLIndexTest() {
        MongoOperation operation = new MongoOperation();
        operation.setType("createIndex");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        Map<String, Object> keys = new HashMap<>();
        keys.put("createdAt", 1);
        params.put("keys", keys);

        Map<String, Object> options = new HashMap<>();
        options.put("expireAfterSeconds", 3600);
        options.put("name", "ttl_index");
        params.put("options", options);
        operation.setParameters(params);

        new CreateIndexOperator(mongoDatabase, operation).apply(null);

        Document index = findIndexByName("ttl_index");
        assertNotNull(index, "TTL index should exist");
        Number expireAfterSeconds = (Number) index.get("expireAfterSeconds");
        assertNotNull(expireAfterSeconds, "Index should have expireAfterSeconds");
        assertEquals(3600, expireAfterSeconds.intValue(),
                "Index should have expireAfterSeconds=3600");
    }

    @Test
    @DisplayName("WHEN createIndex with sparse=true THEN sparse index is created")
    void createSparseIndexTest() {
        MongoOperation operation = new MongoOperation();
        operation.setType("createIndex");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        Map<String, Object> keys = new HashMap<>();
        keys.put("optionalField", 1);
        params.put("keys", keys);

        Map<String, Object> options = new HashMap<>();
        options.put("sparse", true);
        options.put("name", "sparse_index");
        params.put("options", options);
        operation.setParameters(params);

        new CreateIndexOperator(mongoDatabase, operation).apply(null);

        Document index = findIndexByName("sparse_index");
        assertNotNull(index, "Sparse index should exist");
        assertTrue(Boolean.TRUE.equals(index.getBoolean("sparse")),
                "Index should be sparse");
    }

    @Test
    @DisplayName("WHEN createIndex with partialFilterExpression THEN partial index is created")
    void createIndexWithPartialFilterExpressionTest() {
        MongoOperation operation = new MongoOperation();
        operation.setType("createIndex");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        Map<String, Object> keys = new HashMap<>();
        keys.put("email", 1);
        params.put("keys", keys);

        Map<String, Object> options = new HashMap<>();
        options.put("name", "partial_filter_index");
        Map<String, Object> partialFilter = new HashMap<>();
        partialFilter.put("status", "active");
        options.put("partialFilterExpression", partialFilter);
        params.put("options", options);
        operation.setParameters(params);

        new CreateIndexOperator(mongoDatabase, operation).apply(null);

        Document index = findIndexByName("partial_filter_index");
        assertNotNull(index, "Partial filter index should exist");
        Document partialFilterExpression = index.get("partialFilterExpression", Document.class);
        assertNotNull(partialFilterExpression, "Index should have partialFilterExpression");
        assertEquals("active", partialFilterExpression.getString("status"),
                "Partial filter should match the configured expression");
    }

    private Document findIndexByName(String indexName) {
        List<Document> indexes = mongoDatabase.getCollection(COLLECTION_NAME)
                .listIndexes()
                .into(new ArrayList<>());
        return indexes.stream()
                .filter(idx -> indexName.equals(idx.getString("name")))
                .findFirst()
                .orElse(null);
    }
}
