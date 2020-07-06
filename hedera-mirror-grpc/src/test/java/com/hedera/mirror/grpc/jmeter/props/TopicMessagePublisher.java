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
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.Random;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;

@Data
@Builder
public class TopicMessagePublisher {
    private final ConsensusTopicId consensusTopicId;
    private final int messagesPerBatchCount;
    private final int messageByteSize;
    private final long publishInterval;
    private final long publishTimeout;

    private AccountId operatorId;

    @ToString.Exclude
    private Ed25519PrivateKey operatorPrivateKey;

    @ToString.Exclude
    private String message;

    private byte[] additionalChars;

    public String getMessage() {
        int timeStampBytes = 8;
        int additionalBytes = messageByteSize <= timeStampBytes ? 0 : messageByteSize - 8;

        // cache additional random bytes once to fit desired message byte array size
        if (additionalChars == null) {
            additionalChars = new byte[additionalBytes];
            new Random().nextBytes(additionalChars);
        }

        // set current time stamp to first 8 bytes of message
        byte[] timeRefBytes = Longs.toByteArray(Instant.now().toEpochMilli());
        byte[] messageBytes = new byte[timeStampBytes + additionalBytes];
        System.arraycopy(timeRefBytes, 0, messageBytes, 0, timeRefBytes.length);

        // set additional bytes where relevant
        if (additionalBytes > 0) {
            System.arraycopy(additionalChars, 0, messageBytes, timeRefBytes.length, additionalChars.length);
        }

        message = new String(messageBytes, Charset.forName("UTF-8"));
        return message;
    }
}
