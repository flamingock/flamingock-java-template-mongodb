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
import io.flamingock.template.mongodb.model.operator.UpdateOperator;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdateOperatorTransactionalTest extends AbstractTransactionalOperatorTest {

    private static final String COLLECTION_NAME = "updateTxnTestCollection";

    @BeforeEach
    void setupEach() {
        mongoDatabase.getCollection(COLLECTION_NAME).drop();
        mongoDatabase.createCollection(COLLECTION_NAME);
        List<Document> docs = new ArrayList<>();
        docs.add(new Document("name", "Alice").append("role", "admin").append("score", 100));
        docs.add(new Document("name", "Bob").append("role", "user").append("score", 80));
        docs.add(new Document("name", "Charlie").append("role", "user").append("score", 90));
        mongoDatabase.getCollection(COLLECTION_NAME).insertMany(docs);
    }

    @Test
    @DisplayName("WHEN updateOne is applied with session THEN update is visible after commit")
    void updateOneWithSessionTest() {
        MongoOperation operation = buildUpdateOperation("role", "user", "role", "guest", false);
        new UpdateOperator(mongoDatabase, operation).apply(clientSession);

        clientSession.commitTransaction();

        long guestCount = mongoDatabase.getCollection(COLLECTION_NAME)
                .countDocuments(new Document("role", "guest"));
        assertEquals(1, guestCount, "Only one document should be updated");
    }

    @Test
    @DisplayName("WHEN updateMany is applied with session THEN all matching documents are updated after commit")
    void updateManyWithSessionTest() {
        MongoOperation operation = buildUpdateOperation("role", "user", "role", "guest", true);
        new UpdateOperator(mongoDatabase, operation).apply(clientSession);

        clientSession.commitTransaction();

        long guestCount = mongoDatabase.getCollection(COLLECTION_NAME)
                .countDocuments(new Document("role", "guest"));
        assertEquals(2, guestCount, "Both user documents should be updated to guest");

        long userCount = mongoDatabase.getCollection(COLLECTION_NAME)
                .countDocuments(new Document("role", "user"));
        assertEquals(0, userCount, "No user documents should remain");
    }

    @Test
    @DisplayName("WHEN update is applied with session and transaction is aborted THEN data is unchanged")
    void updateWithSessionAbortRollsBackTest() {
        MongoOperation operation = buildUpdateOperation("role", "user", "role", "guest", true);
        new UpdateOperator(mongoDatabase, operation).apply(clientSession);

        clientSession.abortTransaction();

        long userCount = mongoDatabase.getCollection(COLLECTION_NAME)
                .countDocuments(new Document("role", "user"));
        assertEquals(2, userCount,
                "User documents should remain unchanged after abort — proves session is passed through");

        long guestCount = mongoDatabase.getCollection(COLLECTION_NAME)
                .countDocuments(new Document("role", "guest"));
        assertEquals(0, guestCount, "No documents should have been updated after abort");
    }

    private MongoOperation buildUpdateOperation(String filterKey, Object filterValue,
                                                 String setKey, Object setValue, boolean multi) {
        MongoOperation operation = new MongoOperation();
        operation.setType("update");
        operation.setCollection(COLLECTION_NAME);

        Map<String, Object> params = new HashMap<>();
        Map<String, Object> filter = new HashMap<>();
        filter.put(filterKey, filterValue);
        params.put("filter", filter);

        Map<String, Object> update = new HashMap<>();
        Map<String, Object> setFields = new HashMap<>();
        setFields.put(setKey, setValue);
        update.put("$set", setFields);
        params.put("update", update);

        if (multi) {
            params.put("multi", true);
        }
        operation.setParameters(params);
        return operation;
    }
}
