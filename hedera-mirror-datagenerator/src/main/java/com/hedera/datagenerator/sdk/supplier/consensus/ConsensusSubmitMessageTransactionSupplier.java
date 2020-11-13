package com.hedera.datagenerator.sdk.supplier.consensus;

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

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.HederaThrowable;
import com.hedera.hashgraph.sdk.consensus.ConsensusMessageSubmitTransaction;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;

@Data
public class ConsensusSubmitMessageTransactionSupplier implements TransactionSupplier<ConsensusMessageSubmitTransaction> {

    @Min(1)
    private long maxTransactionFee = 1_000_000;

    private String message = StringUtils.EMPTY;

    @Min(1)
    private int messageSize = 256;

    @NotBlank
    private String topicId;

    private boolean retry = false;

    // Cached for performance testing
    @Getter(lazy = true)
    private final ConsensusTopicId consensusTopicId = ConsensusTopicId.fromString(topicId);

    @Getter(lazy = true)
    private final String messageSuffix = randomString();

    @Override
    public ConsensusMessageSubmitTransaction get() {
        return new NonRetryableConsensusMessageSubmitTransaction()
                .setMaxTransactionFee(maxTransactionFee)
                .setMessage(generateMessage())
                .setTopicId(getConsensusTopicId());
    }

    private String generateMessage() {
        return Utility.getEncodedTimestamp() + getMessageSuffix();
    }

    private String randomString() {
        if (StringUtils.isNotBlank(message)) {
            return message;
        }
        int additionalBytes = messageSize <= Long.BYTES ? 0 : messageSize - Long.BYTES;
        return RandomStringUtils.randomAlphanumeric(additionalBytes);
    }

    private class NonRetryableConsensusMessageSubmitTransaction extends ConsensusMessageSubmitTransaction {

        @Override
        protected boolean shouldRetry(HederaThrowable e) {
            return retry;
        }
    }
}

