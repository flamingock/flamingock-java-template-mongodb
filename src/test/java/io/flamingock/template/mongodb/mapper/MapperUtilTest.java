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
import org.bson.BsonDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MapperUtilTest {

    @Nested
    @DisplayName("getBoolean Tests")
    class GetBooleanTests {

        @Test
        @DisplayName("WHEN value is Boolean THEN returns value")
        void getBooleanValidTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("flag", true);

            Boolean result = MapperUtil.getBoolean(options, "flag");

            assertTrue(result);
        }

        @Test
        @DisplayName("WHEN value is not Boolean THEN throws IllegalArgumentException")
        void getBooleanInvalidTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("flag", "not a boolean");

            assertThrows(IllegalArgumentException.class, () ->
                    MapperUtil.getBoolean(options, "flag"));
        }
    }

    @Nested
    @DisplayName("getString Tests")
    class GetStringTests {

        @Test
        @DisplayName("WHEN value is String THEN returns value")
        void getStringValidTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("name", "testName");

            String result = MapperUtil.getString(options, "name");

            assertEquals("testName", result);
        }

        @Test
        @DisplayName("WHEN value is not String THEN throws IllegalArgumentException")
        void getStringInvalidTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("name", 123);

            assertThrows(IllegalArgumentException.class, () ->
                    MapperUtil.getString(options, "name"));
        }
    }

    @Nested
    @DisplayName("getInteger Tests")
    class GetIntegerTests {

        @Test
        @DisplayName("WHEN value is Integer THEN returns value")
        void getIntegerValidTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("count", 42);

            Integer result = MapperUtil.getInteger(options, "count");

            assertEquals(42, result);
        }

        @Test
        @DisplayName("WHEN value is Long THEN returns as Integer")
        void getIntegerFromLongTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("count", 42L);

            Integer result = MapperUtil.getInteger(options, "count");

            assertEquals(42, result);
        }

        @Test
        @DisplayName("WHEN value is not Number THEN throws IllegalArgumentException")
        void getIntegerInvalidTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("count", "not a number");

            assertThrows(IllegalArgumentException.class, () ->
                    MapperUtil.getInteger(options, "count"));
        }
    }

    @Nested
    @DisplayName("getLong Tests")
    class GetLongTests {

        @Test
        @DisplayName("WHEN value is Long THEN returns value")
        void getLongValidTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("timestamp", 1234567890L);

            Long result = MapperUtil.getLong(options, "timestamp");

            assertEquals(1234567890L, result);
        }

        @Test
        @DisplayName("WHEN value is Integer THEN returns as Long")
        void getLongFromIntegerTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("timestamp", 42);

            Long result = MapperUtil.getLong(options, "timestamp");

            assertEquals(42L, result);
        }

        @Test
        @DisplayName("WHEN value is not Number THEN throws IllegalArgumentException")
        void getLongInvalidTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("timestamp", "not a number");

            assertThrows(IllegalArgumentException.class, () ->
                    MapperUtil.getLong(options, "timestamp"));
        }
    }

    @Nested
    @DisplayName("getDouble Tests")
    class GetDoubleTests {

        @Test
        @DisplayName("WHEN value is Double THEN returns value")
        void getDoubleValidTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("rate", 3.14);

            Double result = MapperUtil.getDouble(options, "rate");

            assertEquals(3.14, result, 0.001);
        }

        @Test
        @DisplayName("WHEN value is Integer THEN returns as Double")
        void getDoubleFromIntegerTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("rate", 42);

            Double result = MapperUtil.getDouble(options, "rate");

            assertEquals(42.0, result, 0.001);
        }

        @Test
        @DisplayName("WHEN value is not Number THEN throws IllegalArgumentException")
        void getDoubleInvalidTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("rate", "not a number");

            assertThrows(IllegalArgumentException.class, () ->
                    MapperUtil.getDouble(options, "rate"));
        }
    }

    @Nested
    @DisplayName("getBson Tests")
    class GetBsonTests {

        @Test
        @DisplayName("WHEN value is Map THEN returns BsonDocument")
        void getBsonFromMapTest() {
            Map<String, Object> innerMap = new HashMap<>();
            innerMap.put("field", "value");

            Map<String, Object> options = new HashMap<>();
            options.put("doc", innerMap);

            org.bson.conversions.Bson result = MapperUtil.getBson(options, "doc");

            assertNotNull(result);
            assertInstanceOf(BsonDocument.class, result);
        }

        @Test
        @DisplayName("WHEN value is not Bson or Map THEN throws IllegalArgumentException")
        void getBsonInvalidTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("doc", "not a bson");

            assertThrows(IllegalArgumentException.class, () ->
                    MapperUtil.getBson(options, "doc"));
        }
    }

    @Nested
    @DisplayName("getCollation Tests")
    class GetCollationTests {

        @Test
        @DisplayName("WHEN value is Collation THEN returns value")
        void getCollationValidTest() {
            Collation collation = Collation.builder()
                    .locale("en")
                    .collationStrength(CollationStrength.PRIMARY)
                    .build();

            Map<String, Object> options = new HashMap<>();
            options.put("collation", collation);

            Collation result = MapperUtil.getCollation(options, "collation");

            assertEquals(collation, result);
        }

        @Test
        @DisplayName("WHEN value is Map with locale only THEN returns Collation")
        void getCollationFromMapLocaleOnlyTest() {
            Map<String, Object> collationMap = new HashMap<>();
            collationMap.put("locale", "en");

            Map<String, Object> options = new HashMap<>();
            options.put("collation", collationMap);

            Collation result = MapperUtil.getCollation(options, "collation");

            assertNotNull(result);
            assertEquals("en", result.getLocale());
        }

        @Test
        @DisplayName("WHEN value is Map with all fields THEN returns fully configured Collation")
        void getCollationFromMapAllFieldsTest() {
            Map<String, Object> collationMap = new HashMap<>();
            collationMap.put("locale", "en");
            collationMap.put("caseLevel", true);
            collationMap.put("caseFirst", "upper");
            collationMap.put("strength", 2);
            collationMap.put("numericOrdering", true);
            collationMap.put("alternate", "shifted");
            collationMap.put("maxVariable", "space");
            collationMap.put("normalization", true);
            collationMap.put("backwards", true);

            Map<String, Object> options = new HashMap<>();
            options.put("collation", collationMap);

            Collation result = MapperUtil.getCollation(options, "collation");

            assertNotNull(result);
            assertEquals("en", result.getLocale());
            assertTrue(result.getCaseLevel());
            assertEquals(CollationCaseFirst.UPPER, result.getCaseFirst());
            assertEquals(CollationStrength.SECONDARY, result.getStrength());
            assertTrue(result.getNumericOrdering());
            assertEquals(CollationAlternate.SHIFTED, result.getAlternate());
            assertEquals(CollationMaxVariable.SPACE, result.getMaxVariable());
            assertTrue(result.getNormalization());
            assertTrue(result.getBackwards());
        }

        @Test
        @DisplayName("WHEN value is not Collation or Map THEN throws IllegalArgumentException")
        void getCollationInvalidTest() {
            Map<String, Object> options = new HashMap<>();
            options.put("collation", "not a collation");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    MapperUtil.getCollation(options, "collation"));
            assertTrue(ex.getMessage().contains("Collation or Map"));
        }
    }

}
