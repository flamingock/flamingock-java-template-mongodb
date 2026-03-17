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
import io.flamingock.template.mongodb.mapper.IndexOptionsMapper;
import io.flamingock.template.mongodb.model.MongoOperation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.lang.String.format;

public class CreateIndexParametersValidator implements OperationValidator {

    private static final Set<String> RECOGNIZED_KEYS = new HashSet<>(Arrays.asList("keys", "options"));

    /**
     * Union of RECOGNIZED_KEYS and UNSUPPORTED_KEYS from the mapper. Used by the unrecognized-key
     * check so that unsupported keys only generate their specific "not supported" error and do not
     * also trigger the generic "unrecognized option" error.
     */
    private static final Set<String> ALL_KNOWN_OPTION_KEYS;
    static {
        Set<String> combined = new HashSet<>(IndexOptionsMapper.RECOGNIZED_KEYS);
        combined.addAll(IndexOptionsMapper.UNSUPPORTED_KEYS);
        ALL_KNOWN_OPTION_KEYS = Collections.unmodifiableSet(combined);
    }

    @Override
    public List<TemplatePayloadValidationError> validate(MongoOperation operation) {
        List<TemplatePayloadValidationError> errors = new ArrayList<>();
        Map<String, Object> params = operation.getParameters();

        if (params == null) {
            errors.add(new TemplatePayloadValidationError("parameters",
                    "CreateIndex operation requires 'parameters' with 'keys'"));
            return errors;
        }

        Object keys = params.get("keys");
        if (keys == null) {
            errors.add(new TemplatePayloadValidationError("parameters.keys",
                    "CreateIndex operation requires 'keys' parameter"));
            return errors;
        }

        if (!(keys instanceof Map)) {
            errors.add(new TemplatePayloadValidationError("parameters.keys",
                    "'keys' must be a map"));
            return errors;
        }

        if (((Map<?, ?>) keys).isEmpty()) {
            errors.add(new TemplatePayloadValidationError("parameters.keys",
                    "'keys' cannot be empty"));
        }

        Object options = params.get("options");
        if (options != null && !(options instanceof Map)) {
            errors.add(new TemplatePayloadValidationError("parameters.options",
                    "'options' must be a document"));
        } else if (options instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> optionsMap = (Map<String, Object>) options;
            for (String unsupported : IndexOptionsMapper.UNSUPPORTED_KEYS) {
                if (optionsMap.containsKey(unsupported)) {
                    errors.add(new TemplatePayloadValidationError(
                            "parameters.options." + unsupported,
                            format("'%s' is not supported — removed from the MongoDB Java driver 4.x+", unsupported)));
                }
            }
            errors.addAll(OperationValidator.checkNullOptionValues(optionsMap, "CreateIndex"));
            errors.addAll(OperationValidator.checkUnrecognizedOptionKeys(
                    optionsMap, ALL_KNOWN_OPTION_KEYS, "CreateIndex"));
        }

        errors.addAll(OperationValidator.checkUnrecognizedKeys(params, RECOGNIZED_KEYS, "CreateIndex"));

        return errors;
    }
}
