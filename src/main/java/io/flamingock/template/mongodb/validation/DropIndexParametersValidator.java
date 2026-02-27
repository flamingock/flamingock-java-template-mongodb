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
import java.util.List;
import java.util.Map;

public class DropIndexParametersValidator implements OperationValidator {

    @Override
    public List<TemplatePayloadValidationError> validate(MongoOperation operation) {
        List<TemplatePayloadValidationError> errors = new ArrayList<>();
        Map<String, Object> params = operation.getParameters();

        if (params == null) {
            errors.add(new TemplatePayloadValidationError("parameters",
                    "DropIndex operation requires 'parameters' with 'indexName' or 'keys'"));
            return errors;
        }

        Object indexName = params.get("indexName");
        Object keys = params.get("keys");

        if (indexName == null && keys == null) {
            errors.add(new TemplatePayloadValidationError("parameters",
                    "DropIndex operation requires either 'indexName' or 'keys' parameter"));
        }

        if (indexName != null && !(indexName instanceof String)) {
            errors.add(new TemplatePayloadValidationError("parameters.indexName",
                    "'indexName' must be a string"));
        }

        if (keys != null && !(keys instanceof Map)) {
            errors.add(new TemplatePayloadValidationError("parameters.keys",
                    "'keys' must be a map"));
        }

        return errors;
    }
}
