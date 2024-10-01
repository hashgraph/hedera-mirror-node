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

import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import com.swirlds.state.spi.ReadableKVStateBase;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;

/**
 * This class serves as a repository layer between hedera app services read only state and the Postgres database in
 * mirror-node The object, which is read from DB is converted to the PBJ generated format, so that it can properly be
 * utilized by the hedera app components
 */
@Named
public class NftReadableKVState extends ReadableKVStateBase<NftID, Nft> {

    private static final String KEY = "NFTS";

    private final NftRepository nftRepository;
    private final CommonEntityAccessor commonEntityAccessor;

    public NftReadableKVState(
            @Nonnull NftRepository nftRepository, @Nonnull CommonEntityAccessor commonEntityAccessor) {
        super(KEY);
        this.nftRepository = nftRepository;
        this.commonEntityAccessor = commonEntityAccessor;
    }

    @Override
    protected Nft readFromDataSource(@Nonnull final NftID key) {
        if (key.tokenId() == null) {
            return null;
        }

        final Optional<Long> optionalTimestamp = ContractCallContext.get().getTimestamp();
        final var entity =
                commonEntityAccessor.get(key.tokenId(), optionalTimestamp).orElse(null);

        if (entity == null) {
            return null;
        }

        return optionalTimestamp
                .map(t -> nftRepository.findActiveByIdAndTimestamp(key.tokenId().tokenNum(), key.serialNumber(), t))
                .orElseGet(() -> nftRepository.findActiveById(key.tokenId().tokenNum(), key.serialNumber()))
                .map(nft -> mapToNft(nft, key.tokenId()))
                .get();
    }

    @Nonnull
    @Override
    protected Iterator<NftID> iterateFromDataSource() {
        return Collections.emptyIterator();
    }

    @Override
    public long size() {
        return 0;
    }

    private Nft mapToNft(final com.hedera.mirror.common.domain.token.Nft nft, final TokenID tokenID) {
        return new Nft(
                new NftID(
                        new TokenID(tokenID.shardNum(), tokenID.realmNum(), tokenID.tokenNum()), nft.getSerialNumber()),
                EntityIdUtils.toAccountId(nft.getAccountId().getId()),
                EntityIdUtils.toAccountId(nft.getSpender().getId()),
                convertToTimestamp(nft.getCreatedTimestamp()),
                Bytes.wrap(nft.getMetadata()),
                null,
                null);
    }

    /**
     * Converts a timestamp in milliseconds to a PBJ Timestamp object.
     *
     * @param timestamp The timestamp in milliseconds.
     * @return The PBJ Timestamp object.
     */
    private Timestamp convertToTimestamp(final long timestamp) {
        var instant = Instant.ofEpochMilli(timestamp);
        return new Timestamp(instant.getEpochSecond(), instant.getNano());
    }
}
