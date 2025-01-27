/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.state.keyvalue;

import static com.hedera.mirror.web3.state.Utils.convertToTimestamp;

import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;

/**
 * This class serves as a repository layer between hedera app services read only state and the Postgres database in
 * mirror-node The object, which is read from DB is converted to the PBJ generated format, so that it can properly be
 * utilized by the hedera app components
 */
@Named
public class NftReadableKVState extends AbstractReadableKVState<NftID, Nft> {

    public static final String KEY = "NFTS";
    private final NftRepository nftRepository;

    public NftReadableKVState(@Nonnull NftRepository nftRepository) {
        super(KEY);
        this.nftRepository = nftRepository;
    }

    @Override
    protected Nft readFromDataSource(@Nonnull final NftID key) {
        if (key.tokenId() == null) {
            return null;
        }

        final var timestamp = ContractCallContext.get().getTimestamp();
        final var nftId = EntityIdUtils.toEntityId(key.tokenId()).getId();
        return timestamp
                .map(t -> nftRepository.findActiveByIdAndTimestamp(nftId, key.serialNumber(), t))
                .orElseGet(() -> nftRepository.findActiveById(nftId, key.serialNumber()))
                .map(nft -> mapToNft(nft, key.tokenId()))
                .orElse(null);
    }

    private Nft mapToNft(final com.hedera.mirror.common.domain.token.Nft nft, final TokenID tokenID) {
        return Nft.newBuilder()
                .metadata(Bytes.wrap(nft.getMetadata()))
                .mintTime(convertToTimestamp(nft.getCreatedTimestamp()))
                .nftId(new NftID(tokenID, nft.getSerialNumber()))
                .ownerId(EntityIdUtils.toAccountId(nft.getAccountId()))
                .spenderId(EntityIdUtils.toAccountId(nft.getSpender()))
                .build();
    }
}
