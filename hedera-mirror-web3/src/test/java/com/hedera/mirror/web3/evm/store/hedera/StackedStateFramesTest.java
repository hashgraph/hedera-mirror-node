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

import com.hedera.mirror.web3.evm.store.hedera.UpdatableReferenceCache.Counts;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.Test;

class StackedStateFramesTest {

    record Address(int n) {}

    record Account(int a, int n) {}

    record Token(int a, int n) {}

    int lastValidAddress = 0;
    int lastInvalidAddress = 0;

    @NonNull
    final Set<Integer> issuedAddresses = new HashSet<>();

    int lastEntity = 0;

    @NonNull
    final Set<Integer> addressesOfIssuedEntities = new HashSet<>();

    static class NonCachedReadOfAddressException extends RuntimeException {
        public NonCachedReadOfAddressException(final String message) {
            super(message);
        }
    }

    enum AddressType {
        INVALID,
        VALID
    }

    Address createAddress(final AddressType type) {
        final var address = type == AddressType.VALID ? ++lastValidAddress : --lastInvalidAddress;
        issuedAddresses.add(address);
        return new Address(address);
    }

    class AccountAccessor implements Accessor<Address, Account> {
        @Override
        public Optional<Account> get(final Address key) {
            if (addressesOfIssuedEntities.contains(key.n)) {
                // Error: Should never be asking for the same address twice because of caching!
                throw new NonCachedReadOfAddressException("trying to get new Account for address %d".formatted(key.n));
            }
            final var account = ++lastEntity;
            addressesOfIssuedEntities.add(key.n);
            return key.n >= 0 ? Optional.of(new Account(key.n, account)) : Optional.empty();
        }
    }

    class TokenAccessor implements Accessor<Address, Token> {
        @Override
        public Optional<Token> get(final Address key) {
            if (addressesOfIssuedEntities.contains(key.n)) {
                // Error: Should never be asking for the same address twice because of caching!
                throw new NonCachedReadOfAddressException("trying to get new Token for address %d".formatted(key.n));
            }
            final var account = ++lastEntity;
            addressesOfIssuedEntities.add(key.n);
            return key.n >= 0 ? Optional.of(new Token(key.n, account)) : Optional.empty();
        }
    }

    Pair<Accessor<Address, Account>, Accessor<Address, Token>> getFakeDBAccessors() {
        return Pair.of(new AccountAccessor(), new TokenAccessor());
    }

    @Test
    void constructorTest() {
        // On construction there should be no accounts and no tokens present, updated, or deleted.  There should be
        // two layers - the R/O layer on top of the DB layer.

        final var sut = new StackedStateFrames<>(getFakeDBAccessors(), Account.class, Token.class);
        assertThat(sut.cachedFramesDepth()).isEqualTo(2);
        assertThat(sut.height()).isZero();

        // TOS is the RO layer
        assertThat(sut.top()).isNotNull().isInstanceOf(ROCachingStateFrame.class);
        final var stack0 = sut.top();
        final var stack0AccountCache = stack0.accountCache;
        assertThat(stack0AccountCache).isNotNull();
        assertThat(stack0AccountCache.getCounts()).isEqualTo(Counts.of(0, 0, 0));
        final var stack0TokenCache = stack0.tokenCache;
        assertThat(stack0TokenCache).isNotNull();
        assertThat(stack0TokenCache.getCounts()).isEqualTo(Counts.of(0, 0, 0));

        // TOS-1 is the DB layer, "mocked" here
        final var stack1 = stack0.next().orElse(null);
        assertThat(stack1).isNotNull().isInstanceOf(DatabaseBackedStateFrame.class);
        assertThat(Triple.of(lastValidAddress, lastInvalidAddress, lastEntity)).isEqualTo(Triple.of(0, 0, 0));
    }

    @Test
    void singleRWLayerTest() {
        // With a single RW layer there should be caching and updating (and deleting) going on.

        final var sut = new StackedStateFrames<>(getFakeDBAccessors(), Account.class, Token.class);
        sut.push();
        assertThat(sut.cachedFramesDepth()).isEqualTo(3);
        assertThat(sut.height()).isEqualTo(1);

        // TOS is the RW layer and currently has nothing in it
        assertThat(sut.top()).isNotNull().isInstanceOf(RWCachingStateFrame.class);
        final var stack0 = sut.top();
        final var stack0AccountCache = stack0.accountCache;
        assertThat(stack0AccountCache).isNotNull();
        assertThat(stack0AccountCache.getCounts()).isEqualTo(Counts.of(0, 0, 0));
        final var stack0TokenCache = stack0.tokenCache;
        assertThat(stack0TokenCache).isNotNull();
        assertThat(stack0TokenCache.getCounts()).isEqualTo(Counts.of(0, 0, 0));
    }

    void verifyAccountAndTokenCacheCounts(
            final CachingStateFrame<Address, Account, Token> sut,
            final UpdatableReferenceCache.Counts accounts,
            final UpdatableReferenceCache.Counts tokens) {}
}
