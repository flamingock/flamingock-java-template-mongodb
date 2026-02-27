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
package io.flamingock.template.mongodb.mapper;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import java.util.List;
import java.util.Map;

public final class BsonConverter {
    private BsonConverter() {}

    public static BsonDocument toBsonDocument(Map<String, Object> map) {
        BsonDocument document = new BsonDocument();
        map.forEach((key, value) -> document.append(key, toBsonValue(value)));
        return document;
    }

    @SuppressWarnings("unchecked")
    public static BsonValue toBsonValue(Object value) {
        if (value == null) {
            return org.bson.BsonNull.VALUE;
        } else if (value instanceof String) {
            return new org.bson.BsonString((String) value);
        } else if (value instanceof Integer) {
            return new org.bson.BsonInt32((Integer) value);
        } else if (value instanceof Long) {
            return new org.bson.BsonInt64((Long) value);
        } else if (value instanceof Double) {
            return new org.bson.BsonDouble((Double) value);
        } else if (value instanceof Boolean) {
            return new org.bson.BsonBoolean((Boolean) value);
        } else if (value instanceof List) {
            return toBsonArray((List<?>) value);
        } else if (value instanceof Map) {
            return toBsonDocument((Map<String, Object>) value);
        }
        throw new IllegalArgumentException("Unsupported BSON type: " + value.getClass().getSimpleName());
    }

    public static BsonArray toBsonArray(List<?> list) {
        BsonArray array = new BsonArray();
        for (Object item : list) {
            array.add(toBsonValue(item));
        }
        return array;
    }
}
