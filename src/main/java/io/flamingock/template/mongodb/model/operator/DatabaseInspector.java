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

import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Package-private utility for inspecting MongoDB state before operator execution.
 * Used by DDL operators to implement idempotent pre-checks.
 */
final class DatabaseInspector {

    private DatabaseInspector() {
    }

    static boolean indexExistsByName(MongoDatabase database, String collection, String indexName) {
        List<Document> indexes = database.getCollection(collection)
                .listIndexes()
                .into(new ArrayList<>());
        return indexes.stream().anyMatch(idx -> indexName.equals(idx.getString("name")));
    }

    static boolean indexExistsByKeys(MongoDatabase database, String collection, Document keys) {
        List<Document> indexes = database.getCollection(collection)
                .listIndexes()
                .into(new ArrayList<>());
        return indexes.stream().anyMatch(idx -> {
            Document idxKeys = idx.get("key", Document.class);
            return idxKeys != null && idxKeys.equals(keys);
        });
    }
}
