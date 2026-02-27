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
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BsonConverterTest {

    @Nested
    @DisplayName("toBsonValue Tests")
    class ToBsonValueTests {

        @Test
        @DisplayName("WHEN value is null THEN returns BsonNull")
        void toBsonValueNullTest() {
            BsonValue result = BsonConverter.toBsonValue(null);

            assertEquals(BsonNull.VALUE, result);
        }

        @Test
        @DisplayName("WHEN value is String THEN returns BsonString")
        void toBsonValueStringTest() {
            BsonValue result = BsonConverter.toBsonValue("test");

            assertInstanceOf(BsonString.class, result);
            assertEquals("test", ((BsonString) result).getValue());
        }

        @Test
        @DisplayName("WHEN value is Integer THEN returns BsonInt32")
        void toBsonValueIntegerTest() {
            BsonValue result = BsonConverter.toBsonValue(42);

            assertInstanceOf(BsonInt32.class, result);
            assertEquals(42, ((BsonInt32) result).getValue());
        }

        @Test
        @DisplayName("WHEN value is Long THEN returns BsonInt64")
        void toBsonValueLongTest() {
            BsonValue result = BsonConverter.toBsonValue(42L);

            assertInstanceOf(BsonInt64.class, result);
            assertEquals(42L, ((BsonInt64) result).getValue());
        }

        @Test
        @DisplayName("WHEN value is Double THEN returns BsonDouble")
        void toBsonValueDoubleTest() {
            BsonValue result = BsonConverter.toBsonValue(3.14);

            assertInstanceOf(BsonDouble.class, result);
            assertEquals(3.14, ((BsonDouble) result).getValue(), 0.001);
        }

        @Test
        @DisplayName("WHEN value is Boolean THEN returns BsonBoolean")
        void toBsonValueBooleanTest() {
            BsonValue result = BsonConverter.toBsonValue(true);

            assertInstanceOf(BsonBoolean.class, result);
            assertTrue(((BsonBoolean) result).getValue());
        }

        @Test
        @DisplayName("WHEN value is Map THEN returns BsonDocument")
        void toBsonValueMapTest() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", "test");
            map.put("count", 42);

            BsonValue result = BsonConverter.toBsonValue(map);

            assertInstanceOf(BsonDocument.class, result);
            BsonDocument doc = (BsonDocument) result;
            assertEquals("test", doc.getString("name").getValue());
            assertEquals(42, doc.getInt32("count").getValue());
        }

        @Test
        @DisplayName("WHEN value is List THEN returns BsonArray")
        void toBsonValueListTest() {
            List<Object> list = Arrays.asList("item1", 42, true);

            BsonValue result = BsonConverter.toBsonValue(list);

            assertInstanceOf(BsonArray.class, result);
            BsonArray array = (BsonArray) result;
            assertEquals(3, array.size());
            assertEquals("item1", array.get(0).asString().getValue());
            assertEquals(42, array.get(1).asInt32().getValue());
            assertTrue(array.get(2).asBoolean().getValue());
        }

        @Test
        @DisplayName("WHEN value is unsupported type THEN throws IllegalArgumentException")
        void toBsonValueUnsupportedTest() {
            assertThrows(IllegalArgumentException.class, () ->
                    BsonConverter.toBsonValue(new Object()));
        }
    }

    @Nested
    @DisplayName("toBsonArray Tests")
    class ToBsonArrayTests {

        @Test
        @DisplayName("WHEN list has mixed types THEN returns BsonArray with correct types")
        void toBsonArrayMixedTypesTest() {
            List<Object> list = Arrays.asList("text", 123, 456L, 3.14, false, null);

            BsonArray result = BsonConverter.toBsonArray(list);

            assertEquals(6, result.size());
            assertInstanceOf(BsonString.class, result.get(0));
            assertInstanceOf(BsonInt32.class, result.get(1));
            assertInstanceOf(BsonInt64.class, result.get(2));
            assertInstanceOf(BsonDouble.class, result.get(3));
            assertInstanceOf(BsonBoolean.class, result.get(4));
            assertTrue(result.get(5).isNull());
        }

        @Test
        @DisplayName("WHEN list has nested list THEN returns nested BsonArray")
        void toBsonArrayNestedTest() {
            List<Object> innerList = Arrays.asList(1, 2, 3);
            List<Object> outerList = Arrays.asList("outer", innerList);

            BsonArray result = BsonConverter.toBsonArray(outerList);

            assertEquals(2, result.size());
            assertEquals("outer", result.get(0).asString().getValue());
            assertInstanceOf(BsonArray.class, result.get(1));
            BsonArray nested = result.get(1).asArray();
            assertEquals(3, nested.size());
        }

        @Test
        @DisplayName("WHEN list has map THEN returns BsonArray with BsonDocument")
        void toBsonArrayWithMapTest() {
            Map<String, Object> map = new HashMap<>();
            map.put("key", "value");
            List<Object> list = Collections.singletonList(map);

            BsonArray result = BsonConverter.toBsonArray(list);

            assertEquals(1, result.size());
            assertInstanceOf(BsonDocument.class, result.get(0));
            assertEquals("value", result.get(0).asDocument().getString("key").getValue());
        }

        @Test
        @DisplayName("WHEN list is empty THEN returns empty BsonArray")
        void toBsonArrayEmptyTest() {
            List<Object> list = Collections.emptyList();

            BsonArray result = BsonConverter.toBsonArray(list);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("toBsonDocument Tests")
    class ToBsonDocumentTests {

        @Test
        @DisplayName("WHEN map has nested structure THEN returns nested BsonDocument")
        void toBsonDocumentNestedTest() {
            Map<String, Object> inner = new HashMap<>();
            inner.put("field", "value");

            Map<String, Object> outer = new HashMap<>();
            outer.put("nested", inner);
            outer.put("simple", "text");

            BsonDocument result = BsonConverter.toBsonDocument(outer);

            assertEquals("text", result.getString("simple").getValue());
            assertInstanceOf(BsonDocument.class, result.get("nested"));
            assertEquals("value", result.getDocument("nested").getString("field").getValue());
        }

        @Test
        @DisplayName("WHEN map has null value THEN returns BsonNull in document")
        void toBsonDocumentWithNullTest() {
            Map<String, Object> map = new HashMap<>();
            map.put("nullField", null);
            map.put("stringField", "value");

            BsonDocument result = BsonConverter.toBsonDocument(map);

            assertTrue(result.get("nullField").isNull());
            assertEquals("value", result.getString("stringField").getValue());
        }
    }
}
