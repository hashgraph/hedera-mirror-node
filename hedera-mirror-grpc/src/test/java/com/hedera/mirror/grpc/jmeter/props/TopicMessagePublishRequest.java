package com.hedera.mirror.grpc.jmeter.props;

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
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Base64;
import org.testcontainers.shaded.org.apache.commons.lang.RandomStringUtils;

import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;

@Data
@Builder
@Log4j2
public class TopicMessagePublishRequest {
    private final ConsensusTopicId consensusTopicId;
    private final int messagesPerBatchCount;
    private final int messageByteSize;
    private final long publishInterval;
    private final long publishTimeout;

    private AccountId operatorId;

    @ToString.Exclude
    private Ed25519PrivateKey operatorPrivateKey;

    private String randomAlphanumeric;

    public String getMessage() {
        int timeStampBytes = 8;
        if (randomAlphanumeric == null) {
            int additionalBytes = messageByteSize <= timeStampBytes ? 0 : messageByteSize - 8;
            randomAlphanumeric = RandomStringUtils.randomAlphanumeric(additionalBytes);
        }

        // set current time stamp to first 8 bytes of message
        byte[] timeRefBytes = Longs.toByteArray(Instant.now().toEpochMilli());

        return Base64.encodeBase64String(timeRefBytes) + randomAlphanumeric;
    }
}
