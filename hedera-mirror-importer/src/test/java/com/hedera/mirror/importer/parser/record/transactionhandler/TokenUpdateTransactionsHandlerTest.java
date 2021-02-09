package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;

import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.util.Utility;

public class TokenUpdateTransactionsHandlerTest extends AbstractUpdatesEntityTransactionHandlerTest {

    private final Key ADMIN_KEY = getKey("4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f");

    private static final Duration AUTO_RENEW_PERIOD = Duration.newBuilder().setSeconds(1).build();

    private static final Timestamp EXPIRATION_TIME = Utility.instantToTimestamp(Instant.now());

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenUpdateTransactionsHandler();
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        Key key = Key.newBuilder()
                .setEd25519(ByteString.copyFromUtf8("4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f"))
                .build();
        return TransactionBody.newBuilder()
                .setTokenUpdate(TokenUpdateTransactionBody.newBuilder()
                        .setToken(TokenID.newBuilder().setTokenNum(DEFAULT_ENTITY_NUM).build())
                        .setAdminKey(key)
                        .setExpiry(Timestamp.newBuilder().setSeconds(360).build())
                        .setKycKey(key)
                        .setFreezeKey(key)
                        .setSymbol("SYMBOL")
                        .setTreasury(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(1).build())
                        .setAutoRenewAccount(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(2)
                                .build())
                        .setAutoRenewPeriod(Duration.newBuilder().setSeconds(100))
                        .setName("token_name")
                        .setWipeKey(key)
                        .build());
    }

    @Override
    protected EntityTypeEnum getExpectedEntityIdType() {
        return EntityTypeEnum.TOKEN;
    }

    @Override
    ByteString getUpdateEntityTransactionBody() {
        return TransactionBody.newBuilder().setTokenUpdate(
                TokenUpdateTransactionBody.newBuilder()
                        .setAdminKey(ADMIN_KEY)
                        .setAutoRenewPeriod(AUTO_RENEW_PERIOD)
                        .setExpiry(EXPIRATION_TIME)
                        .build())
                .build().toByteString();
    }

    @Override
    void buildUpdateEntityExpectedEntity(Entities entity) {
        entity.setKey(ADMIN_KEY.toByteArray());
        entity.setAutoRenewPeriod(AUTO_RENEW_PERIOD.getSeconds());
        entity.setExpiryTimeNs(Utility.timestampInNanosMax(EXPIRATION_TIME));
    }
}
