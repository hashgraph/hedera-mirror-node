package com.hedera.mirror.monitor.publish;

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

import java.time.Instant;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.TransactionRecord;

@Builder
@Value
public class PublishResponse {

    private final PublishRequest request;
    private final TransactionRecord record;
    private final TransactionReceipt receipt;
    private final Instant timestamp;
    private final TransactionId transactionId;

    // Needed since the SDK doesn't implement toString() or have actual fields to use with reflection
    @Override
    public String toString() {
        ToStringBuilder toStringBuilder = new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE);
        toStringBuilder.append("timestamp", timestamp);
        toStringBuilder.append("transactionId", transactionId);
        toStringBuilder.append("type", request.getType());

        if (record != null) {
            toStringBuilder.append("consensusTimestamp", record.consensusTimestamp);
        }

        if (receipt != null) {
            toStringBuilder.append("status", receipt.status);

            if (receipt.accountId != null) {
                toStringBuilder.append("accountId", receipt.accountId.num);
            } else if (receipt.contractId != null) {
                toStringBuilder.append("contractId", receipt.contractId.num);
            } else if (receipt.fileId != null) {
                toStringBuilder.append("fileId", receipt.fileId.num);
            } else if (receipt.tokenId != null) {
                toStringBuilder.append("tokenId", receipt.tokenId.num);
            } else if (receipt.topicId != null) {
                toStringBuilder.append("topicId", receipt.topicId.num);
            }

            if (receipt.topicSequenceNumber > 0) {
                toStringBuilder.append("topicSequenceNumber", receipt.topicSequenceNumber);
            }
        }

        return toStringBuilder.toString();
    }
}
