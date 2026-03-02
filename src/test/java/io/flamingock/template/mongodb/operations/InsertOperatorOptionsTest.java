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

import io.flamingock.template.mongodb.MongoTemplateExecutionException;
import io.flamingock.template.mongodb.model.MongoOperation;
import io.flamingock.template.mongodb.model.operator.InsertOperator;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InsertOperatorOptionsTest extends AbstractMongoOperatorTest {

    private static final String COLLECTION_NAME = "insertOptionsTestCollection";

    @BeforeEach
    void setupEach() {
        mongoDatabase.getCollection(COLLECTION_NAME).drop();
    }

    @Test
    @DisplayName("WHEN insert with bypassDocumentValidation=true THEN document bypasses schema validator")
    void insertWithBypassDocumentValidationTest() {
        // Create collection with JSON schema validator requiring 'name' and 'email'
        Document validator = new Document("$jsonSchema",
                new Document("bsonType", "object")
                        .append("required", java.util.Arrays.asList("name", "email"))
                        .append("properties", new Document("name",
                                new Document("bsonType", "string"))
                                .append("email", new Document("bsonType", "string"))));

        mongoDatabase.runCommand(new Document("create", COLLECTION_NAME)
                .append("validator", validator)
                .append("validationAction", "error"));

        // Insert a document missing 'email' — should succeed with bypassDocumentValidation=true
        MongoOperation operation = new MongoOperation();
        operation.setType("insert");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        List<Map<String, Object>> documents = new ArrayList<>();
        Map<String, Object> doc = new HashMap<>();
        doc.put("name", "Alice");
        // Intentionally omit 'email'
        documents.add(doc);
        params.put("documents", documents);

        Map<String, Object> options = new HashMap<>();
        options.put("bypassDocumentValidation", true);
        params.put("options", options);
        operation.setParameters(params);

        new InsertOperator(mongoDatabase, operation).apply(null);

        assertEquals(1, mongoDatabase.getCollection(COLLECTION_NAME).countDocuments(),
                "Document should be inserted despite missing required field");
    }

    @Test
    @DisplayName("WHEN insertMany with ordered=false and duplicate _id THEN non-duplicate documents are still inserted")
    void insertManyWithOrderedFalseTest() {
        mongoDatabase.createCollection(COLLECTION_NAME);

        // Seed a document to create duplicate _id conflict
        String duplicateId = "dup-id-001";
        mongoDatabase.getCollection(COLLECTION_NAME)
                .insertOne(new Document("_id", duplicateId).append("name", "Existing"));

        MongoOperation operation = new MongoOperation();
        operation.setType("insert");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        List<Map<String, Object>> documents = new ArrayList<>();

        Map<String, Object> doc1 = new HashMap<>();
        doc1.put("_id", "unique-001");
        doc1.put("name", "First");
        documents.add(doc1);

        Map<String, Object> doc2 = new HashMap<>();
        doc2.put("_id", duplicateId);  // This will conflict
        doc2.put("name", "Duplicate");
        documents.add(doc2);

        Map<String, Object> doc3 = new HashMap<>();
        doc3.put("_id", "unique-002");
        doc3.put("name", "Third");
        documents.add(doc3);

        params.put("documents", documents);

        Map<String, Object> options = new HashMap<>();
        options.put("ordered", false);
        params.put("options", options);
        operation.setParameters(params);

        // With ordered=false, driver continues past errors — non-duplicates are inserted
        assertThrows(MongoTemplateExecutionException.class,
                () -> new InsertOperator(mongoDatabase, operation).apply(null));

        // 1 existing + 2 non-conflicting = 3 total
        assertEquals(3, mongoDatabase.getCollection(COLLECTION_NAME).countDocuments(),
                "With ordered=false, first and third documents should be inserted despite second failing");
    }

    @Test
    @DisplayName("WHEN insertMany with ordered=true and duplicate _id THEN only documents before error are inserted")
    void insertManyWithOrderedTrueStopsOnFirstErrorTest() {
        mongoDatabase.createCollection(COLLECTION_NAME);

        // Seed a document to create duplicate _id conflict
        String duplicateId = "dup-id-002";
        mongoDatabase.getCollection(COLLECTION_NAME)
                .insertOne(new Document("_id", duplicateId).append("name", "Existing"));

        MongoOperation operation = new MongoOperation();
        operation.setType("insert");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        List<Map<String, Object>> documents = new ArrayList<>();

        Map<String, Object> doc1 = new HashMap<>();
        doc1.put("_id", "unique-003");
        doc1.put("name", "First");
        documents.add(doc1);

        Map<String, Object> doc2 = new HashMap<>();
        doc2.put("_id", duplicateId);  // This will conflict
        doc2.put("name", "Duplicate");
        documents.add(doc2);

        Map<String, Object> doc3 = new HashMap<>();
        doc3.put("_id", "unique-004");
        doc3.put("name", "Third");
        documents.add(doc3);

        params.put("documents", documents);

        Map<String, Object> options = new HashMap<>();
        options.put("ordered", true);
        params.put("options", options);
        operation.setParameters(params);

        // With ordered=true, driver stops on first error
        assertThrows(MongoTemplateExecutionException.class,
                () -> new InsertOperator(mongoDatabase, operation).apply(null));

        // 1 existing + 1 first doc = 2 total (third doc not inserted)
        assertEquals(2, mongoDatabase.getCollection(COLLECTION_NAME).countDocuments(),
                "With ordered=true, only the first document should be inserted before error stops execution");
    }
}
