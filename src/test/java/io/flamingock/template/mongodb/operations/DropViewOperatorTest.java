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
import io.flamingock.template.mongodb.model.operator.DropViewOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropViewOperatorTest extends AbstractMongoOperatorTest {

    private static final String SOURCE_COLLECTION = "dropViewSourceCollection";
    private static final String VIEW_NAME = "viewToDrop";

    @BeforeEach
    void setupEach() {
        mongoDatabase.getCollection(SOURCE_COLLECTION).drop();
        mongoDatabase.getCollection(VIEW_NAME).drop();
    }

    @Test
    @DisplayName("WHEN dropView operator is applied THEN view is dropped")
    void dropViewTest() {
        mongoDatabase.createCollection(SOURCE_COLLECTION);
        mongoDatabase.createView(VIEW_NAME, SOURCE_COLLECTION, Collections.emptyList());
        assertTrue(collectionExists(VIEW_NAME), "View should exist before drop");

        MongoOperation operation = new MongoOperation();
        operation.setType("dropView");
        operation.setCollection(VIEW_NAME);
        operation.setParameters(new HashMap<>());

        DropViewOperator operator = new DropViewOperator(mongoDatabase, operation);
        operator.apply(null);

        assertFalse(collectionExists(VIEW_NAME), "View should have been dropped");
    }

    @Test
    @DisplayName("WHEN dropView is applied on non-existent view THEN no exception is thrown")
    void dropNonExistentViewSucceedsSilentlyTest() {
        assertFalse(collectionExists(VIEW_NAME), "View should not exist before drop");

        MongoOperation operation = new MongoOperation();
        operation.setType("dropView");
        operation.setCollection(VIEW_NAME);
        operation.setParameters(new HashMap<>());

        DropViewOperator operator = new DropViewOperator(mongoDatabase, operation);
        // MongoDB drop() is natively idempotent — no exception on non-existent view
        operator.apply(null);

        assertFalse(collectionExists(VIEW_NAME), "View should still not exist");
    }

    private boolean collectionExists(String collectionName) {
        List<String> collections = mongoDatabase.listCollectionNames().into(new ArrayList<>());
        return collections.contains(collectionName);
    }
}
