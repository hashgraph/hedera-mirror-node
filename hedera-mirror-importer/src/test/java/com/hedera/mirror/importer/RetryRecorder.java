/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import jakarta.inject.Named;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;

// Records retry attempts made via Spring @Retryable or RetryTemplate for verification in tests
@Named
public final class RetryRecorder implements RetryListener {

    private final Multiset<Class<? extends Throwable>> retries = ConcurrentHashMultiset.create();

    @Override
    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable t) {
        retries.add(t.getClass());
    }

    public int getRetries(Class<? extends Throwable> throwableClass) {
        return retries.count(throwableClass);
    }

    public void reset() {
        retries.clear();
    }
}
