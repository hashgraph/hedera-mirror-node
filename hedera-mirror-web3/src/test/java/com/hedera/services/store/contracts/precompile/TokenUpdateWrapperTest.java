/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.createNonFungibleTokenUpdateWrapperWithKeys;
import static com.hedera.services.utils.EntityIdUtils.contractIdFromEvmAddress;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.StringValue;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import org.junit.jupiter.api.Test;

import com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenKeyWrapper;

class TokenUpdateWrapperTest {
    @Test
    void createsExpectedTokenUpdateCallForNonFungible() {
        // given
        final var ComplexKey = new KeyValueWrapper(
                false, null, new byte[] {}, new byte[] {}, contractIdFromEvmAddress(contractAddress.toArrayUnsafe()));
        final var multiKey = new KeyValueWrapper(
                false, contractIdFromEvmAddress(contractAddress.toArrayUnsafe()), new byte[] {}, new byte[] {}, null);
        final var wrapper = createNonFungibleTokenUpdateWrapperWithKeys(List.of(
                new TokenKeyWrapper(112, multiKey),
                new TokenKeyWrapper(2, ComplexKey),
                new TokenKeyWrapper(4, ComplexKey),
                new TokenKeyWrapper(8, ComplexKey)));

        // when
        final var result = createTokenUpdate(wrapper);
        final var txnBody = result.build().getTokenUpdate();

        // then
        assertEquals(multiKey.asGrpc(), txnBody.getSupplyKey());
        assertEquals(multiKey.asGrpc(), txnBody.getFeeScheduleKey());
        assertEquals(multiKey.asGrpc(), txnBody.getPauseKey());
    }

    private TransactionBody.Builder createTokenUpdate(final TokenUpdateWrapper updateWrapper) {
        final var builder = TokenUpdateTransactionBody.newBuilder();
        builder.setToken(updateWrapper.tokenID());

        if (updateWrapper.name() != null) {
            builder.setName(updateWrapper.name());
        }
        if (updateWrapper.symbol() != null) {
            builder.setSymbol(updateWrapper.symbol());
        }
        if (updateWrapper.memo() != null) {
            builder.setMemo(StringValue.of(updateWrapper.memo()));
        }
        if (updateWrapper.treasury() != null) {
            builder.setTreasury(updateWrapper.treasury());
        }

        if (updateWrapper.expiry().second() != 0) {
            builder.setExpiry(Timestamp.newBuilder()
                    .setSeconds(updateWrapper.expiry().second())
                    .build());
        }
        if (updateWrapper.expiry().autoRenewAccount() != null) {
            builder.setAutoRenewAccount(updateWrapper.expiry().autoRenewAccount());
        }
        if (updateWrapper.expiry().autoRenewPeriod() != 0) {
            builder.setAutoRenewPeriod(
                    Duration.newBuilder().setSeconds(updateWrapper.expiry().autoRenewPeriod()));
        }

        return checkTokenKeysTypeAndBuild(updateWrapper.tokenKeys(), builder);
    }

    private TransactionBody.Builder checkTokenKeysTypeAndBuild(
            final List<TokenKeyWrapper> tokenKeys, final TokenUpdateTransactionBody.Builder builder) {
        tokenKeys.forEach(tokenKeyWrapper -> {
            final var key = tokenKeyWrapper.key().asGrpc();
            if (tokenKeyWrapper.isUsedForAdminKey()) {
                builder.setAdminKey(key);
            }
            if (tokenKeyWrapper.isUsedForKycKey()) {
                builder.setKycKey(key);
            }
            if (tokenKeyWrapper.isUsedForFreezeKey()) {
                builder.setFreezeKey(key);
            }
            if (tokenKeyWrapper.isUsedForWipeKey()) {
                builder.setWipeKey(key);
            }
            if (tokenKeyWrapper.isUsedForSupplyKey()) {
                builder.setSupplyKey(key);
            }
            if (tokenKeyWrapper.isUsedForFeeScheduleKey()) {
                builder.setFeeScheduleKey(key);
            }
            if (tokenKeyWrapper.isUsedForPauseKey()) {
                builder.setPauseKey(key);
            }
        });

        return TransactionBody.newBuilder().setTokenUpdate(builder);
    }
}
