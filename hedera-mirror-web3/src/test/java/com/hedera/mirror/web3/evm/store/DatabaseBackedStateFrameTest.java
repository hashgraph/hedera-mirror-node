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

package com.hedera.mirror.web3.evm.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.hedera.mirror.web3.evm.store.accessor.DatabaseAccessor;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import org.junit.jupiter.api.Test;

class DatabaseBackedStateFrameTest {

    private static final Optional<Long> timestamp = Optional.of(1234L);
    final DatabaseAccessorStub<Integer, Long> dbAccessorForLong = new DatabaseAccessorStub<>() {};
    final List<DatabaseAccessor<Integer, ?>> someAccessors = List.of(
            dbAccessorForLong,
            new DatabaseAccessorStub<Integer, String>() {},
            new DatabaseAccessorStub<Integer, Math>() {});
    final Class<?>[] thoseValueClasses = new Class<?>[] {Long.class, String.class, Math.class};

    @Test
    void getValueWorks() {
        final Integer aKey = 5;
        final UpdatableReferenceCache<Integer> aCache = new UpdatableReferenceCache<>();
        final var sut = new DatabaseBackedStateFrame<>(someAccessors, thoseValueClasses, timestamp);

        dbAccessorForLong.setCannedKV(aKey, null);
        assertThat(sut.getValue(Long.class, aCache, aKey)).isEmpty();

        final Long expectedValue = 123L;
        dbAccessorForLong.setCannedKV(aKey, expectedValue);
        assertThat(sut.getValue(Long.class, aCache, aKey)).isPresent().contains(expectedValue);
    }

    @Test
    void getValueWithBadClassFails() {
        final Integer aKey = 5;
        final var anInvalidClass = Double.class;
        final UpdatableReferenceCache<Integer> aCache = new UpdatableReferenceCache<>();
        final var sut = new DatabaseBackedStateFrame<>(someAccessors, thoseValueClasses, timestamp);

        assertThatNullPointerException().isThrownBy(() -> sut.getValue(anInvalidClass, aCache, aKey));
    }

    @Test
    void setValueIsUnsupported() {
        final Integer aKey = 5;
        final Long aValue = 7L;
        final UpdatableReferenceCache<Integer> aCache = new UpdatableReferenceCache<>();
        final var sut = new DatabaseBackedStateFrame<>(someAccessors, thoseValueClasses, timestamp);

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> sut.setValue(Long.class, aCache, aKey, aValue));
    }

    @Test
    void deleteValueIsUnsupported() {
        final Integer aKey = 5;
        final UpdatableReferenceCache<Integer> aCache = new UpdatableReferenceCache<>();
        final var sut = new DatabaseBackedStateFrame<>(someAccessors, thoseValueClasses, timestamp);

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> sut.deleteValue(Long.class, aCache, aKey));
    }

    @Test
    void updateIsUnsupported() {
        final var someClasses = new Class<?>[] {Long.class, String.class, Math.class};
        final var upstream = new BottomCachingStateFrame<Integer>(Optional.empty(), someClasses);
        final var sut = new DatabaseBackedStateFrame<>(someAccessors, thoseValueClasses, timestamp);

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> sut.updatesFromDownstream(upstream));
    }

    @Test
    void commitIsUnsupported() {
        final var sut = new DatabaseBackedStateFrame<>(someAccessors, thoseValueClasses, timestamp);

        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(sut::commit);
    }

    static class DatabaseAccessorStub<K, V> extends DatabaseAccessor<K, V> {
        public K cannedKey;
        public Optional<V> cannedValue = Optional.empty();

        @Override
        public @NonNull Optional<V> get(@NonNull final K key, final Optional<Long> timestamp) {
            return key == cannedKey ? cannedValue : Optional.empty();
        }

        public void setCannedKV(@NonNull final K key, @Nullable final V value) {
            cannedKey = key;
            cannedValue = Optional.ofNullable(value);
        }
    }
}
