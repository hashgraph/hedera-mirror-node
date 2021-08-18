package com.hedera.mirror.monitor.publish.transaction.consensus;

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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TopicUpdateTransaction;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class ConsensusUpdateTopicTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        ConsensusUpdateTopicTransactionSupplier consensusUpdateTopicTransactionSupplier =
                new ConsensusUpdateTopicTransactionSupplier();
        consensusUpdateTopicTransactionSupplier.setTopicId(TOPIC_ID.toString());
        TopicUpdateTransaction actual = consensusUpdateTopicTransactionSupplier.get();

        assertThat(actual)
                .returns(null, TopicUpdateTransaction::getAdminKey)
                .returns(null, TopicUpdateTransaction::getAutoRenewAccountId)
                .returns(null, TopicUpdateTransaction::getAutoRenewPeriod)
                .returns(MAX_TRANSACTION_FEE_HBAR, TopicUpdateTransaction::getMaxTransactionFee)
                .returns(null, TopicUpdateTransaction::getSubmitKey)
                .returns(TOPIC_ID, TopicUpdateTransaction::getTopicId)
                .satisfies(a -> assertThat(a.getTopicMemo()).contains("Mirror node updated test topic"));
    }

    @Test
    void createWithCustomData() {
        PublicKey key = PrivateKey.generate().getPublicKey();
        Duration autoRenewPeriod = Duration.ofSeconds(1);

        ConsensusUpdateTopicTransactionSupplier consensusUpdateTopicTransactionSupplier =
                new ConsensusUpdateTopicTransactionSupplier();
        consensusUpdateTopicTransactionSupplier.setAdminKey(key.toString());
        consensusUpdateTopicTransactionSupplier.setAutoRenewAccountId("0.0.2");
        consensusUpdateTopicTransactionSupplier.setAutoRenewPeriod(autoRenewPeriod);
        consensusUpdateTopicTransactionSupplier.setMaxTransactionFee(1);
        consensusUpdateTopicTransactionSupplier.setTopicId(TOPIC_ID.toString());
        TopicUpdateTransaction actual = consensusUpdateTopicTransactionSupplier.get();

        assertThat(actual)
                .returns(key, TopicUpdateTransaction::getAdminKey)
                .returns(AccountId.fromString("0.0.2"), TopicUpdateTransaction::getAutoRenewAccountId)
                .returns(autoRenewPeriod, TopicUpdateTransaction::getAutoRenewPeriod)
                .returns(ONE_TINYBAR, TopicUpdateTransaction::getMaxTransactionFee)
                .returns(key, TopicUpdateTransaction::getSubmitKey)
                .returns(TOPIC_ID, TopicUpdateTransaction::getTopicId)
                .satisfies(a -> assertThat(a.getTopicMemo()).contains("Mirror node updated test topic"));
    }
}
