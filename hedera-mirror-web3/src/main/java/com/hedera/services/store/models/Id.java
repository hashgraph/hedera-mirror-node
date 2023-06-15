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

import static com.hedera.services.utils.EntityIdUtils.asHexedEvmAddress;
import static com.hedera.services.utils.MiscUtils.perm64;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.Comparator;
import org.hyperledger.besu.datatypes.Address;

/**
 * Copied Id type from hedera-services.
 *
 * Represents the id of a Hedera entity (account, topic, token, contract, file, or schedule).
 */
public record Id(long shard, long realm, long num) {
    public static final Id DEFAULT = new Id(0, 0, 0);
    public static final Comparator<Id> ID_COMPARATOR =
            Comparator.comparingLong(Id::num).thenComparingLong(Id::shard).thenComparingLong(Id::realm);

    public static Id fromGrpcAccount(final AccountID id) {
        return new Id(id.getShardNum(), id.getRealmNum(), id.getAccountNum());
    }

    public static Id fromGrpcContract(final ContractID id) {
        return new Id(id.getShardNum(), id.getRealmNum(), id.getContractNum());
    }

    public static Id fromGrpcToken(final TokenID id) {
        return new Id(id.getShardNum(), id.getRealmNum(), id.getTokenNum());
    }

    public AccountID asGrpcAccount() {
        return AccountID.newBuilder()
                .setShardNum(shard)
                .setRealmNum(realm)
                .setAccountNum(num)
                .build();
    }

    public TokenID asGrpcToken() {
        return TokenID.newBuilder()
                .setShardNum(shard)
                .setRealmNum(realm)
                .setTokenNum(num)
                .build();
    }

    public ContractID asGrpcContract() {
        return ContractID.newBuilder()
                .setShardNum(shard)
                .setRealmNum(realm)
                .setContractNum(num)
                .build();
    }

    @Override
    public int hashCode() {
        return (int) perm64(perm64(perm64(shard) ^ realm) ^ num);
    }

    @Override
    public String toString() {
        return String.format("%d.%d.%d", shard, realm, num);
    }

    /**
     * Returns the EVM representation of the Account
     *
     * @return {@link Address} evm representation
     */
    public Address asEvmAddress() {
        return Address.fromHexString(asHexedEvmAddress(this));
    }
}
