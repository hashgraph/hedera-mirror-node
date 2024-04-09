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

import static com.google.common.base.NullnessCasts.uncheckedCastNullableTToT;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.util.concurrent.locks.ReentrantLock;
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
     * <p>The returned supplier is thread-safe. The delegate's {@code get()} method will be invoked at
     * most once unless the underlying {@code get()} throws an exception. The supplier's serialized
     * form does not contain the cached value, which will be recalculated when {@code get()} is called
     * on the deserialized instance.
     *
     * <p>When the underlying delegate throws an exception then this memoizing supplier will keep
     * delegating calls until it returns valid data.
     *
     * <p>If {@code delegate} is an instance created by an earlier call to {@code memoize}, it is
     * returned directly.
     */
    public static <T extends @Nullable Object> Supplier<T> memoize(Supplier<T> delegate) {
        if (delegate instanceof NonSerializableMemoizingSupplier || delegate instanceof MemoizingSupplier) {
            return delegate;
        }
        return delegate instanceof Serializable
                ? new MemoizingSupplier<T>(delegate)
                : new NonSerializableMemoizingSupplier<T>(delegate);
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

    static class MemoizingSupplier<T extends @Nullable Object> implements Supplier<T>, Serializable {
        final Supplier<T> delegate;
        private final ReentrantLock lock = new ReentrantLock();
        transient volatile boolean initialized;
        // "value" does not need to be volatile; visibility piggy-backs
        // on volatile read of "initialized".
        @CheckForNull
        transient T value;

        MemoizingSupplier(Supplier<T> delegate) {
            this.delegate = checkNotNull(delegate);
        }

        @Override
        @ParametricNullness
        public T get() {
            lock.lock();
            try {
                if (!initialized) {
                    T t = delegate.get();
                    value = t;
                    initialized = true;
                    return t;
                }
            } finally {
                lock.unlock();
            }
            // This is safe because we checked `initialized`.
            return uncheckedCastNullableTToT(value);
        }

        @Override
        public String toString() {
            return "Suppliers.memoize(" + (initialized ? "<supplier that returned " + value + ">" : delegate) + ")";
        }

        private static final long serialVersionUID = 0;
    }

    static class NonSerializableMemoizingSupplier<T extends @Nullable Object> implements Supplier<T> {
        @SuppressWarnings("UnnecessaryLambda") // Must be a fixed singleton object
        private static final Supplier<Void> SUCCESSFULLY_COMPUTED = () -> {
            throw new IllegalStateException(); // Should never get called.
        };

        private volatile Supplier<T> delegate;
        private final ReentrantLock lock = new ReentrantLock();
        // "value" does not need to be volatile; visibility piggy-backs on volatile read of "delegate".
        @CheckForNull
        private T value;

        NonSerializableMemoizingSupplier(Supplier<T> delegate) {
            this.delegate = checkNotNull(delegate);
        }

        @Override
        @ParametricNullness
        @SuppressWarnings("unchecked") // Cast from Supplier<Void> to Supplier<T> is always valid
        public T get() {
            // Because Supplier is read-heavy, we use the "double-checked locking" pattern.
            lock.lock();
            try {
                if (delegate != SUCCESSFULLY_COMPUTED) {
                    T t = delegate.get();
                    value = t;
                    delegate = (Supplier<T>) SUCCESSFULLY_COMPUTED;
                    return t;
                }
            } finally {
                lock.unlock();
            }

            // This is safe because we checked `delegate`.
            return uncheckedCastNullableTToT(value);
        }

        @Override
        public String toString() {
            Supplier<T> delegate = this.delegate;
            return "Suppliers.memoize("
                    + (delegate == SUCCESSFULLY_COMPUTED ? "<supplier that returned " + value + ">" : delegate)
                    + ")";
        }
    }
}
