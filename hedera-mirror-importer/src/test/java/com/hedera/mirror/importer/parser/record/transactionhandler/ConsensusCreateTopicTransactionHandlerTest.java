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
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;

import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.EntityTypeEnum;

class ConsensusCreateTopicTransactionHandlerTest extends AbstractUpdatesEntityTransactionHandlerTest {

    private final static Key ADMIN_KEY = getKey("4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f");

    private static final Duration AUTO_RENEW_PERIOD = Duration.newBuilder().setSeconds(1).build();

    private static final String MEMO = "consensusCreateTopicMemo";

    private final static Key SUBMIT_KEY = getKey("5a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96G");

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ConsensusCreateTopicTransactionHandler();
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setConsensusCreateTopic(ConsensusCreateTopicTransactionBody.getDefaultInstance());
    }

    @Override
    protected TransactionRecord.Builder getDefaultTransactionRecord() {
        return TransactionRecord.newBuilder()
                .setReceipt(TransactionReceipt.newBuilder()
                        .setTopicID(TopicID.newBuilder().setTopicNum(DEFAULT_ENTITY_NUM).build()));
    }

    @Override
    protected EntityTypeEnum getExpectedEntityIdType() {
        return EntityTypeEnum.TOPIC;
    }

    @Override
    protected ByteString getUpdateEntityTransactionBody() {
        return TransactionBody.newBuilder().setConsensusCreateTopic(
                ConsensusCreateTopicTransactionBody.newBuilder()
                        .setAdminKey(ADMIN_KEY)
                        .setAutoRenewPeriod(AUTO_RENEW_PERIOD)
                        .setMemo(MEMO)
                        .setSubmitKey(SUBMIT_KEY)
                        .build())
                .build().toByteString();
    }

    @Override
    protected void buildUpdateEntityExpectedEntity(Entities entity) {
        entity.setKey(ADMIN_KEY.toByteArray());
        entity.setAutoRenewPeriod(AUTO_RENEW_PERIOD.getSeconds());
        entity.setSubmitKey(SUBMIT_KEY.toByteArray());
        entity.setMemo(MEMO);
    }
}
