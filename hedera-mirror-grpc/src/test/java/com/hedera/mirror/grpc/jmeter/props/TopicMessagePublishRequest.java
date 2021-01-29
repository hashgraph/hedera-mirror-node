package com.hedera.mirror.grpc.jmeter.props;

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

import com.google.common.primitives.Longs;
import java.security.SecureRandom;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ArrayUtils;

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

    private byte[] randomBytes;

    public byte[] getMessage() {
        if (randomBytes == null) {
            randomBytes = new byte[messageByteSize > Long.BYTES ? messageByteSize - Long.BYTES : 0];
            new SecureRandom().nextBytes(randomBytes);
        }

        // set current time stamp to first 8 bytes of message and random bytes message after
        return ArrayUtils.addAll(Longs.toByteArray(System.currentTimeMillis()), randomBytes);
    }
}
