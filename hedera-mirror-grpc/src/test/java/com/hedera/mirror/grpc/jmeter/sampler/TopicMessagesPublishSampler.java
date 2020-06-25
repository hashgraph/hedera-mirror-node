package com.hedera.mirror.grpc.jmeter.sampler;

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

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.consensus.ConsensusMessageSubmitTransaction;
import com.hedera.mirror.grpc.jmeter.client.TopicMessagePublishClient;
import com.hedera.mirror.grpc.jmeter.props.TopicMessagePublisher;
import com.hedera.mirror.grpc.jmeter.sampler.result.TransactionSubmissionResult;

@Log4j2
@RequiredArgsConstructor
public class TopicMessagesPublishSampler {
    private final TopicMessagePublisher topicMessagePublisher;
    private final TopicMessagePublishClient.SDKClient sdkClient;

    @SneakyThrows
    public int run() {
        // publish MessagesPerBatchCount number of messages to the noted topic id
        Client client = sdkClient.getClient();
        log.debug("Submit transaction to {}, topicMessagePublisher: {}", sdkClient
                .getNodeInfo(), topicMessagePublisher);
        Transaction transaction;
        TransactionSubmissionResult result = new TransactionSubmissionResult();

        for (int i = 0; i < topicMessagePublisher.getMessagesPerBatchCount(); i++) {
            transaction = new ConsensusMessageSubmitTransaction()
                    .setTopicId(topicMessagePublisher.getConsensusTopicId())
                    .setMessage(topicMessagePublisher.getMessage())
                    .build(client);

//            if (submitKey != null) {
//                // The transaction is automatically signed by the payer.
//                // Due to the topic having a submitKey requirement, additionally sign the transaction with that key.
//                transaction.sign(submitKey);
//            }

            TransactionId transactionId = transaction.execute(client, Duration.ofSeconds(2));
            result.onNext(transactionId);
        }

        result.onComplete();
        return result.getCounter().get();
    }
}
