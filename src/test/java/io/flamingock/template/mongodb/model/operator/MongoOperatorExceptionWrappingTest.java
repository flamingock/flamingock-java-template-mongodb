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
package io.flamingock.template.mongodb.model.operator;

import com.mongodb.client.ClientSession;
import io.flamingock.template.mongodb.MongoTemplateExecutionException;
import io.flamingock.template.mongodb.model.MongoOperation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoOperatorExceptionWrappingTest {

    private static MongoOperation createOp(String type, String collection) {
        MongoOperation op = new MongoOperation();
        op.setType(type);
        op.setCollection(collection);
        return op;
    }

    private static MongoOperator createOperator(MongoOperation op, Runnable action) {
        return new MongoOperator(null, op, false) {
            @Override
            protected void applyInternal(ClientSession clientSession) {
                if (action != null) {
                    action.run();
                }
            }
        };
    }

    @Test
    @DisplayName("WHEN applyInternal throws RuntimeException THEN wrapped with MongoTemplateExecutionException")
    void wrapsRuntimeExceptionTest() {
        MongoOperation op = createOp("insert", "users");
        RuntimeException original = new RuntimeException("connection refused");
        MongoOperator operator = createOperator(op, () -> { throw original; });

        MongoTemplateExecutionException ex = assertThrows(
                MongoTemplateExecutionException.class,
                () -> operator.apply(null)
        );

        assertTrue(ex.getMessage().contains("insert"));
        assertTrue(ex.getMessage().contains("users"));
        assertTrue(ex.getMessage().contains("connection refused"));
        assertSame(original, ex.getCause());
    }

    @Test
    @DisplayName("WHEN applyInternal throws checked-style Exception THEN wrapped with MongoTemplateExecutionException")
    void wrapsMongoCommandExceptionTest() {
        MongoOperation op = createOp("createIndex", "orders");
        IllegalStateException original = new IllegalStateException("duplicate key");
        MongoOperator operator = createOperator(op, () -> { throw original; });

        MongoTemplateExecutionException ex = assertThrows(
                MongoTemplateExecutionException.class,
                () -> operator.apply(null)
        );

        assertTrue(ex.getMessage().contains("createIndex"));
        assertTrue(ex.getMessage().contains("orders"));
        assertSame(original, ex.getCause());
    }

    @Test
    @DisplayName("WHEN applyInternal succeeds THEN no exception thrown")
    void noExceptionOnSuccessTest() {
        MongoOperation op = createOp("insert", "users");
        MongoOperator operator = createOperator(op, null);

        assertDoesNotThrow(() -> operator.apply(null));
    }

    @Test
    @DisplayName("WHEN applyInternal throws MongoTemplateExecutionException THEN not double-wrapped")
    void doesNotDoubleWrapTest() {
        MongoOperation op = createOp("update", "products");
        MongoTemplateExecutionException original =
                new MongoTemplateExecutionException("update", "products", new RuntimeException("inner"));
        MongoOperator operator = createOperator(op, () -> { throw original; });

        MongoTemplateExecutionException ex = assertThrows(
                MongoTemplateExecutionException.class,
                () -> operator.apply(null)
        );

        assertSame(original, ex);
    }

    @Test
    @DisplayName("WHEN exception constructed THEN message matches expected format")
    void exceptionMessageFormatTest() {
        MongoTemplateExecutionException ex =
                new MongoTemplateExecutionException("delete", "logs", new RuntimeException("timeout"));

        assertEquals("Failed to execute 'delete' on collection 'logs': timeout", ex.getMessage());
    }
}
