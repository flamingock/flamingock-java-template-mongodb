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
import io.flamingock.template.mongodb.model.operator.DropCollectionOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropCollectionOperatorTest extends AbstractMongoOperatorTest {

    private static final String COLLECTION_NAME = "testCollection";

    @BeforeEach
    void setupEach() {
        mongoDatabase.getCollection(COLLECTION_NAME).drop();
    }

    @Test
    @DisplayName("WHEN dropCollection operator is applied THEN collection is dropped")
    void dropCollectionTest() {
        mongoDatabase.createCollection(COLLECTION_NAME);
        assertTrue(collectionExists(COLLECTION_NAME), "Collection should exist before drop");

        MongoOperation operation = new MongoOperation();
        operation.setType("dropCollection");
        operation.setCollection(COLLECTION_NAME);
        operation.setParameters(new HashMap<>());

        DropCollectionOperator operator = new DropCollectionOperator(mongoDatabase, operation);
        operator.apply(null);

        assertFalse(collectionExists(COLLECTION_NAME), "Collection should have been dropped");
    }

    private boolean collectionExists(String collectionName) {
        return mongoDatabase.listCollectionNames()
                .into(new ArrayList<>())
                .contains(collectionName);
    }
}
