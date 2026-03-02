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
import io.flamingock.template.mongodb.model.operator.RenameCollectionOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flamingock.template.mongodb.MongoTemplateExecutionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenameCollectionOperatorTest extends AbstractMongoOperatorTest {

    private static final String ORIGINAL_NAME = "originalCollection";
    private static final String RENAMED_NAME = "renamedCollection";

    @BeforeEach
    void setupEach() {
        mongoDatabase.getCollection(ORIGINAL_NAME).drop();
        mongoDatabase.getCollection(RENAMED_NAME).drop();
    }

    @Test
    @DisplayName("WHEN renameCollection operator is applied THEN collection is renamed")
    void renameCollectionTest() {
        mongoDatabase.createCollection(ORIGINAL_NAME);
        assertTrue(collectionExists(ORIGINAL_NAME), "Original collection should exist before rename");

        MongoOperation operation = new MongoOperation();
        operation.setType("renameCollection");
        operation.setCollection(ORIGINAL_NAME);
        Map<String, Object> params = new HashMap<>();
        params.put("target", RENAMED_NAME);
        operation.setParameters(params);

        RenameCollectionOperator operator = new RenameCollectionOperator(mongoDatabase, operation);
        operator.apply(null);

        assertFalse(collectionExists(ORIGINAL_NAME), "Original collection should not exist after rename");
        assertTrue(collectionExists(RENAMED_NAME), "Renamed collection should exist");
    }

    @Test
    @DisplayName("WHEN renameCollection operator is applied twice THEN second call succeeds silently")
    void renameCollectionIdempotentTest() {
        mongoDatabase.createCollection(ORIGINAL_NAME);
        assertTrue(collectionExists(ORIGINAL_NAME), "Original collection should exist before rename");

        MongoOperation operation = buildRenameOperation();
        RenameCollectionOperator operator = new RenameCollectionOperator(mongoDatabase, operation);
        operator.apply(null);

        assertFalse(collectionExists(ORIGINAL_NAME), "Original should not exist after rename");
        assertTrue(collectionExists(RENAMED_NAME), "Renamed should exist after rename");

        // Second apply should not throw (source gone, target exists → already renamed)
        assertDoesNotThrow(() -> operator.apply(null));
        assertTrue(collectionExists(RENAMED_NAME), "Renamed should still exist after second apply");
    }

    @Test
    @DisplayName("WHEN source is gone and target exists THEN operation is skipped as already renamed")
    void renameCollectionAlreadyRenamedTest() {
        mongoDatabase.createCollection(RENAMED_NAME);
        assertFalse(collectionExists(ORIGINAL_NAME), "Original should not exist");
        assertTrue(collectionExists(RENAMED_NAME), "Target should already exist");

        MongoOperation operation = buildRenameOperation();
        RenameCollectionOperator operator = new RenameCollectionOperator(mongoDatabase, operation);
        assertDoesNotThrow(() -> operator.apply(null));
        assertTrue(collectionExists(RENAMED_NAME), "Target should still exist after skipped rename");
    }

    @Test
    @DisplayName("WHEN both source and target exist THEN operation throws MongoTemplateExecutionException")
    void renameCollectionBothExistTest() {
        mongoDatabase.createCollection(ORIGINAL_NAME);
        mongoDatabase.createCollection(RENAMED_NAME);
        assertTrue(collectionExists(ORIGINAL_NAME), "Source should exist");
        assertTrue(collectionExists(RENAMED_NAME), "Target should exist");

        MongoOperation operation = buildRenameOperation();
        RenameCollectionOperator operator = new RenameCollectionOperator(mongoDatabase, operation);
        assertThrows(MongoTemplateExecutionException.class, () -> operator.apply(null));
    }

    @Test
    @DisplayName("WHEN neither source nor target exists THEN operation throws MongoTemplateExecutionException")
    void renameCollectionNeitherExistsTest() {
        assertFalse(collectionExists(ORIGINAL_NAME), "Source should not exist");
        assertFalse(collectionExists(RENAMED_NAME), "Target should not exist");

        MongoOperation operation = buildRenameOperation();
        RenameCollectionOperator operator = new RenameCollectionOperator(mongoDatabase, operation);
        assertThrows(MongoTemplateExecutionException.class, () -> operator.apply(null));
    }

    private MongoOperation buildRenameOperation() {
        MongoOperation operation = new MongoOperation();
        operation.setType("renameCollection");
        operation.setCollection(ORIGINAL_NAME);
        Map<String, Object> params = new HashMap<>();
        params.put("target", RENAMED_NAME);
        operation.setParameters(params);
        return operation;
    }

    private boolean collectionExists(String collectionName) {
        List<String> collections = mongoDatabase.listCollectionNames().into(new ArrayList<>());
        return collections.contains(collectionName);
    }
}
