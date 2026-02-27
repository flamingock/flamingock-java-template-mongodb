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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@FunctionalInterface
public interface OperationValidator {

    OperationValidator NO_OP = operation -> Collections.emptyList();

    List<TemplatePayloadValidationError> validate(MongoOperation operation);

    static List<TemplatePayloadValidationError> checkUnrecognizedKeys(
            Map<String, Object> params, Set<String> recognizedKeys, String operationName) {
        List<TemplatePayloadValidationError> errors = new ArrayList<>();
        if (params == null) {
            return errors;
        }
        for (String key : params.keySet()) {
            if (!recognizedKeys.contains(key)) {
                errors.add(new TemplatePayloadValidationError("parameters." + key,
                        operationName + " operation does not recognize parameter '" + key + "'"));
            }
        }
        return errors;
    }
}
