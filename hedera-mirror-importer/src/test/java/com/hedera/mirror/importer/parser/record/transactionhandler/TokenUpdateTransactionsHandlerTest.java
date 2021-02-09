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
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.util.Utility;

public class TokenUpdateTransactionsHandlerTest extends AbstractUpdatesEntityTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenUpdateTransactionsHandler();
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setTokenUpdate(TokenUpdateTransactionBody.newBuilder()
                        .setToken(TokenID.newBuilder().setTokenNum(DEFAULT_ENTITY_NUM).build())
                        .setAdminKey(DEFAULT_KEY)
                        .setExpiry(Timestamp.newBuilder().setSeconds(360).build())
                        .setKycKey(DEFAULT_KEY)
                        .setFreezeKey(DEFAULT_KEY)
                        .setSymbol("SYMBOL")
                        .setTreasury(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(1).build())
                        .setAutoRenewAccount(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(2)
                                .build())
                        .setAutoRenewPeriod(Duration.newBuilder().setSeconds(100))
                        .setName("token_name")
                        .setWipeKey(DEFAULT_KEY)
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
                        .setAdminKey(DEFAULT_KEY)
                        .setAutoRenewPeriod(DEFAULT_AUTO_RENEW_PERIOD)
                        .setExpiry(DEFAULT_EXPIRATION_TIME)
                        .build())
                .build().toByteString();
    }

    @Override
    void buildUpdateEntityExpectedEntity(Entities entity) {
        entity.setAutoRenewPeriod(DEFAULT_AUTO_RENEW_PERIOD.getSeconds());
        entity.setExpiryTimeNs(Utility.timestampInNanosMax(DEFAULT_EXPIRATION_TIME));
        entity.setKey(DEFAULT_KEY.toByteArray());
    }
}
