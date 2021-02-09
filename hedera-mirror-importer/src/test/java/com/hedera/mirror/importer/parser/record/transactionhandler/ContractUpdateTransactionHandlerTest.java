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
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.util.Utility;

class ContractUpdateTransactionHandlerTest extends AbstractUpdatesEntityTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ContractUpdateTransactionHandler();
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setContractUpdateInstance(ContractUpdateTransactionBody.newBuilder()
                        .setContractID(ContractID.newBuilder().setContractNum(DEFAULT_ENTITY_NUM).build()));
    }

    @Override
    protected EntityTypeEnum getExpectedEntityIdType() {
        return EntityTypeEnum.CONTRACT;
    }

    @Override
    ByteString getUpdateEntityTransactionBody() {
        return TransactionBody.newBuilder().setContractUpdateInstance(
                ContractUpdateTransactionBody.newBuilder()
                        .setAdminKey(DEFAULT_KEY)
                        .setAutoRenewPeriod(DEFAULT_AUTO_RENEW_PERIOD)
                        .setExpirationTime(DEFAULT_EXPIRATION_TIME)
                        .setMemo(DEFAULT_MEMO)
                        .build())
                .build().toByteString();
    }

    @Override
    void buildUpdateEntityExpectedEntity(Entities entity) {
        entity.setAutoRenewPeriod(DEFAULT_AUTO_RENEW_PERIOD.getSeconds());
        entity.setExpiryTimeNs(Utility.timestampInNanosMax(DEFAULT_EXPIRATION_TIME));
        entity.setKey(DEFAULT_KEY.toByteArray());
        entity.setMemo(DEFAULT_MEMO);
    }
}
