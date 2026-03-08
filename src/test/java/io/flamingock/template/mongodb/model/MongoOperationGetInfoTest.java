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
package io.flamingock.template.mongodb.model;

import io.flamingock.api.template.TemplatePayloadInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MongoOperationGetInfoTest {

    private static MongoOperation operationWithType(String type) {
        MongoOperation op = new MongoOperation();
        op.setType(type);
        return op;
    }

    @ParameterizedTest
    @ValueSource(strings = {"insert", "update", "delete"})
    @DisplayName("Transactional types should report supportsTransactions=true")
    void transactionalTypes(String type) {
        TemplatePayloadInfo info = operationWithType(type).getInfo();
        assertEquals(Optional.of(true), info.getSupportsTransactions());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "createCollection", "dropCollection", "renameCollection", "modifyCollection",
            "createIndex", "dropIndex", "createView", "dropView"
    })
    @DisplayName("Non-transactional types should report supportsTransactions=false")
    void nonTransactionalTypes(String type) {
        TemplatePayloadInfo info = operationWithType(type).getInfo();
        assertEquals(Optional.of(false), info.getSupportsTransactions());
    }

    @Test
    @DisplayName("Null type should leave supportsTransactions empty")
    void nullType() {
        TemplatePayloadInfo info = operationWithType(null).getInfo();
        assertEquals(Optional.empty(), info.getSupportsTransactions());
    }

    @Test
    @DisplayName("Invalid type should leave supportsTransactions empty")
    void invalidType() {
        TemplatePayloadInfo info = operationWithType("banana").getInfo();
        assertEquals(Optional.empty(), info.getSupportsTransactions());
    }

    @Test
    @DisplayName("getInfo() never returns null")
    void infoNeverNull() {
        assertNotNull(operationWithType(null).getInfo());
        assertNotNull(operationWithType("insert").getInfo());
        assertNotNull(operationWithType("banana").getInfo());
    }
}
