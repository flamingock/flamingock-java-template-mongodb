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

public class InsertParametersValidator implements OperationValidator {

    private static final Set<String> RECOGNIZED_KEYS = new HashSet<>(Arrays.asList("documents", "options"));

    @Override
    public List<TemplatePayloadValidationError> validate(MongoOperation operation) {
        List<TemplatePayloadValidationError> errors = new ArrayList<>();
        Map<String, Object> params = operation.getParameters();

        if (params == null) {
            errors.add(new TemplatePayloadValidationError("parameters",
                    "Insert operation requires 'parameters' with 'documents'"));
            return errors;
        }

        Object docs = params.get("documents");
        if (docs == null) {
            errors.add(new TemplatePayloadValidationError("parameters.documents",
                    "Insert operation requires 'documents' parameter"));
            return errors;
        }

        if (!(docs instanceof List)) {
            errors.add(new TemplatePayloadValidationError("parameters.documents",
                    "'documents' must be a list"));
            return errors;
        }

        List<?> docList = (List<?>) docs;
        if (docList.isEmpty()) {
            errors.add(new TemplatePayloadValidationError("parameters.documents",
                    "'documents' cannot be empty"));
        }

        for (int i = 0; i < docList.size(); i++) {
            if (docList.get(i) == null) {
                errors.add(new TemplatePayloadValidationError("parameters.documents[" + i + "]",
                        "Document at index " + i + " is null"));
            }
        }

        Object options = params.get("options");
        if (options != null && !(options instanceof Map)) {
            errors.add(new TemplatePayloadValidationError("parameters.options",
                    "'options' must be a document"));
        }

        errors.addAll(OperationValidator.checkUnrecognizedKeys(params, RECOGNIZED_KEYS, "Insert"));

        return errors;
    }
}
