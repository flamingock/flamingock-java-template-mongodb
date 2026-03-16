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
import io.flamingock.template.mongodb.mapper.UpdateOptionsMapper;
import io.flamingock.template.mongodb.model.MongoOperation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UpdateParametersValidator implements OperationValidator {

    private static final Set<String> RECOGNIZED_KEYS = new HashSet<>(Arrays.asList("filter", "update", "multi", "options"));

    @Override
    public List<TemplatePayloadValidationError> validate(MongoOperation operation) {
        List<TemplatePayloadValidationError> errors = new ArrayList<>();
        Map<String, Object> params = operation.getParameters();

        if (params == null) {
            errors.add(new TemplatePayloadValidationError("parameters",
                    "Update operation requires 'parameters' with 'filter' and 'update'"));
            return errors;
        }

        Object filter = params.get("filter");
        if (filter == null) {
            errors.add(new TemplatePayloadValidationError("parameters.filter",
                    "Update operation requires 'filter' parameter"));
        } else if (!(filter instanceof Map)) {
            errors.add(new TemplatePayloadValidationError("parameters.filter",
                    "'filter' must be a document"));
        }

        Object update = params.get("update");
        if (update == null) {
            errors.add(new TemplatePayloadValidationError("parameters.update",
                    "Update operation requires 'update' parameter"));
        } else if (!(update instanceof Map)) {
            errors.add(new TemplatePayloadValidationError("parameters.update",
                    "'update' must be a document"));
        }

        Object multi = params.get("multi");
        if (multi != null && !(multi instanceof Boolean)) {
            errors.add(new TemplatePayloadValidationError("parameters.multi",
                    "'multi' must be a boolean"));
        }

        Object options = params.get("options");
        if (options != null && !(options instanceof Map)) {
            errors.add(new TemplatePayloadValidationError("parameters.options",
                    "'options' must be a document"));
        } else if (options instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> optionsMap = (Map<String, Object>) options;
            errors.addAll(OperationValidator.checkNullOptionValues(optionsMap, "Update"));
            errors.addAll(OperationValidator.checkUnrecognizedOptionKeys(
                    optionsMap, UpdateOptionsMapper.RECOGNIZED_KEYS, "Update"));
        }

        errors.addAll(OperationValidator.checkUnrecognizedKeys(params, RECOGNIZED_KEYS, "Update"));

        return errors;
    }
}
