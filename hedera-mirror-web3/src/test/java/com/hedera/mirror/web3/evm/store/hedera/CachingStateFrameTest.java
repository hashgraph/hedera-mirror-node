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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.hedera.mirror.web3.evm.store.hedera.CachingStateFrame.CacheAccessIncorrectType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@SuppressWarnings(
        "java:S1192") // "define a constant instead of duplicating this literal" - worse readability if applied to small
// literals
class CachingStateFrameTest {

    // Test construction produces a correct instance

    /** A no-additional-behavior-at-all subclass of the abstract `CachingStateFrame`, suitable for constructor tests. */
    static class BareCachingStateFrame<K> extends CachingStateFrame<K> {

        public BareCachingStateFrame(
                @NonNull final Optional<CachingStateFrame<K>> upstreamFrame,
                @NonNull final Class<?>... klassesToCache) {
            super(upstreamFrame, klassesToCache);
        }

        @Override
        public void updatesFromDownstream(@NonNull final CachingStateFrame<K> childFrame) {}

        @NonNull
        @Override
        protected Optional<Object> getValue(
                @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key) {
            return Optional.empty();
        }

        @Override
        protected void setValue(
                @NonNull final Class<?> klass,
                @NonNull final UpdatableReferenceCache<K> cache,
                @NonNull final K key,
                @NonNull final Object value) {}

        @Override
        protected void deleteValue(
                @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key) {}
    }

    @Test
    void constructWithNoUpstream() {

        final var sut = new BareCachingStateFrame<Integer>(Optional.empty(), Integer.class);
        assertThat(sut.getUpstream()).isEmpty();
        assertThat(sut.height()).isEqualTo(1);
    }

    @Test
    void constructWithSingleUpstream() {
        final var us0 = new BareCachingStateFrame<Integer>(Optional.empty(), Integer.class);
        final var sut = new BareCachingStateFrame<Integer>(Optional.of(us0), Integer.class);

        assertThat(sut.getUpstream()).contains(us0);
        assertThat(sut.height()).isEqualTo(2);
    }

    @Test
    void constructWithTwoFramesUpstream() {
        final var us0 = new BareCachingStateFrame<Integer>(Optional.empty(), Integer.class);
        final var us1 = new BareCachingStateFrame<Integer>(Optional.of(us0), Integer.class);
        final var sut = new BareCachingStateFrame<Integer>(Optional.of(us1), Integer.class);

        assertThat(sut.getUpstream()).contains(us1);
        assertThat(sut.height()).isEqualTo(3);
    }

