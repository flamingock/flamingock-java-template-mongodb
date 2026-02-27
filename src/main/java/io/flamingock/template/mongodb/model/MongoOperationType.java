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
import io.flamingock.template.mongodb.model.operator.CreateCollectionOperator;
import io.flamingock.template.mongodb.model.operator.CreateIndexOperator;
import io.flamingock.template.mongodb.model.operator.CreateViewOperator;
import io.flamingock.template.mongodb.model.operator.DeleteOperator;
import io.flamingock.template.mongodb.model.operator.DropCollectionOperator;
import io.flamingock.template.mongodb.model.operator.DropIndexOperator;
import io.flamingock.template.mongodb.model.operator.DropViewOperator;
import io.flamingock.template.mongodb.model.operator.InsertOperator;
import io.flamingock.template.mongodb.model.operator.ModifyCollectionOperator;
import io.flamingock.template.mongodb.model.operator.MongoOperator;
import io.flamingock.template.mongodb.model.operator.RenameCollectionOperator;
import io.flamingock.template.mongodb.model.operator.UpdateOperator;
import io.flamingock.template.mongodb.validation.CreateIndexParametersValidator;
import io.flamingock.template.mongodb.validation.CreateViewParametersValidator;
import io.flamingock.template.mongodb.validation.DeleteParametersValidator;
import io.flamingock.template.mongodb.validation.DropIndexParametersValidator;
import io.flamingock.template.mongodb.validation.InsertParametersValidator;
import io.flamingock.template.mongodb.validation.ModifyCollectionParametersValidator;
import io.flamingock.template.mongodb.validation.OperationValidator;
import io.flamingock.template.mongodb.validation.RenameCollectionParametersValidator;
import io.flamingock.template.mongodb.validation.UpdateParametersValidator;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiFunction;

public enum MongoOperationType {

    CREATE_COLLECTION("createCollection", CreateCollectionOperator::new, OperationValidator.NO_OP),
    CREATE_INDEX("createIndex", CreateIndexOperator::new, new CreateIndexParametersValidator()),
    INSERT("insert", InsertOperator::new, new InsertParametersValidator()),
    UPDATE("update", UpdateOperator::new, new UpdateParametersValidator()),
    DELETE("delete", DeleteOperator::new, new DeleteParametersValidator()),
    DROP_COLLECTION("dropCollection", DropCollectionOperator::new, OperationValidator.NO_OP),
    DROP_INDEX("dropIndex", DropIndexOperator::new, new DropIndexParametersValidator()),
    RENAME_COLLECTION("renameCollection", RenameCollectionOperator::new, new RenameCollectionParametersValidator()),
    MODIFY_COLLECTION("modifyCollection", ModifyCollectionOperator::new, new ModifyCollectionParametersValidator()),
    CREATE_VIEW("createView", CreateViewOperator::new, new CreateViewParametersValidator()),
    DROP_VIEW("dropView", DropViewOperator::new, OperationValidator.NO_OP);

    private final String value;
    private final BiFunction<MongoDatabase, MongoOperation, MongoOperator> createOperatorFunction;
    private final OperationValidator operationValidator;

    MongoOperationType(String value,
                       BiFunction<MongoDatabase, MongoOperation, MongoOperator> createOperatorFunction,
                       OperationValidator operationValidator) {
        this.value = value;
        this.createOperatorFunction = createOperatorFunction;
        this.operationValidator = operationValidator;
    }

    public static MongoOperationType findByTypeOrThrow(String typeValue) {
        return Arrays.stream(MongoOperationType.values())
                .filter(type -> type.matches(typeValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("MongoOperation not supported: " + typeValue));
    }

    public static Optional<MongoOperationType> findByType(String typeValue) {
        return Arrays.stream(MongoOperationType.values())
                .filter(type -> type.matches(typeValue))
                .findFirst();
    }

    public MongoOperator getOperator(MongoDatabase mongoDatabase, MongoOperation operation) {
        return createOperatorFunction.apply(mongoDatabase, operation);
    }

    public OperationValidator getOperationValidator() {
        return operationValidator;
    }

    private boolean matches(String operationType) {
        return this.value.equals(operationType);
    }
}
