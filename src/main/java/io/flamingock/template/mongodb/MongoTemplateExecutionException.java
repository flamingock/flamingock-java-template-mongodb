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
package io.flamingock.template.mongodb;

/**
 * Wraps MongoDB driver exceptions with template-level context (operation type and collection name),
 * making failures in multi-step changes easier to debug. The original exception is preserved as the cause.
 */
public class MongoTemplateExecutionException extends RuntimeException {

    public MongoTemplateExecutionException(String type, String collection, Throwable cause) {
        super("Failed to execute '" + type + "' on collection '" + collection + "': " + cause.getMessage(), cause);
    }
}
