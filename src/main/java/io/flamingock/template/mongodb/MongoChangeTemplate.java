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
import io.flamingock.api.template.AbstractChangeTemplate;
import io.flamingock.template.mongodb.exception.MongoStepExecutionException;
import io.flamingock.template.mongodb.model.MongoOperation;
import io.flamingock.template.mongodb.model.MongoStep;
import io.flamingock.template.mongodb.validation.MongoOperationValidator;
import io.flamingock.template.mongodb.validation.MongoTemplateValidationException;
import io.flamingock.template.mongodb.validation.ValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * MongoDB Change Template for executing declarative MongoDB operations defined in YAML.
 *
 * <h2>Supported YAML Structures</h2>
 *
 * <h3>Step-Based Format (Recommended)</h3>
 * <p>Each step contains an apply operation and optional rollback operation.
 * When a step fails, all previously successful steps are rolled back in reverse order.</p>
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
 * <h3>Legacy Format</h3>
 * <p>Separate apply and rollback operation lists. The framework handles rollback invocation on failure.</p>
 * <pre>{@code
 * id: create-orders-collection
 * transactional: true
 * template: MongoChangeTemplate
 * targetSystem:
 *   id: "mongodb"
 * apply:
 *   - type: createCollection
 *     collection: orders
 *   - type: insert
 *     collection: orders
 *     parameters:
 *       documents:
 *         - orderId: "ORD-001"
 * rollback:
 *   - type: delete
 *     collection: orders
 *     parameters:
 *       filter: {}
 *   - type: dropCollection
 *     collection: orders
 * }</pre>
 *
 * <h2>Execution Behavior</h2>
 * <ul>
 *   <li>Step-based format: Each step's apply operation executes sequentially. On failure,
 *       rollback operations for completed steps execute in reverse order.</li>
 *   <li>Legacy format: Apply operations execute sequentially in order.
 *       Rollback operations execute in the order defined by the user.</li>
 *   <li>In transactional mode, MongoDB transaction provides atomicity.</li>
 * </ul>
 *
 * @see MongoOperation
 * @see MongoStep
 */
/*
 * Backward Compatibility for YAML Formats
 *
 * This template supports three YAML structures:
 * - New step format:   steps: [- apply: {...}, rollback: {...}]
 * - New list format:   apply: [- type: createCollection, - type: insert]
 * - Old single format: apply: {type: createCollection, collection: users}
 *
 * The framework uses getApplyPayloadClass() to deserialize YAML payloads.
 * Due to Java type erasure, List<MongoOperation> becomes List.class, which
 * cannot deserialize the old Map format correctly.
 *
 * Solution: Override getApplyPayloadClass() to return Object.class, allowing
 * the framework to pass raw YAML data (Map or List). The convertRawPayload()
 * method then handles conversion to List<MongoOperation> for both formats.
 *
 * For steps format, the framework populates the stepsPayload field from the
 * 'steps' key in the YAML.
 *
 * The converted operations are cached to avoid repeated conversion.
 */
public class MongoChangeTemplate extends AbstractChangeTemplate<Void, List<MongoOperation>, List<MongoOperation>> {

    private static final Logger log = LoggerFactory.getLogger(MongoChangeTemplate.class);

    /**
     * Steps payload populated by the framework from the 'steps' key in YAML.
     * This field is set via reflection by the Flamingock framework.
     */
    protected List<MongoStep> stepsPayload;

    private List<MongoOperation> convertedApplyOps;
    private List<MongoOperation> convertedRollbackOps;
    private List<MongoStep> convertedSteps;
    private Boolean isStepsFormat;

    public MongoChangeTemplate() {
        super(MongoOperation.class);
    }

