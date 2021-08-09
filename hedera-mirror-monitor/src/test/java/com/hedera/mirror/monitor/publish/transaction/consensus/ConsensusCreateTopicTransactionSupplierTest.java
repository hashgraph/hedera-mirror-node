package com.hedera.mirror.monitor.publish.transaction.consensus;

/*
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TopicCreateTransaction;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class ConsensusCreateTopicTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        ConsensusCreateTopicTransactionSupplier consensusCreateTopicTransactionSupplier =
                new ConsensusCreateTopicTransactionSupplier();
        TopicCreateTransaction actual = consensusCreateTopicTransactionSupplier.get();

        TopicCreateTransaction expected = new TopicCreateTransaction()
                .setMaxTransactionFee(MAX_TRANSACTION_FEE_HBAR)
                .setTopicMemo(actual.getTopicMemo())
                .setTransactionMemo(actual.getTransactionMemo());

        assertAll(
                () -> assertThat(actual.getTopicMemo()).contains("Mirror node created test topic"),
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node created test topic"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }

    @Test
    void createWithCustomData() {
        PublicKey key = PrivateKey.generate().getPublicKey();

        ConsensusCreateTopicTransactionSupplier consensusCreateTopicTransactionSupplier =
                new ConsensusCreateTopicTransactionSupplier();
        consensusCreateTopicTransactionSupplier.setAdminKey(key.toString());
        consensusCreateTopicTransactionSupplier.setAutoRenewAccountId(ACCOUNT_ID.toString());
        consensusCreateTopicTransactionSupplier.setMaxTransactionFee(1);
        TopicCreateTransaction actual = consensusCreateTopicTransactionSupplier.get();

        TopicCreateTransaction expected = new TopicCreateTransaction()
                .setAdminKey(key)
                .setAutoRenewAccountId(ACCOUNT_ID)
                .setMaxTransactionFee(ONE_TINYBAR)
                .setSubmitKey(key)
                .setTopicMemo(actual.getTopicMemo())
                .setTransactionMemo(actual.getTransactionMemo());

        assertAll(
                () -> assertThat(actual.getTopicMemo()).contains("Mirror node created test topic"),
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node created test topic"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }
}
