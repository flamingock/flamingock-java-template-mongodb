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
import io.flamingock.template.mongodb.model.operator.DeleteOperator;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeleteOperatorTransactionalTest extends AbstractTransactionalOperatorTest {

    private static final String COLLECTION_NAME = "deleteTxnTestCollection";

    @BeforeEach
    void setupEach() {
        mongoDatabase.getCollection(COLLECTION_NAME).drop();
        mongoDatabase.createCollection(COLLECTION_NAME);
        List<Document> docs = new ArrayList<>();
        docs.add(new Document("name", "Alice").append("role", "admin"));
        docs.add(new Document("name", "Bob").append("role", "user"));
        docs.add(new Document("name", "Charlie").append("role", "user"));
        docs.add(new Document("name", "Diana").append("role", "admin"));
        mongoDatabase.getCollection(COLLECTION_NAME).insertMany(docs);
    }

    @Test
    @DisplayName("WHEN deleteOne is applied with session THEN one document is deleted after commit")
    void deleteOneWithSessionTest() {
        assertEquals(4, getDocumentCount(), "Collection should have 4 documents before delete");

        MongoOperation operation = buildDeleteOperation("role", "user", false);
        new DeleteOperator(mongoDatabase, operation).apply(clientSession);

        clientSession.commitTransaction();

        assertEquals(3, getDocumentCount(), "One document should be deleted after commit");
    }

    @Test
    @DisplayName("WHEN deleteMany is applied with session THEN all matching documents are deleted after commit")
    void deleteManyWithSessionTest() {
        assertEquals(4, getDocumentCount(), "Collection should have 4 documents before delete");

        MongoOperation operation = buildDeleteOperation("role", "user", true);
        new DeleteOperator(mongoDatabase, operation).apply(clientSession);

        clientSession.commitTransaction();

        assertEquals(2, getDocumentCount(), "Two user documents should be deleted after commit");

        List<Document> remaining = mongoDatabase.getCollection(COLLECTION_NAME)
                .find()
                .into(new ArrayList<>());
        for (Document doc : remaining) {
            assertEquals("admin", doc.getString("role"), "Only admin documents should remain");
        }
    }

    @Test
    @DisplayName("WHEN delete is applied with session and transaction is aborted THEN documents are unchanged")
    void deleteWithSessionAbortRollsBackTest() {
        assertEquals(4, getDocumentCount(), "Collection should have 4 documents before delete");

        MongoOperation operation = buildDeleteOperation("role", "user", true);
        new DeleteOperator(mongoDatabase, operation).apply(clientSession);

        clientSession.abortTransaction();

        assertEquals(4, getDocumentCount(),
                "All documents should still exist after abort — proves session is passed through");
    }

    private MongoOperation buildDeleteOperation(String filterKey, Object filterValue, boolean multi) {
        MongoOperation operation = new MongoOperation();
        operation.setType("delete");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        Map<String, Object> filter = new HashMap<>();
        filter.put(filterKey, filterValue);
        params.put("filter", filter);
        if (multi) {
            params.put("multi", true);
        }
        operation.setParameters(params);
        return operation;
    }

    private long getDocumentCount() {
        return mongoDatabase.getCollection(COLLECTION_NAME).countDocuments();
    }
}
