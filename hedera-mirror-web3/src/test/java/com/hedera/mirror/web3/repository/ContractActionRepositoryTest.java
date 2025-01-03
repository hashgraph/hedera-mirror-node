/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.repository;

import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.REVERT_REASON;
import static com.hedera.services.stream.proto.ContractActionType.SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.web3.Web3IntegrationTest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ContractActionRepositoryTest extends Web3IntegrationTest {
    private final ContractActionRepository contractActionRepository;

    @Test
    void findAllByConsensusTimestampSuccessful() {
        final var timestamp = domainBuilder.timestamp();
        final var otherActions = List.of(
                domainBuilder
                        .contractAction()
                        .customize(action -> action.consensusTimestamp(timestamp))
                        .persist(),
                domainBuilder
                        .contractAction()
                        .customize(action -> action.consensusTimestamp(timestamp))
                        .persist());
        final var failedSystemActions = List.of(
                domainBuilder
                        .contractAction()
                        .customize(action -> action.callType(SYSTEM.getNumber())
                                .consensusTimestamp(timestamp)
                                .resultDataType(REVERT_REASON.getNumber()))
                        .persist(),
                domainBuilder
                        .contractAction()
                        .customize(action -> action.callType(SYSTEM.getNumber())
                                .consensusTimestamp(timestamp)
                                .resultDataType(REVERT_REASON.getNumber()))
                        .persist());

        assertThat(contractActionRepository.findFailedSystemActionsByConsensusTimestamp(timestamp))
                .containsExactlyElementsOf(failedSystemActions)
                .doesNotContainAnyElementsOf(otherActions);
    }
}
