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
package io.flamingock.template.mongodb.model.operator;

import com.mongodb.MongoCommandException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateViewOptions;
import io.flamingock.template.mongodb.mapper.CreateViewOptionsMapper;
import io.flamingock.template.mongodb.model.MongoOperation;
import org.bson.Document;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CreateViewOperator extends MongoOperator {

    public CreateViewOperator(MongoDatabase mongoDatabase, MongoOperation operation) {
        super(mongoDatabase, operation, false);
    }

    @Override
    protected void applyInternal(ClientSession clientSession) {
        try {
            CreateViewOptions options = CreateViewOptionsMapper.map(op.getOptions());
            mongoDatabase.createView(op.getCollection(), getViewOn(), getPipeline(), options);
        } catch (MongoCommandException e) {
            if (e.getErrorCode() == 48) { // NamespaceExists
                logger.info("View '{}' already exists, skipping createView", op.getCollection());
                return;
            }
            throw e;
        }
    }

    private String getViewOn() {
        return (String) op.getParameters().get("viewOn");
    }

    @SuppressWarnings("unchecked")
    private List<Document> getPipeline() {
        Object value = op.getParameters().get("pipeline");
        if (!(value instanceof List)) {
            throw new IllegalStateException(
                    "getPipeline() called but 'pipeline' is not a List — validate() must run before operator execution");
        }
        List<Map<String, Object>> rawPipeline = (List<Map<String, Object>>) value;
        return rawPipeline.stream().map(Document::new).collect(Collectors.toList());
    }
}
