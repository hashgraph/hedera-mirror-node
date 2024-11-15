/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.state;

import static com.hedera.services.utils.EntityIdUtils.toEntityId;

import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.PendingAirdropValue;
import com.hedera.hapi.node.state.token.AccountPendingAirdrop;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.repository.TokenAirdropRepository;
import com.swirlds.state.spi.ReadableKVStateBase;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.Iterator;

@Named
public class AirdropsReadableKVState extends ReadableKVStateBase<PendingAirdropId, AccountPendingAirdrop> {

    private final TokenAirdropRepository tokenAirdropRepository;

    protected AirdropsReadableKVState(final TokenAirdropRepository tokenAirdropRepository) {
        super("PENDING_AIRDROPS");
        this.tokenAirdropRepository = tokenAirdropRepository;
    }

    @Override
    protected AccountPendingAirdrop readFromDataSource(@Nonnull PendingAirdropId key) {
        final var senderId = toEntityId(key.senderId()).getId();
        final var receiverId = toEntityId(key.receiverId()).getId();
        final var tokenId = toEntityId(
                        key.hasNonFungibleToken() ? key.nonFungibleToken().tokenId() : key.fungibleTokenType())
                .getId();
        final var serialNumber =
                key.hasNonFungibleToken() ? key.nonFungibleToken().serialNumber() : 0L;
        final var timestamp = ContractCallContext.getTimestamp();

        return timestamp
                .map(t -> tokenAirdropRepository.findByIdAndTimestamp(senderId, receiverId, tokenId, serialNumber, t))
                .orElseGet(() -> tokenAirdropRepository.findById(senderId, receiverId, tokenId, serialNumber))
                .map(tokenAirdrop -> key.hasNonFungibleToken()
                        ? AccountPendingAirdrop.DEFAULT
                        : mapToAccountPendingAirdrop(tokenAirdrop.getAmount()))
                .orElse(null);
    }

    @Nonnull
    @Override
    protected Iterator<PendingAirdropId> iterateFromDataSource() {
        return Collections.emptyIterator();
    }

    @Override
    public long size() {
        return 0;
    }

    private AccountPendingAirdrop mapToAccountPendingAirdrop(final long amount) {
        return AccountPendingAirdrop.newBuilder()
                .pendingAirdropValue(mapToPendingAirdropValue(amount))
                .build();
    }

    private PendingAirdropValue mapToPendingAirdropValue(final long amount) {
        return PendingAirdropValue.newBuilder().amount(amount).build();
    }
}
