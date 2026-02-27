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

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import io.flamingock.template.mongodb.mapper.UpdateOptionsMapper;
import io.flamingock.template.mongodb.model.MongoOperation;
import org.bson.Document;

import java.util.Map;

public class UpdateOperator extends MongoOperator {

    public UpdateOperator(MongoDatabase mongoDatabase, MongoOperation operation) {
        super(mongoDatabase, operation, true);
    }

    @Override
    protected void applyInternal(ClientSession clientSession) {
        MongoCollection<Document> collection = mongoDatabase.getCollection(op.getCollection());
        Document filter = op.getFilter();
        Document update = getUpdate();
        boolean multi = op.isMulti();

        if (multi) {
            updateMany(clientSession, collection, filter, update);
        } else {
            updateOne(clientSession, collection, filter, update);
        }
    }

    private void updateOne(ClientSession clientSession, MongoCollection<Document> collection,
                           Document filter, Document update) {
        UpdateOptions options = op.getOptions().isEmpty()
                ? null : UpdateOptionsMapper.mapToUpdateOptions(op.getOptions());

        if (clientSession != null && options != null) {
            collection.updateOne(clientSession, filter, update, options);
        } else if (clientSession != null) {
            collection.updateOne(clientSession, filter, update);
        } else if (options != null) {
            collection.updateOne(filter, update, options);
        } else {
            collection.updateOne(filter, update);
        }
    }

    private void updateMany(ClientSession clientSession, MongoCollection<Document> collection,
                            Document filter, Document update) {
        UpdateOptions options = op.getOptions().isEmpty()
                ? null : UpdateOptionsMapper.mapToUpdateOptions(op.getOptions());

        if (clientSession != null && options != null) {
            collection.updateMany(clientSession, filter, update, options);
        } else if (clientSession != null) {
            collection.updateMany(clientSession, filter, update);
        } else if (options != null) {
            collection.updateMany(filter, update, options);
        } else {
            collection.updateMany(filter, update);
        }
    }

    @SuppressWarnings("unchecked")
    private Document getUpdate() {
        return new Document((Map<String, Object>) op.getParameters().get("update"));
    }
}