    /**
     * Returns Object.class to allow the framework to pass raw YAML data without
     * attempting to deserialize it as List. This enables backward compatibility
     * with the old single-operation YAML format and the new steps format.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Class<List<MongoOperation>> getApplyPayloadClass() {
        return (Class<List<MongoOperation>>) (Class<?>) Object.class;
    }

    /**
     * Returns Object.class to allow the framework to pass raw YAML data without
     * attempting to deserialize it as List. This enables backward compatibility
     * with the old single-operation YAML format.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Class<List<MongoOperation>> getRollbackPayloadClass() {
        return (Class<List<MongoOperation>>) (Class<?>) Object.class;
    }

    /**
     * Sets the steps payload. Called by the framework when 'steps' key is present in YAML.
     * Accepts Object to handle raw YAML data (List of Maps) and converts it to List of MongoStep.
     *
     * @param stepsPayload the steps from the YAML (can be raw List of Maps or List of MongoStep)
     */
    @SuppressWarnings("unchecked")
    public void setStepsPayload(Object stepsPayload) {
        if (stepsPayload == null) {
            this.stepsPayload = null;
            return;
        }

        if (stepsPayload instanceof List) {
            List<?> list = (List<?>) stepsPayload;
            if (list.isEmpty()) {
                this.stepsPayload = new ArrayList<>();
                return;
            }

            Object firstElement = list.get(0);
            if (firstElement instanceof MongoStep) {
                // Already converted
                this.stepsPayload = (List<MongoStep>) stepsPayload;
            } else if (firstElement instanceof Map) {
                // Raw YAML data - convert to MongoStep list
                this.stepsPayload = convertListToSteps(list);
            } else {
                this.stepsPayload = new ArrayList<>();
            }
        } else {
            this.stepsPayload = new ArrayList<>();
        }
    }

    /**
     * Returns the steps payload.
     *
     * @return the steps payload, or null if not using steps format
     */
    public List<MongoStep> getStepsPayload() {
        return stepsPayload;
    }

    @Apply
    public void apply(MongoDatabase db, @Nullable ClientSession clientSession) {
        validateSession(clientSession);

        if (isStepsFormat()) {
            List<MongoStep> steps = getConvertedSteps();
            validateStepsPayload(steps);
            executeStepsWithRollback(db, steps, clientSession);
        } else {
            List<MongoOperation> operations = getConvertedApplyOperations();
            validatePayload(operations, changeId + ".apply");
            executeOperations(db, operations, clientSession);
        }
    }

    @Rollback
    public void rollback(MongoDatabase db, @Nullable ClientSession clientSession) {
        validateSession(clientSession);

        if (isStepsFormat()) {
            // Framework-triggered rollback: rollback all steps in reverse order
            List<MongoStep> steps = getConvertedSteps();
            if (steps != null && !steps.isEmpty()) {
                rollbackSteps(db, steps, clientSession);
            }
        } else {
            // Legacy format: execute rollback operations as before
            List<MongoOperation> operations = getConvertedRollbackOperations();
            validatePayload(operations, changeId + ".rollback");
            executeOperations(db, operations, clientSession);
        }
    }

    /**
     * Determines if the payload uses the step-based format.
     *
     * @return true if using steps format, false for legacy format
     */
    private boolean isStepsFormat() {
        if (isStepsFormat == null) {
            // First check if stepsPayload is directly set (new YAML format with 'steps' key)
            if (stepsPayload != null && !stepsPayload.isEmpty()) {
                isStepsFormat = true;
                return isStepsFormat;
            }

            // Fall back to checking applyPayload for step-format data (backward compatibility)
            Object rawPayload = getRawPayload("applyPayload");
            isStepsFormat = detectStepsFormat(rawPayload);
        }
        return isStepsFormat;
    }

