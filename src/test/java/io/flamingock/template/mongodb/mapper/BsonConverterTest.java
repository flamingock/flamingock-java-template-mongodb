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
        @DisplayName("WHEN value is empty String THEN returns empty BsonString")
        void toBsonValueEmptyStringTest() {
            BsonValue result = BsonConverter.toBsonValue("");

            assertInstanceOf(BsonString.class, result);
            assertEquals("", ((BsonString) result).getValue());
        }

        @Test
        @DisplayName("WHEN value is Integer THEN returns BsonInt32")
        void toBsonValueIntegerTest() {
            BsonValue result = BsonConverter.toBsonValue(42);

            assertInstanceOf(BsonInt32.class, result);
            assertEquals(42, ((BsonInt32) result).getValue());
        }

        @Test
        @DisplayName("WHEN value is Integer zero THEN returns BsonInt32 zero")
        void toBsonValueIntegerZeroTest() {
            BsonValue result = BsonConverter.toBsonValue(0);

            assertInstanceOf(BsonInt32.class, result);
            assertEquals(0, ((BsonInt32) result).getValue());
        }

        @Test
        @DisplayName("WHEN value is negative Integer THEN returns negative BsonInt32")
        void toBsonValueNegativeIntegerTest() {
            BsonValue result = BsonConverter.toBsonValue(-1);

            assertInstanceOf(BsonInt32.class, result);
            assertEquals(-1, ((BsonInt32) result).getValue());
        }

        @Test
        @DisplayName("WHEN value is Long THEN returns BsonInt64")
        void toBsonValueLongTest() {
            BsonValue result = BsonConverter.toBsonValue(42L);

            assertInstanceOf(BsonInt64.class, result);
            assertEquals(42L, ((BsonInt64) result).getValue());
        }

        @Test
        @DisplayName("WHEN value is negative Long THEN returns negative BsonInt64")
        void toBsonValueNegativeLongTest() {
            BsonValue result = BsonConverter.toBsonValue(-100L);

            assertInstanceOf(BsonInt64.class, result);
            assertEquals(-100L, ((BsonInt64) result).getValue());
        }

        @Test
        @DisplayName("WHEN value is Double THEN returns BsonDouble")
        void toBsonValueDoubleTest() {
            BsonValue result = BsonConverter.toBsonValue(3.14);

            assertInstanceOf(BsonDouble.class, result);
            assertEquals(3.14, ((BsonDouble) result).getValue(), 0.001);
        }

        @Test
        @DisplayName("WHEN value is Double zero THEN returns BsonDouble zero")
        void toBsonValueDoubleZeroTest() {
            BsonValue result = BsonConverter.toBsonValue(0.0);

            assertInstanceOf(BsonDouble.class, result);
            assertEquals(0.0, ((BsonDouble) result).getValue(), 0.001);
        }

        @Test
        @DisplayName("WHEN value is negative Double THEN returns negative BsonDouble")
        void toBsonValueNegativeDoubleTest() {
            BsonValue result = BsonConverter.toBsonValue(-2.5);

            assertInstanceOf(BsonDouble.class, result);
            assertEquals(-2.5, ((BsonDouble) result).getValue(), 0.001);
        }

        @Test
        @DisplayName("WHEN value is Boolean true THEN returns BsonBoolean true")
        void toBsonValueBooleanTrueTest() {
            BsonValue result = BsonConverter.toBsonValue(true);

            assertInstanceOf(BsonBoolean.class, result);
            assertTrue(((BsonBoolean) result).getValue());
        }

        @Test
        @DisplayName("WHEN value is Boolean false THEN returns BsonBoolean false")
        void toBsonValueBooleanFalseTest() {
            BsonValue result = BsonConverter.toBsonValue(false);

            assertInstanceOf(BsonBoolean.class, result);
            assertFalse(((BsonBoolean) result).getValue());
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
        @DisplayName("WHEN value is empty Map THEN returns empty BsonDocument")
        void toBsonValueEmptyMapTest() {
            BsonValue result = BsonConverter.toBsonValue(new HashMap<>());

            assertInstanceOf(BsonDocument.class, result);
            assertTrue(((BsonDocument) result).isEmpty());
        }

        @Test
        @DisplayName("WHEN value is empty List THEN returns empty BsonArray")
        void toBsonValueEmptyListTest() {
            BsonValue result = BsonConverter.toBsonValue(Collections.emptyList());

            assertInstanceOf(BsonArray.class, result);
            assertTrue(((BsonArray) result).isEmpty());
        }

        @Test
        @DisplayName("WHEN value is Float THEN throws IllegalArgumentException")
        void toBsonValueFloatUnsupportedTest() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    BsonConverter.toBsonValue(1.5f));
            assertTrue(ex.getMessage().contains("Float"));
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

        @Test
        @DisplayName("WHEN map is empty THEN returns empty BsonDocument")
        void toBsonDocumentEmptyTest() {
            BsonDocument result = BsonConverter.toBsonDocument(new HashMap<>());

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("WHEN map has deep nesting THEN converts recursively")
        void toBsonDocumentDeepNestingTest() {
            Map<String, Object> level3 = new HashMap<>();
            level3.put("value", 42);

            List<Object> level2List = Arrays.asList(level3, "text");

            Map<String, Object> level1 = new HashMap<>();
            level1.put("items", level2List);

            Map<String, Object> root = new HashMap<>();
            root.put("nested", level1);

            BsonDocument result = BsonConverter.toBsonDocument(root);

            BsonDocument nestedDoc = result.getDocument("nested");
            BsonArray items = nestedDoc.getArray("items");
            assertEquals(2, items.size());
            assertEquals(42, items.get(0).asDocument().getInt32("value").getValue());
            assertEquals("text", items.get(1).asString().getValue());
        }

        @Test
        @DisplayName("WHEN map has all supported types THEN converts all correctly")
        void toBsonDocumentAllTypesTest() {
            Map<String, Object> map = new HashMap<>();
            map.put("string", "text");
            map.put("int", 1);
            map.put("long", 2L);
            map.put("double", 3.0);
            map.put("bool", true);
            map.put("null", null);
            map.put("list", Arrays.asList(1, 2));
            Map<String, Object> inner = new HashMap<>();
            inner.put("key", "val");
            map.put("map", inner);

            BsonDocument result = BsonConverter.toBsonDocument(map);

            assertEquals(8, result.size());
            assertInstanceOf(BsonString.class, result.get("string"));
            assertInstanceOf(BsonInt32.class, result.get("int"));
            assertInstanceOf(BsonInt64.class, result.get("long"));
            assertInstanceOf(BsonDouble.class, result.get("double"));
            assertInstanceOf(BsonBoolean.class, result.get("bool"));
            assertTrue(result.get("null").isNull());
            assertInstanceOf(BsonArray.class, result.get("list"));
            assertInstanceOf(BsonDocument.class, result.get("map"));
        }
    }
}
