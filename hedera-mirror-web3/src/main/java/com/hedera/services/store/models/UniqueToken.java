/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.hyperledger.besu.datatypes.Address;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.state.submerkle.RichInstant;

/**
 * Encapsulates the state and operations of a Hedera Unique token.
 *
 * <p>Operations are validated, and throw a {@link InvalidTransactionException} with response code
 * capturing the failure when one occurs. This model is used as Value in State, which is used as speculative rights
 * operations.
 */
@Value
@Builder(toBuilder = true)
public class UniqueToken {
    private Id tokenId;
    private Address address;
    private long serialNumber;
    private RichInstant creationTime;
    private Id owner;
    private Id spender;
    private byte[] metadata;
    private NftId nftId;

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
