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

import static com.hedera.mirror.web3.utils.MiscUtilities.requireAllNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;

/** A CachingStateFrame that answers "missing" for all reads and disallows all updates/writes. */
public class BottomCachingStateFrame<Address> extends CachingStateFrame<Address> {

    public BottomCachingStateFrame(
            @NonNull final Optional<CachingStateFrame<Address>> upstreamFrame,
            @NonNull final Class<?>... klassesToCache) {
        super(upstreamFrame, klassesToCache);
        upstreamFrame.ifPresent(dummy -> {
            throw new UnsupportedOperationException("bottom cache can not have an upstream cache");
        });
    }

    @Override
    public @NonNull Optional<Object> getEntity(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<Address> cache,
            @NonNull final Address address) {
        requireAllNonNull(klass, "klass", cache, "cache", address, "address");
        return Optional.empty();
    }

    @Override
    public void setEntity(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<Address> cache,
            @NonNull final Address address,
            @NonNull final Object entity) {
        requireAllNonNull(klass, "klass", cache, "cache", address, "address", entity, "entity");
        throw new UnsupportedOperationException("cannot write to a bottom cache");
    }

    @Override
    public void deleteEntity(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<Address> cache,
            @NonNull final Address address) {
        requireAllNonNull(klass, "klass", cache, "cache", address, "address");
        throw new UnsupportedOperationException("cannot delete from a bottom cache");
    }

    @Override
    public void updatesFromDownstream(@NonNull final CachingStateFrame<Address> childFrame) {
        Objects.requireNonNull(childFrame, "childFrame");
        // ok to commit but does nothing
    }
}
