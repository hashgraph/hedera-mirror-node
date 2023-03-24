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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Test spy that implements an UpdatableReferenceCache with K = String. Only new
 * behavior added - **no existing behavior overridden** so you can be sure it
 * doesn't _change_ the behavior of the UpdatableReferenceCache you're testing. It
 * merely allows you to set/inspect the internal state at will.
 */
public class UpdatableReferenceCacheSpy extends UpdatableReferenceCache<String> {

    public static final Class<Long> VALUE_CLASS = Long.class;

    public UpdatableReferenceCacheSpy() {}

    @NonNull
    public UpdatableReferenceCacheSpy addToOriginal(@NonNull final String k, final long v) {
        original.put(k, v);
        return this;
    }

    @NonNull
    public UpdatableReferenceCacheSpy addNullToOriginal(@NonNull final String k) {
        original.put(k, null);
        return this;
    }

    @NonNull
    public UpdatableReferenceCacheSpy clearOriginal() {
        original.clear();
        return this;
    }

    @NonNull
    public Map<String, Object> getOriginal() {
        return original;
    }

  @NonNull
  public UpdatableReferenceCacheSpy addToCurrent(@NonNull final String k, final long v) {
        current.put(k, v);
        return this;
    }

  @NonNull
  public UpdatableReferenceCacheSpy addNullToCurrent(@NonNull final String k) {
        current.put(k, null);
        return this;
    }

  @NonNull
  public UpdatableReferenceCacheSpy clearCurrent() {
        current.clear();
        return this;
    }

  @NonNull
  public Map<String, Object> getCurrent() {
        return current;
    }

    public static record Counts(int read, int updated, int deleted) {
        public static Counts of(int r, int u, int d) {
            return new Counts(r, u, d);
        }
    }

    /** Returns (for tests) a triple of #original accounts, #added accounts, #deleted accounts */
    public @NonNull Counts getCounts() {
        return Counts.of(
                original.size(),
                (int) current.values().stream().filter(Objects::nonNull).count(),
                (int) current.values().stream().filter(Objects::isNull).count());
    }

    public enum Type {
        READ_ONLY,
        UPDATED,
        CURRENT_STATE
    }

    /**
     * Returns the current cache state for reads (from parent caches) or the current adds or the
     * current deletes
     */
    public @NonNull Map<String, Long> getCacheState(@NonNull final Type type) {
        Objects.requireNonNull(type, "type");

        final Function<Map<String, Object>, Map<String, Long>> copyAndCast =
                m -> m.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, VALUE_CLASS::cast));

        return switch (type) {
            case READ_ONLY -> copyAndCast.apply(original);
            case UPDATED -> copyAndCast.apply(current);
            case CURRENT_STATE -> {
                final var r = copyAndCast.apply(original);
                current.forEach((k, v) -> r.put(k, (Long) v));
                yield r;
            }
        };
    }
}
