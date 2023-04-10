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

package com.hedera.mirror.web3.evm.store.hedera;

import static com.hedera.mirror.web3.evm.store.hedera.impl.UpdatableReferenceCacheLineState.ValueState.INVALID;
import static com.hedera.mirror.web3.evm.store.hedera.impl.UpdatableReferenceCacheLineState.ValueState.NOT_YET_FETCHED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.evm.store.hedera.impl.UpdatableReferenceCacheLineState.Entry;
import com.hedera.mirror.web3.evm.store.hedera.impl.UpdatableReferenceCacheLineState.ValueState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ROCachingStateFrameTest {

    static class CannedCachingStateFrame extends CachingStateFrame<Integer> {

        final Optional<Character> cannedValue;

        public CannedCachingStateFrame(@NonNull final Optional<Character> cannedValue) {
            super(Optional.empty(), Character.class);
            this.cannedValue = cannedValue;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        @NonNull
        @Override
        <V> Accessor<Integer, V> getAccessor(@NonNull Class<V> klass) {
            return new Accessor<Integer, V>() {
                @Override
                public Optional<V> get(@NonNull final Integer key) {
                    return (Optional<V>) (Optional) cannedValue;
                }

                @Override
                public void set(@NonNull final Integer key, @NonNull final V value) {
                    /* not needed */
                }

                @Override
                public void delete(@NonNull final Integer key) {
                    /* not needed */
                }
            };
        }

        @Override
        public void updatesFromDownstream(@NonNull final CachingStateFrame<Integer> childFrame) {
            /* not needed */
        }

        @NonNull
        @Override
        protected Optional<Object> getValue(
                @NonNull final Class<?> klass,
                @NonNull final UpdatableReferenceCache<Integer> cache,
                @NonNull final Integer key) {
            /* not needed */
            return Optional.empty();
        }

        @Override
        protected void setValue(
                @NonNull final Class<?> klass,
                @NonNull final UpdatableReferenceCache<Integer> cache,
                @NonNull final Integer key,
                @NonNull final Object value) {
            /* not needed */
        }

        @Override
        protected void deleteValue(
                @NonNull final Class<?> klass,
                @NonNull final UpdatableReferenceCache<Integer> cache,
                @NonNull final Integer key) {
            /* not needed */
        }
    }
    ;

    @Mock
    UpdatableReferenceCache<Integer> mockCache;

    @Test
    void getValidOfNotYetFetchedGoesUpstreamAndItIsMissing() {
        final Integer k = 555;

        final var upstreamFrame = new CannedCachingStateFrame(Optional.empty());
        when(mockCache.get(k)).thenReturn(new Entry(NOT_YET_FETCHED, null));
        final var sut = new ROCachingStateFrame<Integer>(Optional.of(upstreamFrame), Character.class);
        final var actual = sut.getValue(Character.class, mockCache, k);

        assertThat(actual).isEmpty();
        verify(mockCache, times(1)).fill(k, null);
    }

    @Test
    void getValidOfNotYetFetchedGoesUpstreamAndItIsPresent() {
        final Integer k = 555;
        final Character v = 'C';

        final var upstreamFrame = new CannedCachingStateFrame(Optional.of(v));
        when(mockCache.get(k)).thenReturn(new Entry(NOT_YET_FETCHED, null));
        final var sut = new ROCachingStateFrame<Integer>(Optional.of(upstreamFrame), Character.class);
        final var actual = sut.getValue(Character.class, mockCache, k);

        assertThat(actual).contains(v);
        verify(mockCache, times(1)).fill(k, v);
    }

    @ParameterizedTest
    @EnumSource(
            value = ValueState.class,
            names = {"PRESENT", "UPDATED"})
    void getValidOfPresentOrUpdatedEntryReturnsValue(ValueState state) {
        final Integer k = 555;
        final Character v = 'C';
        when(mockCache.get(k)).thenReturn(new Entry(state, v));
        final var sut = new ROCachingStateFrame<Integer>(Optional.empty(), Character.class);
        final var actual = sut.getValue(Character.class, mockCache, k);
        assertThat(actual).contains(v);
    }

    @ParameterizedTest
    @EnumSource(
            value = ValueState.class,
            names = {"MISSING", "DELETED"})
    void getValidOfMissingOrDeletedEntryReturnsEmpty(ValueState state) {
        final Integer k = 555;
        when(mockCache.get(k)).thenReturn(new Entry(state, null));
        final var sut = new ROCachingStateFrame<Integer>(Optional.empty(), Character.class);
        final var actual = sut.getValue(Character.class, mockCache, k);
        assertThat(actual).isEmpty();
    }

    @Test
    void getValueOfInvalidEntryThrows() {
        final Integer k = 555;
        when(mockCache.get(k)).thenReturn(new Entry(INVALID, null));
        final var sut = new ROCachingStateFrame<Integer>(Optional.empty(), Character.class);
        assertThatIllegalArgumentException().isThrownBy(() -> sut.getValue(Character.class, mockCache, k));
    }

    @Test
    void setValueIsNotAllowed() {
        final var cache = new UpdatableReferenceCache<Integer>();
        final var sut = new ROCachingStateFrame<Integer>(Optional.empty(), Character.class);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> sut.setValue(Character.class, cache, 1, 'C'));
    }

    @Test
    void deleteValueIsNotAllowed() {
        final var cache = new UpdatableReferenceCache<Integer>();
        final var sut = new ROCachingStateFrame<Integer>(Optional.empty(), Character.class);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> sut.deleteValue(Character.class, cache, 1));
    }

    @Test
    void updatesFromDownstreamIsNotAllowed() {
        final var sut = new ROCachingStateFrame<Integer>(Optional.empty(), Character.class);
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> sut.updatesFromDownstream(sut));
    }
}
