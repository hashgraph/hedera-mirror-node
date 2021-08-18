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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.TopicMessageSubmitTransaction;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class ConsensusSubmitMessageTransactionSupplierTest extends AbstractTransactionSupplierTest {

    private static final Hbar MAX_TRANSACTION_FEE_HBAR = Hbar.fromTinybars(1_000_000);

    @Test
    void createWithMinimumData() {
        ConsensusSubmitMessageTransactionSupplier consensusSubmitMessageTransactionSupplier =
                new ConsensusSubmitMessageTransactionSupplier();
        consensusSubmitMessageTransactionSupplier.setTopicId(TOPIC_ID.toString());
        TopicMessageSubmitTransaction actual = consensusSubmitMessageTransactionSupplier.get();

        assertThat(actual)
                .returns(MAX_TRANSACTION_FEE_HBAR, TopicMessageSubmitTransaction::getMaxTransactionFee)
                .returns(TOPIC_ID, TopicMessageSubmitTransaction::getTopicId)
                .satisfies(a -> assertThat(a.getMessage()).isNotNull())
                .satisfies(a -> assertThat(a.getMessage().size()).isEqualTo(256));
    }

    @Test
    void createWithCustomMessageSize() {
        ConsensusSubmitMessageTransactionSupplier consensusSubmitMessageTransactionSupplier =
                new ConsensusSubmitMessageTransactionSupplier();
        consensusSubmitMessageTransactionSupplier.setMaxTransactionFee(1);
        consensusSubmitMessageTransactionSupplier.setMessageSize(14);
        consensusSubmitMessageTransactionSupplier.setTopicId(TOPIC_ID.toString());
        TopicMessageSubmitTransaction actual = consensusSubmitMessageTransactionSupplier.get();

        assertThat(actual)
                .returns(ONE_TINYBAR, TopicMessageSubmitTransaction::getMaxTransactionFee)
                .returns(TOPIC_ID, TopicMessageSubmitTransaction::getTopicId)
                .satisfies(a -> assertThat(a.getMessage()).isNotNull())
                .satisfies(a -> assertThat(a.getMessage().size()).isEqualTo(14));
    }

    @Test
    void createWithCustomMessage() {
        String message = "ConsensusSubmitMessageTransactionSupplierTest.createWithCustomData";
        ConsensusSubmitMessageTransactionSupplier consensusSubmitMessageTransactionSupplier =
                new ConsensusSubmitMessageTransactionSupplier();
        consensusSubmitMessageTransactionSupplier.setMaxTransactionFee(1);
        consensusSubmitMessageTransactionSupplier.setMessage(message);
        consensusSubmitMessageTransactionSupplier.setTopicId(TOPIC_ID.toString());
        TopicMessageSubmitTransaction actual = consensusSubmitMessageTransactionSupplier.get();

        assertThat(actual)
                .returns(true, a -> Arrays.equals(message.getBytes(StandardCharsets.UTF_8), actual.getMessage()
                        .toByteArray()))
                .returns(ONE_TINYBAR, TopicMessageSubmitTransaction::getMaxTransactionFee)
                .returns(TOPIC_ID, TopicMessageSubmitTransaction::getTopicId);
    }
}
