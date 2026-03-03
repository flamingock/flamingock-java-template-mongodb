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

import com.mongodb.client.ClientSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for transactional operator tests. Extends {@link AbstractMongoOperatorTest}
 * with session lifecycle management: starts a session and transaction before each test,
 * and aborts any active transaction + closes the session after each test.
 *
 * <p>TestContainers 2.x with {@code mongo:6} starts a single-node replica set by default,
 * so transactions work without additional container configuration.</p>
 */
abstract class AbstractTransactionalOperatorTest extends AbstractMongoOperatorTest {

    protected ClientSession clientSession;

    @BeforeEach
    void setupSession() {
        clientSession = mongoClient.startSession();
        clientSession.startTransaction();
    }

    @AfterEach
    void teardownSession() {
        if (clientSession != null) {
            if (clientSession.hasActiveTransaction()) {
                clientSession.abortTransaction();
            }
            clientSession.close();
        }
    }
}
