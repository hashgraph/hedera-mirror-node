package com.hedera.mirror.monitor.supplier;

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

import com.google.common.primitives.Longs;
import java.time.Instant;
import java.util.Base64;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.RandomStringUtils;

import com.hedera.hashgraph.sdk.consensus.ConsensusMessageSubmitTransaction;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;

@Builder
@Value
public class ConsensusSubmitMessageSupplier implements TransactionSupplier<ConsensusMessageSubmitTransaction> {

    @Builder.Default
    private final int messageSize = 256;

    private final String topicId;

    @Override
    public ConsensusMessageSubmitTransaction get() {
        return new ConsensusMessageSubmitTransaction()
                .setTopicId(ConsensusTopicId.fromString(topicId))
                .setMessage(getMessage());
    }

    // Generate a message with the current time stamp as the first 8 bytes
    public String getMessage() {
        byte[] timeRefBytes = Longs.toByteArray(Instant.now().toEpochMilli());
        int additionalBytes = messageSize <= timeRefBytes.length ? 0 : messageSize - timeRefBytes.length;
        String randomAlphanumeric = RandomStringUtils.randomAlphanumeric(additionalBytes);
        return Base64.getEncoder().encodeToString(timeRefBytes) + randomAlphanumeric;
    }
}
