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
package io.flamingock.template.mongodb.validation;

import io.flamingock.api.template.TemplatePayloadValidationError;
import io.flamingock.template.mongodb.model.MongoOperation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DeleteParametersValidator implements OperationValidator {

    private static final Set<String> RECOGNIZED_KEYS = new HashSet<>(Arrays.asList("filter", "multi"));

    @Override
    public List<TemplatePayloadValidationError> validate(MongoOperation operation) {
        List<TemplatePayloadValidationError> errors = new ArrayList<>();
        Map<String, Object> params = operation.getParameters();

        Object filter = params == null ? null : params.get("filter");
        if (filter == null) {
            errors.add(new TemplatePayloadValidationError("parameters.filter",
                    "Delete operation requires 'filter' parameter"));
        } else if (!(filter instanceof Map)) {
            errors.add(new TemplatePayloadValidationError("parameters.filter",
                    "'filter' must be a document"));
        }

        if (params != null) {
            Object multi = params.get("multi");
            if (multi != null && !(multi instanceof Boolean)) {
                errors.add(new TemplatePayloadValidationError("parameters.multi",
                        "'multi' must be a boolean"));
            }

            errors.addAll(OperationValidator.checkUnrecognizedKeys(params, RECOGNIZED_KEYS, "Delete"));
        }

        return errors;
    }
}
