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

public class CreateViewParametersValidator implements OperationValidator {

    private static final Set<String> RECOGNIZED_KEYS = new HashSet<>(Arrays.asList("viewOn", "pipeline", "options"));

    @Override
    public List<TemplatePayloadValidationError> validate(MongoOperation operation) {
        List<TemplatePayloadValidationError> errors = new ArrayList<>();
        Map<String, Object> params = operation.getParameters();

        if (params == null) {
            errors.add(new TemplatePayloadValidationError("parameters",
                    "CreateView operation requires 'parameters' with 'viewOn' and 'pipeline'"));
            return errors;
        }

        Object viewOn = params.get("viewOn");
        if (viewOn == null || (viewOn instanceof String && ((String) viewOn).trim().isEmpty())) {
            errors.add(new TemplatePayloadValidationError("parameters.viewOn",
                    "CreateView operation requires 'viewOn' parameter"));
        }

        Object pipeline = params.get("pipeline");
        if (pipeline == null) {
            errors.add(new TemplatePayloadValidationError("parameters.pipeline",
                    "CreateView operation requires 'pipeline' parameter"));
        } else if (!(pipeline instanceof List)) {
            errors.add(new TemplatePayloadValidationError("parameters.pipeline",
                    "'pipeline' must be a list"));
        }

        Object options = params.get("options");
        if (options != null && !(options instanceof Map)) {
            errors.add(new TemplatePayloadValidationError("parameters.options",
                    "'options' must be a document"));
        }

        errors.addAll(OperationValidator.checkUnrecognizedKeys(params, RECOGNIZED_KEYS, "CreateView"));

        return errors;
    }
}
