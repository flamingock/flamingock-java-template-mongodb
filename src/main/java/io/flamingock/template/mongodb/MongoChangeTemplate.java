/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.template.mongodb;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.ChangeTemplate;
import io.flamingock.api.annotations.Nullable;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.template.AbstractChangeTemplate;
import io.flamingock.template.mongodb.model.MongoOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MongoDB Change Template for executing declarative MongoDB operations defined in YAML.
 *
 * <p>This template extends {@link AbstractChangeTemplate} and is annotated with
 * {@code @ChangeTemplate(multiStep = true)} for step-based changes where each step
 * has its own apply and rollback operation. The framework manages step iteration,
 * calling {@code @Apply} and {@code @Rollback} once per step with the appropriate
 * payload set via {@code applyPayload} and {@code rollbackPayload}.
 *
 * <h2>YAML Structure</h2>
 *
 * <pre>{@code
 * id: create-orders-collection
 * transactional: false
 * template: mongodb-sync-template
 * targetSystem:
 *   id: "mongodb"
 * steps:
 *   - apply:
 *       type: createCollection
 *       collection: orders
 *     rollback:
 *       type: dropCollection
 *       collection: orders
 *   - apply:
 *       type: insert
 *       collection: orders
 *       parameters:
 *         documents:
 *           - orderId: "ORD-001"
 *     rollback:
 *       type: delete
 *       collection: orders
 *       parameters:
 *         filter: {}
 * }</pre>
 *
 * <h2>Execution Behavior</h2>
 * <ul>
 *   <li>The framework iterates through steps, calling apply/rollback per step</li>
 *   <li>On failure, the framework rolls back completed steps in reverse order</li>
 *   <li>In transactional mode, MongoDB transaction provides atomicity</li>
 * </ul>
 *
 * @see MongoOperation
 */
@ChangeTemplate( name = "mongodb-sync-template", multiStep = true)
public class MongoChangeTemplate extends AbstractChangeTemplate<Void, MongoOperation, MongoOperation> {

    private static final Logger log = LoggerFactory.getLogger(MongoChangeTemplate.class);

    public MongoChangeTemplate() {
        super(MongoOperation.class);
    }

    @Apply
    public void apply(MongoDatabase db, @Nullable ClientSession clientSession) {
        validateSession(clientSession);
        applyPayload.getOperator(db).apply(clientSession);
    }

    @Rollback
    public void rollback(MongoDatabase db, @Nullable ClientSession clientSession) {
        validateSession(clientSession);
        rollbackPayload.getOperator(db).apply(clientSession);
    }

    private void validateSession(ClientSession clientSession) {
        if (this.isTransactional && clientSession == null) {
            throw new IllegalArgumentException(
                    String.format("Transactional change[%s] requires transactional ecosystem with ClientSession", changeId));
        }
    }
}
