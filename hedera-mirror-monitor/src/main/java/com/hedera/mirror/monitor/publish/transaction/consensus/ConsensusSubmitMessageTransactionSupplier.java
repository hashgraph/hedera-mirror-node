/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.TopicMessageSubmitTransaction;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;
import com.hedera.mirror.monitor.util.Utility;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import lombok.Data;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Data
public class ConsensusSubmitMessageTransactionSupplier implements TransactionSupplier<TopicMessageSubmitTransaction> {

    @Min(1)
    private long maxTransactionFee = 1_000_000;

    @NotNull
    private String message = StringUtils.EMPTY;

    @Min(14)
    @Max(6144)
    private int messageSize = 256;

    @NotBlank
    private String topicId;

    // Internal variables that are cached for performance reasons
    @Getter(lazy = true)
    private final TopicId consensusTopicId = TopicId.fromString(topicId);

    @Override
    public TopicMessageSubmitTransaction get() {
        return new TopicMessageSubmitTransaction()
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setMessage(
                        !message.isEmpty()
                                ? message.getBytes(StandardCharsets.UTF_8)
                                : Utility.generateMessage(messageSize))
                .setTopicId(getConsensusTopicId());
    }
}
