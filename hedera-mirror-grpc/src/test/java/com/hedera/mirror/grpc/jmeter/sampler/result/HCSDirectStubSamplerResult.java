package com.hedera.mirror.grpc.jmeter.sampler.result;

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

import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.api.proto.ConsensusTopicResponse;

@SuperBuilder
@Data
@Log4j2
public class HCSDirectStubSamplerResult extends HCSSamplerResult<ConsensusTopicResponse> {
    private Instant consensusInstant;

    @Override
    public Instant getConsensusInstant(ConsensusTopicResponse consensusTopicResponse) {
        consensusInstant = toInstant(consensusTopicResponse.getConsensusTimestamp());
        return consensusInstant;
    }

    @Override
    public long getSequenceNumber(ConsensusTopicResponse consensusTopicResponse) {
        return consensusTopicResponse.getSequenceNumber();
    }

    @Override
    public String getMessage(ConsensusTopicResponse response) {
        return response.getMessage().toStringUtf8();
    }

    private Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    @Override
    byte[] getMessageByteArray(ConsensusTopicResponse response) {
        return response.getMessage().toByteArray();
    }
}
