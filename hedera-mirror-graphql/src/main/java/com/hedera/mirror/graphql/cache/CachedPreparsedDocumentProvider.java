/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.graphql.cache;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import graphql.ExecutionInput;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import jakarta.inject.Named;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Named
final class CachedPreparsedDocumentProvider implements PreparsedDocumentProvider {

    private final AsyncCache<String, PreparsedDocumentEntry> cache;

    CachedPreparsedDocumentProvider(CacheProperties properties) {
        cache = Caffeine.from(properties.getQuery()).buildAsync();
    }

    @Override
    public CompletableFuture<PreparsedDocumentEntry> getDocumentAsync(
            ExecutionInput executionInput, Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
        return cache.get(executionInput.getQuery(), key -> parseAndValidateFunction.apply(executionInput));
    }
}
