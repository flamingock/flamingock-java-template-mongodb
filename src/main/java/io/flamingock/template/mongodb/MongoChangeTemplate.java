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
import io.flamingock.api.annotations.Nullable;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.template.AbstractSteppableTemplate;
import io.flamingock.api.template.TemplateStep;
import io.flamingock.template.mongodb.exception.MongoStepExecutionException;
import io.flamingock.template.mongodb.model.MongoOperation;
import io.flamingock.template.mongodb.validation.MongoOperationValidator;
import io.flamingock.template.mongodb.validation.MongoTemplateValidationException;
import io.flamingock.template.mongodb.validation.ValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MongoDB Change Template for executing declarative MongoDB operations defined in YAML.
 *
 * <p>This template extends {@link AbstractSteppableTemplate} and is designed for step-based changes
 * where each step can have its own apply and rollback operation.
 *
 * <h2>YAML Structure</h2>
 *
 * <pre>{@code
 * id: create-orders-collection
 * transactional: false
 * template: MongoChangeTemplate
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
 *   <li>Each step's apply operation executes sequentially</li>
 *   <li>On failure, rollback operations for completed steps execute in reverse order</li>
 *   <li>In transactional mode, MongoDB transaction provides atomicity</li>
 * </ul>
 *
 * @see MongoOperation
 * @see TemplateStep
 */
public class MongoChangeTemplate extends AbstractSteppableTemplate<Void, MongoOperation, MongoOperation> {

    private static final Logger log = LoggerFactory.getLogger(MongoChangeTemplate.class);

    public MongoChangeTemplate() {
        super(MongoOperation.class);
    }

    @Apply
    public void apply(MongoDatabase db, @Nullable ClientSession clientSession) {
        validateSession(clientSession);

        if (hasSteps()) {
            validateStepsPayload(steps);
            executeStepsWithRollback(db, steps, clientSession);
        } else {
            log.warn("No steps defined for change[{}]", changeId);
        }
    }

    @Rollback
    public void rollback(MongoDatabase db, @Nullable ClientSession clientSession) {
        validateSession(clientSession);

        if (hasSteps()) {
            rollbackAllSteps(db, steps, clientSession);
        }
    }

    private void validateSession(ClientSession clientSession) {
        if (this.isTransactional && clientSession == null) {
            throw new IllegalArgumentException(
                    String.format("Transactional change[%s] requires transactional ecosystem with ClientSession", changeId));
        }
    }

    private void validateStepsPayload(List<TemplateStep<MongoOperation, MongoOperation>> steps) {
        if (steps == null || steps.isEmpty()) {
            return;
        }

        List<ValidationError> errors = MongoOperationValidator.validateSteps(steps, changeId);
        if (!errors.isEmpty()) {
            throw new MongoTemplateValidationException(errors);
        }
    }

    /**
     * Executes steps with automatic rollback on failure.
     *
     * <p>When a step fails, all previously successful steps are rolled back
     * in reverse order. Rollback errors are logged but don't stop the rollback process.</p>
     *
     * @param db            the MongoDB database
     * @param steps         the list of steps to execute
     * @param clientSession the client session (may be null)
     * @throws MongoStepExecutionException if a step fails
     */
    private void executeStepsWithRollback(MongoDatabase db,
                                          List<TemplateStep<MongoOperation, MongoOperation>> steps,
                                          ClientSession clientSession) {
        if (steps == null || steps.isEmpty()) {
            return;
        }

        List<TemplateStep<MongoOperation, MongoOperation>> completedSteps = new ArrayList<>();

        for (int i = 0; i < steps.size(); i++) {
            TemplateStep<MongoOperation, MongoOperation> step = steps.get(i);
            int stepNumber = i + 1; // 1-based indexing for user-friendly messages

            try {
                log.debug("Executing step {} apply operation", stepNumber);
                step.getApply().getOperator(db).apply(clientSession);
                completedSteps.add(step);
            } catch (Exception e) {
                log.error("Step {} failed: {}", stepNumber, e.getMessage());

                // Rollback completed steps in reverse order
                if (!completedSteps.isEmpty()) {
                    log.info("Rolling back {} completed step(s)", completedSteps.size());
                    rollbackAllSteps(db, completedSteps, clientSession);
                }

                throw new MongoStepExecutionException(
                        e.getMessage(),
                        stepNumber,
                        new ArrayList<>(completedSteps),
                        e
                );
            }
        }
    }

    /**
     * Rolls back all steps in reverse order.
     *
     * <p>Steps without rollback operations are skipped. Rollback errors are logged
     * but don't stop the rollback process for remaining steps.</p>
     *
     * @param db            the MongoDB database
     * @param steps         the list of steps to rollback
     * @param clientSession the client session (may be null)
     */
    private void rollbackAllSteps(MongoDatabase db,
                                  List<TemplateStep<MongoOperation, MongoOperation>> steps,
                                  ClientSession clientSession) {
        if (steps == null || steps.isEmpty()) {
            return;
        }

        List<Exception> rollbackErrors = new ArrayList<>();
        List<TemplateStep<MongoOperation, MongoOperation>> reversedSteps = new ArrayList<>(steps);
        Collections.reverse(reversedSteps);

        for (int i = 0; i < reversedSteps.size(); i++) {
            TemplateStep<MongoOperation, MongoOperation> step = reversedSteps.get(i);
            int originalStepNumber = steps.size() - i;

            if (step.hasRollback()) {
                try {
                    log.debug("Executing rollback for step {}", originalStepNumber);
                    step.getRollback().getOperator(db).apply(clientSession);
                } catch (Exception e) {
                    log.error("Rollback failed for step {}: {}", originalStepNumber, e.getMessage());
                    rollbackErrors.add(e);
                }
            } else {
                log.debug("Step {} has no rollback operation, skipping", originalStepNumber);
            }
        }

        if (!rollbackErrors.isEmpty()) {
            log.warn("{} rollback operation(s) failed", rollbackErrors.size());
        }
    }
}
