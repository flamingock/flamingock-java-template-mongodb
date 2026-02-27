/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.template.mongodb.validation;

import io.flamingock.api.template.TemplatePayloadValidationError;
import io.flamingock.template.mongodb.model.MongoOperation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModifyCollectionParametersValidator implements OperationValidator {

    private static final Set<String> VALID_VALIDATION_LEVELS = new HashSet<>(Arrays.asList("off", "strict", "moderate"));
    private static final Set<String> VALID_VALIDATION_ACTIONS = new HashSet<>(Arrays.asList("error", "warn"));
    private static final Set<String> RECOGNIZED_KEYS = new HashSet<>(Arrays.asList("validator", "validationLevel", "validationAction"));

    @Override
    public List<TemplatePayloadValidationError> validate(MongoOperation operation) {
        List<TemplatePayloadValidationError> errors = new ArrayList<>();
        Map<String, Object> params = operation.getParameters();

        if (params == null || !containsAnyRecognizedKey(params)) {
            errors.add(new TemplatePayloadValidationError("parameters",
                    "ModifyCollection operation requires 'parameters' with at least one of: 'validator', 'validationLevel', 'validationAction'"));
            return errors;
        }

        Object validator = params.get("validator");
        if (validator != null && !(validator instanceof Map)) {
            errors.add(new TemplatePayloadValidationError("parameters.validator",
                    "'validator' must be a document (map)"));
        }

        Object validationLevel = params.get("validationLevel");
        if (validationLevel != null && (!(validationLevel instanceof String) || !VALID_VALIDATION_LEVELS.contains((String) validationLevel))) {
            errors.add(new TemplatePayloadValidationError("parameters.validationLevel",
                    "'validationLevel' must be one of: 'off', 'strict', 'moderate'"));
        }

        Object validationAction = params.get("validationAction");
        if (validationAction != null && (!(validationAction instanceof String) || !VALID_VALIDATION_ACTIONS.contains((String) validationAction))) {
            errors.add(new TemplatePayloadValidationError("parameters.validationAction",
                    "'validationAction' must be one of: 'error', 'warn'"));
        }

        errors.addAll(OperationValidator.checkUnrecognizedKeys(params, RECOGNIZED_KEYS, "ModifyCollection"));

        return errors;
    }

    private boolean containsAnyRecognizedKey(Map<String, Object> params) {
        for (String key : RECOGNIZED_KEYS) {
            if (params.containsKey(key)) {
                return true;
            }
        }
        return false;
    }
}