    @SuppressWarnings("unchecked")
    private boolean detectStepsFormat(Object rawPayload) {
        if (rawPayload == null) {
            return false;
        }

        // Check if it's a list where first element has "apply" key (steps format)
        if (rawPayload instanceof List && !((List<?>) rawPayload).isEmpty()) {
            Object firstElement = ((List<?>) rawPayload).get(0);
            if (firstElement instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) firstElement;
                return map.containsKey("apply");
            }
            // Check if it's already a list of MongoStep
            if (firstElement instanceof MongoStep) {
                return true;
            }
        }
        return false;
    }

    private List<MongoStep> getConvertedSteps() {
        if (convertedSteps == null) {
            // First try to get from stepsPayload (new format with 'steps' key)
            if (stepsPayload != null && !stepsPayload.isEmpty()) {
                convertedSteps = stepsPayload;
            } else {
                // Fall back to applyPayload (backward compatibility for step-format in apply)
                convertedSteps = convertToSteps(getRawPayload("applyPayload"));
            }
        }
        return convertedSteps;
    }

    private List<MongoOperation> getConvertedApplyOperations() {
        if (convertedApplyOps == null) {
            convertedApplyOps = convertRawPayload(getRawPayload("applyPayload"));
        }
        return convertedApplyOps;
    }

    private List<MongoOperation> getConvertedRollbackOperations() {
        if (convertedRollbackOps == null) {
            convertedRollbackOps = convertRawPayload(getRawPayload("rollbackPayload"));
        }
        return convertedRollbackOps;
    }

    /**
     * Accesses a payload field via reflection to avoid Java's checkcast instruction.
     * Since we override getApplyPayloadClass() to return Object.class, the actual
     * runtime value may be a Map (old format) or List (new format), not List<MongoOperation>.
     */
    private Object getRawPayload(String fieldName) {
        try {
            java.lang.reflect.Field field = findField(getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                return field.get(this);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    private void validateSession(ClientSession clientSession) {
        if (this.isTransactional && clientSession == null) {
            throw new IllegalArgumentException(
                    String.format("Transactional change[%s] requires transactional ecosystem with ClientSession", changeId));
        }
    }

    private void validatePayload(List<MongoOperation> operations, String entityId) {
        if (operations == null || operations.isEmpty()) {
            return;
        }

        List<ValidationError> errors = new ArrayList<>();
        for (int i = 0; i < operations.size(); i++) {
            String opId = entityId + "[" + i + "]";
            errors.addAll(MongoOperationValidator.validate(operations.get(i), opId));
        }

        if (!errors.isEmpty()) {
            throw new MongoTemplateValidationException(errors);
        }
    }

    private void validateStepsPayload(List<MongoStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return;
        }

        List<ValidationError> errors = MongoOperationValidator.validateSteps(steps, changeId);
        if (!errors.isEmpty()) {
            throw new MongoTemplateValidationException(errors);
        }
    }

    private void executeOperations(MongoDatabase db, List<MongoOperation> operations, ClientSession clientSession) {
        if (operations == null || operations.isEmpty()) {
            return;
        }

        for (MongoOperation op : operations) {
            op.getOperator(db).apply(clientSession);
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
    private void executeStepsWithRollback(MongoDatabase db, List<MongoStep> steps, ClientSession clientSession) {
        if (steps == null || steps.isEmpty()) {
            return;
        }

        List<MongoStep> completedSteps = new ArrayList<>();

        for (int i = 0; i < steps.size(); i++) {
            MongoStep step = steps.get(i);
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
                    rollbackSteps(db, completedSteps, clientSession);
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
     * Rolls back steps in reverse order.
     *
     * <p>Steps without rollback operations are skipped. Rollback errors are logged
     * but don't stop the rollback process for remaining steps.</p>
     *
     * @param db            the MongoDB database
     * @param steps         the list of steps to rollback
     * @param clientSession the client session (may be null)
     */
    private void rollbackSteps(MongoDatabase db, List<MongoStep> steps, ClientSession clientSession) {
        if (steps == null || steps.isEmpty()) {
            return;
        }

        List<Exception> rollbackErrors = new ArrayList<>();
        List<MongoStep> reversedSteps = new ArrayList<>(steps);
        Collections.reverse(reversedSteps);

        for (int i = 0; i < reversedSteps.size(); i++) {
            MongoStep step = reversedSteps.get(i);
            int originalStepNumber = steps.size() - i; // Original 1-based step number

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

    @SuppressWarnings("unchecked")
    private List<MongoStep> convertToSteps(Object rawPayload) {
        if (rawPayload == null) {
            return new ArrayList<>();
        }

        // Handle if it's already a list of MongoStep
        if (rawPayload instanceof List && !((List<?>) rawPayload).isEmpty()) {
            Object firstElement = ((List<?>) rawPayload).get(0);
            if (firstElement instanceof MongoStep) {
                return (List<MongoStep>) rawPayload;
            }
        }

        // Handle direct List of step maps
        if (rawPayload instanceof List) {
            List<?> list = (List<?>) rawPayload;
            if (!list.isEmpty()) {
                Object firstElement = list.get(0);
                if (firstElement instanceof Map && ((Map<?, ?>) firstElement).containsKey("apply")) {
                    return convertListToSteps(list);
                }
            }
        }

        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<MongoStep> convertListToSteps(List<?> stepsList) {
        List<MongoStep> steps = new ArrayList<>();

        for (Object item : stepsList) {
            if (item instanceof Map) {
                Map<String, Object> stepMap = (Map<String, Object>) item;
                MongoStep step = new MongoStep();

                Object applyValue = stepMap.get("apply");
                if (applyValue instanceof Map) {
                    step.setApply(convertMapToOperation((Map<String, Object>) applyValue));
                }

                Object rollbackValue = stepMap.get("rollback");
                if (rollbackValue instanceof Map) {
                    step.setRollback(convertMapToOperation((Map<String, Object>) rollbackValue));
                }

                steps.add(step);
            }
        }

        return steps;
    }

    @SuppressWarnings("unchecked")
    private List<MongoOperation> convertRawPayload(Object rawPayload) {
        if (rawPayload == null) {
            return null;
        }

        // Handle single MongoOperation (already converted)
        if (rawPayload instanceof MongoOperation) {
            List<MongoOperation> operations = new ArrayList<>();
            operations.add((MongoOperation) rawPayload);
            return operations;
        }

        // Handle single Map (backward compatibility)
        if (rawPayload instanceof Map && !(rawPayload instanceof Collection)) {
            Map<String, Object> map = (Map<String, Object>) rawPayload;
            // Check if it looks like an operation (has 'type' field)
            if (map.containsKey("type")) {
                List<MongoOperation> operations = new ArrayList<>();
                operations.add(convertMapToOperation(map));
                return operations;
            }
        }

        // Handle Collection types
        if (rawPayload instanceof Collection) {
            Collection<?> rawCollection = (Collection<?>) rawPayload;
            if (rawCollection.isEmpty()) {
                return new ArrayList<>();
            }

            // Check if already properly deserialized
            Object firstElement = rawCollection.iterator().next();
            if (firstElement instanceof MongoOperation) {
                // If it's already a List<MongoOperation>, return as-is
                if (rawPayload instanceof List) {
                    return (List<MongoOperation>) rawPayload;
                }
                // Otherwise convert to List
                return new ArrayList<>((Collection<MongoOperation>) rawCollection);
            }

            // Convert from LinkedHashMap elements
            List<MongoOperation> operations = new ArrayList<>();
            for (Object item : rawCollection) {
                if (item instanceof Map) {
                    operations.add(convertMapToOperation((Map<String, Object>) item));
                }
            }
            return operations;
        }

        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private MongoOperation convertMapToOperation(Map<String, Object> map) {
        MongoOperation op = new MongoOperation();
        op.setType((String) map.get("type"));
        op.setCollection((String) map.get("collection"));

        Object params = map.get("parameters");
        if (params instanceof Map) {
            op.setParameters((Map<String, Object>) params);
        }

        return op;
    }
}
