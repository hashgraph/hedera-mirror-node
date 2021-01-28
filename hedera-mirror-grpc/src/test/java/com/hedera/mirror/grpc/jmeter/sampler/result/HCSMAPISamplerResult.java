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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.mirror.MirrorConsensusTopicResponse;

@SuperBuilder
@Data
@Log4j2
public class HCSMAPISamplerResult extends HCSSamplerResult<MirrorConsensusTopicResponse> {

    @Override
    Instant getConsensusInstant(MirrorConsensusTopicResponse response) {
        return response.consensusTimestamp;
    }

    @Override
    long getSequenceNumber(MirrorConsensusTopicResponse response) {
        return response.sequenceNumber;
    }

    @Override
    String getMessage(MirrorConsensusTopicResponse response) {
        return new String(response.message, StandardCharsets.UTF_8);
    }

    @Override
    byte[] getMessageByteArray(MirrorConsensusTopicResponse response) {
        return response.message;
    }
}
