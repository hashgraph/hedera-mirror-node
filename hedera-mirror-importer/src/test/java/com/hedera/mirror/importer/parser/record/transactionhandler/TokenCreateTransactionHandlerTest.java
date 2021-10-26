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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;

import com.hedera.mirror.importer.domain.EntityTypeEnum;

class TokenCreateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenCreateTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setTokenCreation(TokenCreateTransactionBody.newBuilder()
                        .setAdminKey(DEFAULT_KEY)
                        .setAutoRenewAccount(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(2)
                                .build())
                        .setAutoRenewPeriod(Duration.newBuilder().setSeconds(100))
                        .setDecimals(1000)
                        .setExpiry(Timestamp.newBuilder().setSeconds(360))
                        .setFreezeDefault(false)
                        .setFreezeKey(DEFAULT_KEY)
                        .setInitialSupply(1_000_000L)
                        .setKycKey(DEFAULT_KEY)
                        .setMemo(DEFAULT_MEMO)
                        .setName("token_name")
                        .setSymbol("SYMBOL")
                        .setTreasury(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(1).build())
                        .setWipeKey(DEFAULT_KEY)
                        .build());
    }

    @Override
    protected TransactionReceipt.Builder getTransactionReceipt(ResponseCodeEnum responseCodeEnum) {
        return TransactionReceipt.newBuilder().setStatus(responseCodeEnum)
                .setTokenID(TokenID.newBuilder().setTokenNum(DEFAULT_ENTITY_NUM).build());
    }

    @Override
    protected EntityTypeEnum getExpectedEntityIdType() {
        return EntityTypeEnum.TOKEN;
    }
}