    @Test
    void constructWithNoValueTypes() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BareCachingStateFrame<Integer>(Optional.empty()));
    }

    @Test
    void constructWithOneTypeOfValues() {
        final var sut = new BareCachingStateFrame<Integer>(Optional.empty(), Integer.class);

        assertThat(sut.getAccessor(Integer.class)).isNotNull();
        assertThatExceptionOfType(CacheAccessIncorrectType.class).isThrownBy(() -> sut.getAccessor(String.class));
        assertThat(sut.getInternalCaches())
                .isNotNull()
                .hasSize(1)
                .containsOnlyKeys(Integer.class)
                .doesNotContainValue(null);
    }

    @Test
    void constructWithTwoTypesOfValues() {
        final var sut = new BareCachingStateFrame<Integer>(Optional.empty(), Integer.class, Character.class);

        assertThat(sut.getAccessor(Integer.class)).isNotNull();
        assertThat(sut.getAccessor(Character.class)).isNotNull();
        assertThatExceptionOfType(CacheAccessIncorrectType.class).isThrownBy(() -> sut.getAccessor(String.class));
        assertThat(sut.getInternalCaches())
                .isNotNull()
                .hasSize(2)
                .containsOnlyKeys(Integer.class, Character.class)
                .doesNotContainValue(null);
    }

    // Test accessors - not semantics of caching, but just that they pass through correctly to lower-level methods for
    // cache access

    /** A spying `CachingStateFrame` that records accesses to the methods underlying value accessors */
    static class RecordingCachingStateFrame<K> extends CachingStateFrame<K> {

        @NonNull
        final StringBuilder spy;

        public RecordingCachingStateFrame(
                @NonNull final StringBuilder spy,
                @NonNull Optional<CachingStateFrame<K>> upstreamFrame,
                @NonNull final Class<?>... klassesToCache) {
            super(upstreamFrame, klassesToCache);
            this.spy = spy;
        }

        @Override
        public void updatesFromDownstream(@NonNull final CachingStateFrame<K> childFrame) {
            spy.append("U;");
        }

        @NonNull
        @Override
        protected Optional<Object> getValue(
                @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key) {
            spy.append("%s-%s-%s;".formatted("G", klass.getSimpleName().charAt(0), key.toString()));
            return Optional.empty();
        }

        @Override
        protected void setValue(
                @NonNull final Class<?> klass,
                @NonNull final UpdatableReferenceCache<K> cache,
                @NonNull final K key,
                @NonNull final Object value) {
            spy.append(
                    "%s-%s-%s-%s;".formatted("S", klass.getSimpleName().charAt(0), key.toString(), value.toString()));
        }

        @Override
        protected void deleteValue(
                @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key) {
            spy.append("%s-%s-%s;".formatted("D", klass.getSimpleName().charAt(0), key.toString()));
        }
    }

    @Test
    void accessorForSingleType() {
        final var actual = new StringBuilder(500);
        final var sut = new RecordingCachingStateFrame<Integer>(actual, Optional.empty(), Integer.class);

        final var keys = List.<Integer>of(2, 4, 6, 8);
        final var values = List.<Integer>of(10, 20, 30, 40);
        assertThat(values).hasSize(keys.size());

        final var expected = new StringBuilder(500);
        final var sutAccessor = sut.getAccessor(Integer.class);
        for (int i = 0; i < keys.size(); i++) {
            final var key = keys.get(i);
            final var value = values.get(i);

            expected.append("S-I-%d-%d;D-I-%d;G-I-%d;".formatted(key, value, key, key));

            sutAccessor.set(key, value);
            sutAccessor.delete(key);
            sutAccessor.get(key);
        }

        assertThat(actual.toString()).hasToString(expected.toString());
    }

    @Test
    void accessorsForTwoTypes() {
        final var actual = new StringBuilder(500);
        final var sut =
                new RecordingCachingStateFrame<Integer>(actual, Optional.empty(), Integer.class, Character.class);

        final var keys = List.<Integer>of(2, 4, 6, 8);
        final var valuesI = List.<Integer>of(10, 20, 30, 40);
        final var valuesC = List.<Character>of('A', 'b', 'X', 'y');
        assertThat(valuesI).hasSize(keys.size());
        assertThat(valuesC).hasSize(keys.size());

        final var expected = new StringBuilder(500);
        final var sutAccessorI = sut.getAccessor(Integer.class);
        final var sutAccessorC = sut.getAccessor(Character.class);
        for (int i = 0; i < keys.size(); i++) {
            final var key = keys.get(i);
            final var valueI = valuesI.get(i);
            final var valueC = valuesC.get(i);

            expected.append("S-I-%d-%d;S-C-%d-%c;D-I-%d;D-C-%d;G-I-%d;G-C-%d;"
                    .formatted(key, valueI, key, valueC, key, key, key, key));

            sutAccessorI.set(key, valueI);
            sutAccessorC.set(key, valueC);
            sutAccessorI.delete(key);
            sutAccessorC.delete(key);
            sutAccessorI.get(key);
            sutAccessorC.get(key);
        }

        assertThat(actual.toString()).hasToString(expected.toString());
    }

    // Accessors detect type errors

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setFailsOnBadValueType() {
        final var sut = new BareCachingStateFrame<Integer>(Optional.empty(), Character.class);
        final var sutAccessorAsFetched = sut.getAccessor(Character.class);
        // N.B.: Really have to go out of your way - via unchecked casting - to violate the type safety
        final var sutAccessor =
                (CachingStateFrame.Accessor<Integer, String>) (CachingStateFrame.Accessor) sutAccessorAsFetched;

        assertThatExceptionOfType(CacheAccessIncorrectType.class).isThrownBy(() -> sutAccessor.set(7, "foobar"));
    }

    @Test
    void getFailsOnBadValueType() {
        final var sut = new BareCachingStateFrame<Integer>(Optional.empty(), Character.class) {
            @NonNull
            @Override
            protected Optional<Object> getValue(
                    @NonNull final Class<?> klass,
                    @NonNull final UpdatableReferenceCache<Integer> cache,
                    @NonNull final Integer key) {
                return Optional.of("foobar" /* returning String, not Character */);
            }
        };
        final var sutAccessor = sut.getAccessor(Character.class);

        assertThatExceptionOfType(CacheAccessIncorrectType.class).isThrownBy(() -> sutAccessor.get(7));
    }

    // Test commit properly updates upstream frames

    @Test
    void commitDoesNothingIfNoUpstreamFrames() {
        final var actual = new StringBuilder(500);
        final var sut = new RecordingCachingStateFrame<Integer>(actual, Optional.empty(), Integer.class);

        sut.commit();

        assertThat(actual).isEmpty();
    }

    @Test
    void commitCallsUpstreamUpdate() {
        final var actual = new StringBuilder(500);
        final var us0 = new RecordingCachingStateFrame<Integer>(actual, Optional.empty(), Integer.class);
        final var sut = new BareCachingStateFrame<Integer>(Optional.of(us0), Integer.class);

        sut.commit();

        assertThat(actual).hasToString("U;");
    }
}
