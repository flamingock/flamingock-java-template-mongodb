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

class InsertOperatorTransactionalTest extends AbstractTransactionalOperatorTest {

    private static final String COLLECTION_NAME = "insertTxnTestCollection";

    @BeforeEach
    void setupEach() {
        mongoDatabase.getCollection(COLLECTION_NAME).drop();
        mongoDatabase.createCollection(COLLECTION_NAME);
    }

    @Test
    @DisplayName("WHEN insertOne is applied with session THEN document is visible after commit")
    void insertOneWithSessionTest() {
        assertEquals(0, getDocumentCount(), "Collection should be empty before insert");

        MongoOperation operation = buildInsertOperation(buildSingleDocumentList("Alice", "alice@example.com"));
        new InsertOperator(mongoDatabase, operation).apply(clientSession);

        // Before commit: document not visible outside transaction
        assertEquals(0, getDocumentCountWithoutSession(),
                "Document should not be visible outside transaction before commit");

        clientSession.commitTransaction();

        assertEquals(1, getDocumentCount(), "Collection should have one document after commit");
        Document inserted = mongoDatabase.getCollection(COLLECTION_NAME).find().first();
        assertEquals("Alice", inserted.getString("name"));
        assertEquals("alice@example.com", inserted.getString("email"));
    }

    @Test
    @DisplayName("WHEN insertMany is applied with session THEN all documents are visible after commit")
    void insertManyWithSessionTest() {
        assertEquals(0, getDocumentCount(), "Collection should be empty before insert");

        List<Map<String, Object>> docs = new ArrayList<>();
        docs.add(buildDocument("Alice", "alice@example.com"));
        docs.add(buildDocument("Bob", "bob@example.com"));
        docs.add(buildDocument("Charlie", "charlie@example.com"));

        MongoOperation operation = buildInsertOperation(docs);
        new InsertOperator(mongoDatabase, operation).apply(clientSession);

        clientSession.commitTransaction();

        assertEquals(3, getDocumentCount(), "Collection should have three documents after commit");
    }

    @Test
    @DisplayName("WHEN insertOne is applied with session and options THEN document is inserted with options")
    void insertOneWithSessionAndOptionsTest() {
        assertEquals(0, getDocumentCount(), "Collection should be empty before insert");

        MongoOperation operation = buildInsertOperation(buildSingleDocumentList("Alice", "alice@example.com"));
        Map<String, Object> options = new HashMap<>();
        options.put("bypassDocumentValidation", true);
        operation.getParameters().put("options", options);

        new InsertOperator(mongoDatabase, operation).apply(clientSession);
        clientSession.commitTransaction();

        assertEquals(1, getDocumentCount(), "Collection should have one document after commit");
    }

    @Test
    @DisplayName("WHEN insertMany is applied with session and options THEN documents are inserted with options")
    void insertManyWithSessionAndOptionsTest() {
        assertEquals(0, getDocumentCount(), "Collection should be empty before insert");

        List<Map<String, Object>> docs = new ArrayList<>();
        docs.add(buildDocument("Alice", "alice@example.com"));
        docs.add(buildDocument("Bob", "bob@example.com"));

        MongoOperation operation = buildInsertOperation(docs);
        Map<String, Object> options = new HashMap<>();
        options.put("bypassDocumentValidation", true);
        operation.getParameters().put("options", options);

        new InsertOperator(mongoDatabase, operation).apply(clientSession);
        clientSession.commitTransaction();

        assertEquals(2, getDocumentCount(), "Collection should have two documents after commit");
    }

    @Test
    @DisplayName("WHEN insert is applied with session and transaction is aborted THEN documents are rolled back")
    void insertWithSessionAbortRollsBackTest() {
        assertEquals(0, getDocumentCount(), "Collection should be empty before insert");

        List<Map<String, Object>> docs = new ArrayList<>();
        docs.add(buildDocument("Alice", "alice@example.com"));
        docs.add(buildDocument("Bob", "bob@example.com"));

        MongoOperation operation = buildInsertOperation(docs);
        new InsertOperator(mongoDatabase, operation).apply(clientSession);

        clientSession.abortTransaction();

        assertEquals(0, getDocumentCount(),
                "Collection should be empty after abort — proves session is passed through to driver");
    }

    private MongoOperation buildInsertOperation(List<Map<String, Object>> documents) {
        MongoOperation operation = new MongoOperation();
        operation.setType("insert");
        operation.setCollection(COLLECTION_NAME);
        Map<String, Object> params = new HashMap<>();
        params.put("documents", documents);
        operation.setParameters(params);
        return operation;
    }

    private List<Map<String, Object>> buildSingleDocumentList(String name, String email) {
        List<Map<String, Object>> docs = new ArrayList<>();
        docs.add(buildDocument(name, email));
        return docs;
    }

    private Map<String, Object> buildDocument(String name, String email) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("name", name);
        doc.put("email", email);
        return doc;
    }

    private long getDocumentCount() {
        return mongoDatabase.getCollection(COLLECTION_NAME).countDocuments();
    }

    private long getDocumentCountWithoutSession() {
        // Reads outside the transaction session to verify isolation
        return mongoDatabase.getCollection(COLLECTION_NAME).countDocuments();
    }
}
