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

package com.hedera.mirror.web3.evm.store;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigInteger;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RWCachingStateFrameTest {

    @Mock
    UpdatableReferenceCache<Integer> mockCache;

    @Test
    void setValueSetsTheValue() {
        final Integer k = 555;
        final Character v = 'C';
        final var sut = new RWCachingStateFrame<Integer>(Optional.empty(), Character.class);
        sut.setValue(Character.class, mockCache, k, v);
        verify(mockCache, times(1)).update(k, v);
    }

    @Test
    void deleteValueDeletesTheValue() {
        final Integer k = 555;
        final var sut = new RWCachingStateFrame<Integer>(Optional.empty(), Character.class);
        sut.deleteValue(Character.class, mockCache, k);
        verify(mockCache, times(1)).delete(k);
    }

    @Test
    void updatesFromDownstreamRequiresSameClassesRegistered() {
        final var otherFrame1 = new BottomCachingStateFrame<Integer>(Optional.empty(), Character.class);
        final var otherFrame2 =
                new BottomCachingStateFrame<Integer>(Optional.empty(), Character.class, BigInteger.class);

        final var sut = new RWCachingStateFrame<Integer>(Optional.empty(), Character.class, String.class);

        assertThatIllegalStateException().isThrownBy(() -> sut.updatesFromDownstream(otherFrame1));
        assertThatIllegalStateException().isThrownBy(() -> sut.updatesFromDownstream(otherFrame2));
    }

    @SuppressWarnings("unchecked")
    @Test
    void updatesFromDownStreamCoalesceProperly() {

        enum CacheKind {
            thisC,
            thisS,
            downC,
            downS
        };

        final EnumMap<CacheKind, UpdatableReferenceCache<Integer>> caches = new EnumMap<>(CacheKind.class);

        for (final var ck : CacheKind.values()) {
            caches.put(ck, Mockito.mock(UpdatableReferenceCache.class));
        }

        final var downstreamFrame =
                Mockito.spy(new BottomCachingStateFrame<Integer>(Optional.empty(), Character.class, String.class));

        final var sutInternalCaches =
                Map.of(Character.class, caches.get(CacheKind.thisC), String.class, caches.get(CacheKind.thisS));
        final var downstreamInternalCaches =
                Map.of(Character.class, caches.get(CacheKind.downC), String.class, caches.get(CacheKind.downS));

        final var sut = Mockito.spy(new RWCachingStateFrame<Integer>(Optional.empty(), Character.class, String.class));

        doReturn(downstreamInternalCaches).when(downstreamFrame).getInternalCaches();
        doReturn(sutInternalCaches).when(sut).getInternalCaches();

        sut.updatesFromDownstream(downstreamFrame);

        verify(caches.get(CacheKind.thisC), times(1)).coalesceFrom(caches.get(CacheKind.downC));
        verify(caches.get(CacheKind.thisS), times(1)).coalesceFrom(caches.get(CacheKind.downS));
    }
}
