/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.monitor.publish.transaction.consensus;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hashgraph.sdk.TopicDeleteTransaction;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class ConsensusDeleteTopicTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        ConsensusDeleteTopicTransactionSupplier consensusDeleteTopicTransactionSupplier =
                new ConsensusDeleteTopicTransactionSupplier();
        consensusDeleteTopicTransactionSupplier.setTopicId(TOPIC_ID.toString());
        TopicDeleteTransaction actual = consensusDeleteTopicTransactionSupplier.get();

        assertThat(actual)
                .returns(MAX_TRANSACTION_FEE_HBAR, TopicDeleteTransaction::getMaxTransactionFee)
                .returns(TOPIC_ID, TopicDeleteTransaction::getTopicId);
    }

    @Test
    void createWithCustomData() {
        ConsensusDeleteTopicTransactionSupplier consensusDeleteTopicTransactionSupplier =
                new ConsensusDeleteTopicTransactionSupplier();
        consensusDeleteTopicTransactionSupplier.setMaxTransactionFee(1);
        consensusDeleteTopicTransactionSupplier.setTopicId(TOPIC_ID.toString());
        TopicDeleteTransaction actual = consensusDeleteTopicTransactionSupplier.get();

        assertThat(actual)
                .returns(ONE_TINYBAR, TopicDeleteTransaction::getMaxTransactionFee)
                .returns(TOPIC_ID, TopicDeleteTransaction::getTopicId);
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return ConsensusDeleteTopicTransactionSupplier.class;
    }
}
