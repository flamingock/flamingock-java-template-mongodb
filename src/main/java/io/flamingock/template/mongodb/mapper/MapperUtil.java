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

import com.mongodb.client.model.Collation;
import com.mongodb.client.model.CollationAlternate;
import com.mongodb.client.model.CollationCaseFirst;
import com.mongodb.client.model.CollationMaxVariable;
import com.mongodb.client.model.CollationStrength;
import org.bson.conversions.Bson;

import java.util.Map;

public final class MapperUtil {
    private MapperUtil(){}


    public static Boolean getBoolean(Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else {
            throw new IllegalArgumentException(String.format("field[%s] should be Boolean", key));
        }
    }

    public static String getString(Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (value instanceof String) {
            return (String) value;
        } else {
            throw new IllegalArgumentException(String.format("field[%s] should be String", key));
        }
    }

    public static Integer getInteger(Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else {
            throw new IllegalArgumentException(String.format("field[%s] should be Integer", key));
        }
    }

    public static Long getLong(Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        } else {
            throw new IllegalArgumentException(String.format("field[%s] should be Long", key));
        }
    }

    public static Double getDouble(Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else {
            throw new IllegalArgumentException(String.format("field[%s] should be Double", key));
        }
    }

    public static Bson getBson(Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (value instanceof Bson) {
            return (Bson) value;
        } else if (value instanceof Map) {
            return BsonConverter.toBsonDocument((Map<String, Object>) value);
        } else {
            throw new IllegalArgumentException(String.format("field[%s] should be Bson", key));
        }
    }

    public static Collation getCollation(Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (value instanceof Collation) {
            return (Collation) value;
        } else if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            return buildCollationFromMap(map);
        } else {
            throw new IllegalArgumentException(String.format("field[%s] should be Collation or Map", key));
        }
    }

    private static Collation buildCollationFromMap(Map<String, Object> map) {
        Object locale = map.get("locale");
        if (!(locale instanceof String)) {
            throw new IllegalArgumentException("Collation requires 'locale' as a String");
        }
        Collation.Builder builder = Collation.builder().locale((String) locale);

        if (map.containsKey("caseLevel")) {
            builder.caseLevel(getBoolean(map, "caseLevel"));
        }
        if (map.containsKey("caseFirst")) {
            builder.collationCaseFirst(CollationCaseFirst.fromString(getString(map, "caseFirst")));
        }
        if (map.containsKey("strength")) {
            builder.collationStrength(CollationStrength.fromInt(getInteger(map, "strength")));
        }
        if (map.containsKey("numericOrdering")) {
            builder.numericOrdering(getBoolean(map, "numericOrdering"));
        }
        if (map.containsKey("alternate")) {
            builder.collationAlternate(CollationAlternate.fromString(getString(map, "alternate")));
        }
        if (map.containsKey("maxVariable")) {
            builder.collationMaxVariable(CollationMaxVariable.fromString(getString(map, "maxVariable")));
        }
        if (map.containsKey("normalization")) {
            builder.normalization(getBoolean(map, "normalization"));
        }
        if (map.containsKey("backwards")) {
            builder.backwards(getBoolean(map, "backwards"));
        }
        return builder.build();
    }

}
