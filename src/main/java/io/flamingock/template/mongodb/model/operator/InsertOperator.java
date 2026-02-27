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
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.InsertOneOptions;
import io.flamingock.template.mongodb.mapper.InsertOptionsMapper;
import io.flamingock.template.mongodb.model.MongoOperation;
import org.bson.Document;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InsertOperator extends MongoOperator {

    public InsertOperator(MongoDatabase mongoDatabase, MongoOperation operation) {
        super(mongoDatabase, operation, true);
    }

    /**
     * Executes the insert operation against MongoDB.
     * <p>
     * Precondition: documents are guaranteed non-null and non-empty
     * by {@code InsertParametersValidator} at load time, so no defensive check is needed here.
     */
    @Override
    protected void applyInternal(ClientSession clientSession) {
        MongoCollection<Document> collection = mongoDatabase.getCollection(op.getCollection());

        if (getDocuments().size() == 1) {
            insertOne(clientSession, collection);
        } else {
            insertMany(clientSession, collection);
        }
    }

    private void insertMany(ClientSession clientSession, MongoCollection<Document> collection) {
        List<Document> docs = getDocuments();
        InsertManyOptions options = op.getOptions().isEmpty()
                ? null : InsertOptionsMapper.mapToInsertManyOptions(op.getOptions());

        if (clientSession != null && options != null) {
            collection.insertMany(clientSession, docs, options);
        } else if (clientSession != null) {
            collection.insertMany(clientSession, docs);
        } else if (options != null) {
            collection.insertMany(docs, options);
        } else {
            collection.insertMany(docs);
        }
    }

    private void insertOne(ClientSession clientSession, MongoCollection<Document> collection) {
        Document doc = getDocuments().get(0);
        InsertOneOptions options = op.getOptions().isEmpty()
                ? null : InsertOptionsMapper.mapToInsertOneOptions(op.getOptions());

        if (clientSession != null && options != null) {
            collection.insertOne(clientSession, doc, options);
        } else if (clientSession != null) {
            collection.insertOne(clientSession, doc);
        } else if (options != null) {
            collection.insertOne(doc, options);
        } else {
            collection.insertOne(doc);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Document> getDocuments() {
        return ((List<Map<String, Object>>) op.getParameters().get("documents"))
                .stream().map(Document::new)
                .collect(Collectors.toList());
    }
}
