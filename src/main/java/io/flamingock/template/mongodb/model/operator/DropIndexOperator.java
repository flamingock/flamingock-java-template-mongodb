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
import com.mongodb.client.MongoDatabase;
import io.flamingock.template.mongodb.model.MongoOperation;

public class DropIndexOperator extends MongoOperator {

    public DropIndexOperator(MongoDatabase mongoDatabase, MongoOperation operation) {
        super(mongoDatabase, operation, false);
    }

    @Override
    protected void applyInternal(ClientSession clientSession) {
        String indexName = getIndexName();
        if (indexName != null) {
            if (!DatabaseInspector.indexExistsByName(mongoDatabase, op.getCollection(), indexName)) {
                logger.info("Index '{}' does not exist on collection '{}', skipping dropIndex", indexName, op.getCollection());
                return;
            }
            mongoDatabase.getCollection(op.getCollection()).dropIndex(indexName);
        } else {
            if (!DatabaseInspector.indexExistsByKeys(mongoDatabase, op.getCollection(), op.getKeys())) {
                logger.info("Index with specified keys does not exist on collection '{}', skipping dropIndex", op.getCollection());
                return;
            }
            mongoDatabase.getCollection(op.getCollection()).dropIndex(op.getKeys());
        }
    }

    private String getIndexName() {
        Object value = op.getParameters().get("indexName");
        return value != null ? (String) value : null;
    }
}
