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

package com.google.common.base;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.CheckForNull;
import org.checkerframework.checker.nullness.qual.Nullable;

@ElementTypesAreNonnullByDefault
public class Suppliers {
    private Suppliers() {}

    /**
     * Returns a supplier which caches the instance retrieved during the first call to {@code get()}
     * and returns that value on subsequent calls to {@code get()}. See: <a
     * href="http://en.wikipedia.org/wiki/Memoization">memoization</a>
     *
     * <p>The supplier's serialized
     * form does not contain the cached value, which will be recalculated when {@code get()} is called
     * on the deserialized instance.
     *
     * <p>When the underlying delegate throws an exception then this memoizing supplier will keep
     * delegating calls until it returns valid data.
     *
     * <p>If {@code delegate} is an instance created by an earlier call to {@code memoize}, it is
     * returned directly.
     */
    public static <T> Supplier<T> memoize(Supplier<T> delegate) {
        final var value = new AtomicReference<T>();
        return () -> {
            final T previousValue = value.get();
            if (previousValue != null) {
                return previousValue;
            }

            final T newValue = delegate.get();
            value.set(newValue);
            return newValue;
        };
    }

    /** Returns a supplier that always supplies {@code instance}. */
    public static <T extends @Nullable Object> Supplier<T> ofInstance(@ParametricNullness T instance) {
        return new SupplierOfInstance<>(instance);
    }

    private static class SupplierOfInstance<T extends @Nullable Object> implements Supplier<T>, Serializable {
        @ParametricNullness
        final T instance;

        SupplierOfInstance(@ParametricNullness T instance) {
            this.instance = instance;
        }

        @Override
        @ParametricNullness
        public T get() {
            return instance;
        }

        @Override
        public boolean equals(@CheckForNull Object obj) {
            if (obj instanceof SupplierOfInstance) {
                SupplierOfInstance<?> that = (SupplierOfInstance<?>) obj;
                return Objects.equal(instance, that.instance);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(instance);
        }

        @Override
        public String toString() {
            return "Suppliers.ofInstance(" + instance + ")";
        }

        private static final long serialVersionUID = 0;
    }
}
