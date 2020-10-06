package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;

import com.hedera.mirror.importer.domain.EntityTypeEnum;

public class TokenCreateTransactionsHandlerTest extends AbstractTransactionHandlerTest {
    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenCreateTransactionsHandler();
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        Key key = Key.newBuilder()
                .setEd25519(ByteString.copyFromUtf8("4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f"))
                .build();
        return TransactionBody.newBuilder()
                .setTokenCreation(TokenCreateTransactionBody.newBuilder()
                        .setAdminKey(key)
                        .setDecimals(1000)
                        .setExpiry(360)
                        .setInitialSupply(1_000_000L)
                        .setFreezeDefault(false)
                        .setKycKey(key)
                        .setAutoRenewPeriod(100)
                        .setName("_token_name")
                        .setFreezeKey(key)
                        .setSymbol("SYMBOL")
                        .setTreasury(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(1).build())
                        .setAutoRenewAccount(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(2)
                                .build())
                        .setAutoRenewPeriod(100)
                        .setName("token_name")
                        .setWipeKey(key)
                        .build());
    }

    @Override
    protected TransactionRecord.Builder getDefaultTransactionRecord() {
        return TransactionRecord.newBuilder()
                .setReceipt(TransactionReceipt.newBuilder()
                        .setTokenId(TokenID.newBuilder().setTokenNum(DEFAULT_ENTITY_NUM).build()));
    }

    @Override
    protected EntityTypeEnum getExpectedEntityIdType() {
        return EntityTypeEnum.TOKEN;
    }
}
