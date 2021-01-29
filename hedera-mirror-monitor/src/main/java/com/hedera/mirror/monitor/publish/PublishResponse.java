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
            com.hedera.hashgraph.proto.TransactionReceipt receiptProto = receipt.toProto();
            toStringBuilder.append("status", receiptProto.getStatus());

            if (receiptProto.hasAccountID()) {
                toStringBuilder.append("accountId", receiptProto.getAccountID().getAccountNum());
            } else if (receiptProto.hasContractID()) {
                toStringBuilder.append("contractId", receiptProto.getContractID().getContractNum());
            } else if (receiptProto.hasFileID()) {
                toStringBuilder.append("fileId", receiptProto.getFileID().getFileNum());
            } else if (receiptProto.hasTokenID()) {
                toStringBuilder.append("tokenId", receiptProto.getTokenID().getTokenNum());
            } else if (receiptProto.hasTopicID()) {
                toStringBuilder.append("topicId", receiptProto.getTopicID().getTopicNum());
            }

            if (receiptProto.getTopicSequenceNumber() > 0) {
                toStringBuilder.append("topicSequenceNumber", receiptProto.getTopicSequenceNumber());
            }
        }

        return toStringBuilder.toString();
    }
}
