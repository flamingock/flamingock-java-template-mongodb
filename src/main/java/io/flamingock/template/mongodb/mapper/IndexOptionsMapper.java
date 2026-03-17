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

import com.mongodb.client.model.IndexOptions;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static io.flamingock.template.mongodb.mapper.MapperUtil.getBoolean;
import static io.flamingock.template.mongodb.mapper.MapperUtil.getBson;
import static io.flamingock.template.mongodb.mapper.MapperUtil.getCollation;
import static io.flamingock.template.mongodb.mapper.MapperUtil.getDouble;
import static io.flamingock.template.mongodb.mapper.MapperUtil.getInteger;
import static io.flamingock.template.mongodb.mapper.MapperUtil.getLong;
import static io.flamingock.template.mongodb.mapper.MapperUtil.getString;


public final class IndexOptionsMapper {

    public static final Set<String> RECOGNIZED_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "background", "unique", "name", "sparse", "expireAfterSeconds",
            "version", "weights", "defaultLanguage", "languageOverride",
            "textVersion", "sphereVersion", "bits", "min", "max",
            "storageEngine", "partialFilterExpression", "collation"
    )));

    /** Options removed from the MongoDB Java driver: rejected at validation time with a clear error. */
    public static final Set<String> UNSUPPORTED_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "bucketSize", "wildcardProjection", "hidden"
    )));

    private IndexOptionsMapper() {}

    public static IndexOptions mapToIndexOptions(Map<String, Object> options) {
        IndexOptions indexOptions = new IndexOptions();

        if (options.containsKey("background")) {
            indexOptions.background(getBoolean(options, "background"));
        }
        if (options.containsKey("unique")) {
            indexOptions.unique(getBoolean(options, "unique"));
        }
        if (options.containsKey("name")) {
            indexOptions.name(getString(options, "name"));
        }
        if (options.containsKey("sparse")) {
            indexOptions.sparse(getBoolean(options, "sparse"));
        }
        if (options.containsKey("expireAfterSeconds")) {
            indexOptions.expireAfter(getLong(options, "expireAfterSeconds"), TimeUnit.SECONDS);
        }
        if (options.containsKey("version")) {
            indexOptions.version(getInteger(options, "version"));
        }
        if (options.containsKey("weights")) {
            indexOptions.weights(getBson(options, "weights"));
        }
        if (options.containsKey("defaultLanguage")) {
            indexOptions.defaultLanguage(getString(options, "defaultLanguage"));
        }
        if (options.containsKey("languageOverride")) {
            indexOptions.languageOverride(getString(options, "languageOverride"));
        }
        if (options.containsKey("textVersion")) {
            indexOptions.textVersion(getInteger(options, "textVersion"));
        }
        if (options.containsKey("sphereVersion")) {
            indexOptions.sphereVersion(getInteger(options, "sphereVersion"));
        }
        if (options.containsKey("bits")) {
            indexOptions.bits(getInteger(options, "bits"));
        }
        if (options.containsKey("min")) {
            indexOptions.min(getDouble(options, "min"));
        }
        if (options.containsKey("max")) {
            indexOptions.max(getDouble(options, "max"));
        }
        if (options.containsKey("storageEngine")) {
            indexOptions.storageEngine(getBson(options, "storageEngine"));
        }
        if (options.containsKey("partialFilterExpression")) {
            indexOptions.partialFilterExpression(getBson(options, "partialFilterExpression"));
        }
        if (options.containsKey("collation")) {
            indexOptions.collation(getCollation(options, "collation"));
        }

        return indexOptions;
    }
}

