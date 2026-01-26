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

import io.flamingock.api.NonLockGuardedType;
import io.flamingock.api.annotations.NonLockGuarded;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wrapper for a list of MongoDB steps in the step-based change template format.
 *
 * <p>This payload represents the new step-based YAML structure where each step
 * contains an apply operation and an optional rollback operation.</p>
 *
 * <h2>YAML Structure</h2>
 * <pre>{@code
 * id: my-change
 * template: MongoChangeTemplate
 * targetSystem:
 *   id: mongodb
 * steps:
 *   - apply:
 *       type: createCollection
 *       collection: users
 *     rollback:
 *       type: dropCollection
 *       collection: users
 * }</pre>
 *
 * @see MongoStep
 */
@NonLockGuarded(NonLockGuardedType.NONE)
public class MongoStepsPayload {

    private List<MongoStep> steps;

    public MongoStepsPayload() {
        this.steps = new ArrayList<>();
    }

    public MongoStepsPayload(List<MongoStep> steps) {
        this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
    }

    /**
     * Returns the list of steps in this payload.
     *
     * @return the list of steps (never null)
     */
    public List<MongoStep> getSteps() {
        return steps;
    }

    /**
     * Sets the list of steps for this payload.
     *
     * @param steps the list of steps
     */
    public void setSteps(List<MongoStep> steps) {
        this.steps = steps != null ? steps : new ArrayList<>();
    }

    /**
     * Returns the number of steps in this payload.
     *
     * @return the number of steps
     */
    public int size() {
        return steps.size();
    }

    /**
     * Checks if this payload has no steps.
     *
     * @return true if there are no steps
     */
    public boolean isEmpty() {
        return steps.isEmpty();
    }

    /**
     * Returns an unmodifiable view of the steps list.
     *
     * @return unmodifiable list of steps
     */
    public List<MongoStep> getStepsUnmodifiable() {
        return Collections.unmodifiableList(steps);
    }

    @Override
    public String toString() {
        return "MongoStepsPayload{" +
                "steps=" + steps +
                '}';
    }
}
