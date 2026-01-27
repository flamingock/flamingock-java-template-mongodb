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
package io.flamingock.template.mongodb.exception;

import io.flamingock.api.template.TemplateStep;
import io.flamingock.template.mongodb.model.MongoOperation;

import java.util.Collections;
import java.util.List;

/**
 * Exception thrown when a step execution fails during apply or rollback.
 *
 * <p>This exception provides context about which step failed (using 1-based indexing
 * for user-friendly error messages) and which steps were successfully completed
 * before the failure.</p>
 *
 * <h2>Error Information</h2>
 * <ul>
 *   <li><b>stepNumber</b> - The 1-based index of the failed step</li>
 *   <li><b>completedSteps</b> - List of steps that completed successfully before the failure</li>
 *   <li><b>cause</b> - The original exception that caused the failure</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * try {
 *     executeSteps(steps);
 * } catch (MongoStepExecutionException e) {
 *     System.out.println("Step " + e.getStepNumber() + " failed: " + e.getMessage());
 *     System.out.println("Completed " + e.getCompletedSteps().size() + " steps before failure");
 * }
 * }</pre>
 */
public class MongoStepExecutionException extends RuntimeException {

    private final int stepNumber;
    private final List<TemplateStep<MongoOperation, MongoOperation>> completedSteps;

    /**
     * Creates a new step execution exception.
     *
     * @param message        the error message
     * @param stepNumber     the 1-based index of the failed step
     * @param completedSteps the list of steps that completed before the failure
     * @param cause          the original exception
     */
    public MongoStepExecutionException(String message, int stepNumber, List<TemplateStep<MongoOperation, MongoOperation>> completedSteps, Throwable cause) {
        super(formatMessage(message, stepNumber), cause);
        this.stepNumber = stepNumber;
        this.completedSteps = completedSteps != null
                ? Collections.unmodifiableList(completedSteps)
                : Collections.emptyList();
    }

    /**
     * Creates a new step execution exception without a cause.
     *
     * @param message        the error message
     * @param stepNumber     the 1-based index of the failed step
     * @param completedSteps the list of steps that completed before the failure
     */
    public MongoStepExecutionException(String message, int stepNumber, List<TemplateStep<MongoOperation, MongoOperation>> completedSteps) {
        this(message, stepNumber, completedSteps, null);
    }

    /**
     * Returns the 1-based index of the step that failed.
     *
     * @return the step number (1-based)
     */
    public int getStepNumber() {
        return stepNumber;
    }

    /**
     * Returns the list of steps that completed successfully before the failure.
     *
     * @return unmodifiable list of completed steps
     */
    public List<TemplateStep<MongoOperation, MongoOperation>> getCompletedSteps() {
        return completedSteps;
    }

    /**
     * Returns the number of steps that completed before the failure.
     *
     * @return the count of completed steps
     */
    public int getCompletedStepCount() {
        return completedSteps.size();
    }

    private static String formatMessage(String message, int stepNumber) {
        return String.format("Step %d failed: %s", stepNumber, message);
    }
}
