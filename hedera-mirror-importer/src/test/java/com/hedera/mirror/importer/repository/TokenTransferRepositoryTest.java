/*
 * Copyright (C) 2020-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TokenTransferRepositoryTest extends ImporterIntegrationTest {

    private final TokenTransferRepository tokenTransferRepository;

    @Test
    void findByConsensusTimestamp() {
        var tokenTransfer1 = domainBuilder.tokenTransfer().persist();
        var tokenTransfer2 = domainBuilder
                .tokenTransfer()
                .customize(t -> {
                    var id = tokenTransfer1.getId().toBuilder()
                            .accountId(domainBuilder.entityId())
                            .build();
                    t.id(id);
                })
                .persist();
        var tokenTransfer3 = domainBuilder.tokenTransfer().persist();

        assertThat(tokenTransferRepository.findByConsensusTimestamp(
                        tokenTransfer1.getId().getConsensusTimestamp()))
                .containsExactlyInAnyOrder(tokenTransfer1, tokenTransfer2);
        assertThat(tokenTransferRepository.findByConsensusTimestamp(
                        tokenTransfer3.getId().getConsensusTimestamp()))
                .containsExactly(tokenTransfer3);
        assertThat(tokenTransferRepository.findByConsensusTimestamp(
                        tokenTransfer3.getId().getConsensusTimestamp() + 1))
                .isEmpty();
    }

    @Test
    void prune() {
        domainBuilder.tokenTransfer().persist();
        var tokenTransfer2 = domainBuilder.tokenTransfer().persist();
        var tokenTransfer3 = domainBuilder.tokenTransfer().persist();

        tokenTransferRepository.prune(tokenTransfer2.getId().getConsensusTimestamp());

        assertThat(tokenTransferRepository.findAll()).containsExactly(tokenTransfer3);
    }

    @Test
    void save() {
        var tokenTransfer = domainBuilder.tokenTransfer().get();
        tokenTransferRepository.save(tokenTransfer);
        assertThat(tokenTransferRepository.findById(tokenTransfer.getId()))
                .get()
                .isEqualTo(tokenTransfer);
    }
}
