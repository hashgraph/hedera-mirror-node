/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3;

import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.mirror.web3.evm.store.Store;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import lombok.CustomLog;
import lombok.SneakyThrows;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.springframework.util.ReflectionUtils;

/**
 * This JUnit extension initializes the ContractCallContext before each test and ensures it's cleaned up afterward.
 */
@CustomLog
public class ContextExtension implements InvocationInterceptor {

    @Override
    public <T> T interceptTestFactoryMethod(
            Invocation<T> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext)
            throws Throwable {
        return intercept(invocation, invocationContext);
    }

    @Override
    public void interceptTestMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext)
            throws Throwable {
        intercept(invocation, invocationContext);
    }

    @Override
    public void interceptTestTemplateMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext)
            throws Throwable {
        intercept(invocation, invocationContext);
    }

    private <T> T intercept(Invocation<T> invocation, ReflectiveInvocationContext<Method> invocationContext)
            throws Throwable {

        var stackedStateFrames =
                getStackedStateFrames(invocationContext.getTarget().get());

        try (var context = ContractCallContext.init(stackedStateFrames)) {
            log.debug("Creating new context {}", context);
            return invocation.proceed();
        }
    }

    // If there's a Store field on the test use it to initialize the context
    @SneakyThrows
    private StackedStateFrames getStackedStateFrames(Object target) {
        for (var field : target.getClass().getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && Store.class.isAssignableFrom(field.getType())) {
                ReflectionUtils.makeAccessible(field);
                var store = (Store) field.get(target);
                return store != null ? store.getStackedStateFrames() : null;
            }
        }

        return null;
    }
}
