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

import com.mongodb.client.MongoDatabase;
import io.flamingock.api.annotations.NonLockGuarded;
import io.flamingock.api.NonLockGuardedType;
import io.flamingock.api.template.TemplatePayload;
import io.flamingock.api.template.TemplatePayloadValidationError;
import io.flamingock.api.template.TemplateValidationContext;
import io.flamingock.template.mongodb.model.operator.MongoOperator;
import io.flamingock.template.mongodb.validation.CollectionValidator;
import io.flamingock.template.mongodb.validation.TypeValidator;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@NonLockGuarded(NonLockGuardedType.NONE)
public class MongoOperation implements TemplatePayload {

    private static final Logger logger = LoggerFactory.getLogger(MongoOperation.class);

    private String type;
    private String collection;
    private Map<String, Object> parameters;

    public String getType() { return type; }

    public String getCollection() { return collection; }

    public Map<String, Object> getParameters() { return parameters; }

    public void setType(String type) { this.type = type; }

    public void setCollection(String collection) { this.collection = collection; }

    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }

    @SuppressWarnings("unchecked")
    public Document getKeys() {
        return new Document((Map<String, Object>) parameters.get("keys"));
    }

    @SuppressWarnings("unchecked")
    public Document getOptions() {
        return parameters.containsKey("options")
                ? new Document((Map<String, Object>) parameters.get("options"))
                : new Document();
    }

    @SuppressWarnings("unchecked")
    public Document getFilter() {
        return new Document((Map<String, Object>) parameters.get("filter"));
    }

    public boolean isMulti() {
        Object multi = parameters.get("multi");
        return multi != null && (Boolean) multi;
    }

    public MongoOperator getOperator(MongoDatabase db) {
        return MongoOperationType.findByTypeOrThrow(getType()).getOperator(db, this);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MongoOperation{");
        sb.append("type='").append(type).append('\'');
        sb.append(", collection='").append(collection).append('\'');
        sb.append(", parameters=").append(parameters);
        sb.append('}');
        return sb.toString();
    }

    private static final TypeValidator TYPE_VALIDATOR = new TypeValidator();
    private static final CollectionValidator COLLECTION_VALIDATOR = new CollectionValidator();

    @Override
    public List<TemplatePayloadValidationError> validate(TemplateValidationContext context) {

        List<TemplatePayloadValidationError> typeErrors = TYPE_VALIDATOR.validate(this);
        List<TemplatePayloadValidationError> errors = new ArrayList<>(typeErrors);
        if (!typeErrors.isEmpty()) {
            return errors;
        }

        //we know it won't throw any exception because we validate the type through TYPE_VALIDATOR
        MongoOperationType operationType = MongoOperationType.findByTypeOrThrow(type);

        errors.addAll(COLLECTION_VALIDATOR.validate(this));
        errors.addAll(operationType.getOperationValidator().validate(this));

        if (!operationType.isTransactional() && context.isTransactional()) {
            errors.add(new TemplatePayloadValidationError("type",
                    "Operation type '" + type + "' does not support transactions. "
                            + "Transactional changes require all operations to be transactional "
                            + "(insert, update, delete)."));
        }
        if (operationType.isTransactional() && !context.isTransactional()) {
            logger.warn("Operation '{}' on collection '{}' supports transactions but the change "
                    + "is not marked as transactional. Consider setting transactional: true "
                    + "for native rollback on failure.", type, collection);
        }

        return errors;
    }
}