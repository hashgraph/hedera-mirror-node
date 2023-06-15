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

package com.hedera.services.store.models;

import com.google.common.base.MoreObjects;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.state.submerkle.RichInstant;
import org.hyperledger.besu.datatypes.Address;

/**
 * Copied model from hedera-services.
 *
 * Encapsulates the state and operations of a Hedera Unique token.
 *
 * <p>Operations are validated, and throw a {@link InvalidTransactionException} with response code
 * capturing the failure when one occurs. This model is used as a value in a special state, used for speculative write
 * operations.
 *
 * Differences from the original:
 *  1. Added address field for convenience
 *  2. Added factory method that returns empty instance
 */
public class UniqueToken {
    private final Id tokenId;

    private final Address address;
    private final long serialNumber;
    private final RichInstant creationTime;
    private final Id owner;
    private final Id spender;
    private final byte[] metadata;
    private final NftId nftId;

    public UniqueToken(Id tokenId, long serialNumber, RichInstant creationTime, Id owner, Id spender, byte[] metadata) {
        this.tokenId = tokenId;
        this.address = tokenId.asEvmAddress();
        this.serialNumber = serialNumber;
        this.creationTime = creationTime;
        this.owner = owner;
        this.spender = spender;
        this.metadata = metadata;
        this.nftId = new NftId(tokenId.shard(), tokenId.realm(), tokenId.num(), serialNumber);
    }

    public static UniqueToken getEmptyUniqueToken() {
        return new UniqueToken(Id.DEFAULT, 0L, RichInstant.MISSING_INSTANT, Id.DEFAULT, Id.DEFAULT, new byte[0]);
    }

    public NftId getNftId() {
        return nftId;
    }

    public Id getTokenId() {
        return tokenId;
    }

    public Address getAddress() {
        return address;
    }

    public long getSerialNumber() {
        return serialNumber;
    }

    public RichInstant getCreationTime() {
        return creationTime;
    }

    public Id getOwner() {
        return owner;
    }

    public Id getSpender() {
        return spender;
    }

    public byte[] getMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("tokenID", tokenId)
                .add("serialNum", serialNumber)
                .add("metadata", metadata)
                .add("creationTime", creationTime)
                .add("owner", owner)
                .add("spender", spender)
                .toString();
    }
}
