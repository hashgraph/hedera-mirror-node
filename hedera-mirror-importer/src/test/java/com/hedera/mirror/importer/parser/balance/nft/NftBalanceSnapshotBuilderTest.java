package com.hedera.mirror.importer.parser.balance.nft;

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

import static com.hedera.mirror.importer.domain.EntityTypeEnum.ACCOUNT;
import static com.hedera.mirror.importer.domain.EntityTypeEnum.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Lists;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.TestPropertySource;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.NftBalance;
import com.hedera.mirror.importer.domain.NftTransfer;
import com.hedera.mirror.importer.domain.NftTransferId;
import com.hedera.mirror.importer.repository.NftBalanceRepository;
import com.hedera.mirror.importer.repository.NftTransferRepository;

@TestPropertySource(properties = "spring.task.scheduling.enabled=false")
class NftBalanceSnapshotBuilderTest extends IntegrationTest {

    private static final EntityId ACCOUNT_ID1 = EntityId.of("0.0.100", ACCOUNT);
    private static final EntityId ACCOUNT_ID2 = EntityId.of("0.0.101", ACCOUNT);
    private static final EntityId TOKEN_ID1 = EntityId.of("0.0.500", TOKEN);
    private static final EntityId TOKEN_ID2 = EntityId.of("0.0.501", TOKEN);

    private static final List<NftBalance> initialSnapshot = List.of(
            new NftBalance(50, ACCOUNT_ID1, 1, TOKEN_ID1),
            new NftBalance(50, ACCOUNT_ID2, 2, TOKEN_ID1),
            new NftBalance(50, ACCOUNT_ID2, 1, TOKEN_ID2)
    );
    private static final List<NftTransfer> nftTransfers = List.of(
            // transfers happened <= first snapshot
            nftTransfer(10, ACCOUNT_ID1, null, 1, TOKEN_ID1), // mint
            nftTransfer(10, ACCOUNT_ID1, null, 2, TOKEN_ID1), // mint
            nftTransfer(20, ACCOUNT_ID2, ACCOUNT_ID1, 2, TOKEN_ID1), // xfer 1 -> 2
            nftTransfer(50, ACCOUNT_ID2, null, 1, TOKEN_ID2), // mint
            // transfers after first snapshot
            nftTransfer(55, ACCOUNT_ID1, null, 3, TOKEN_ID1), // mint
            nftTransfer(55, ACCOUNT_ID1, null, 4, TOKEN_ID1), // mint
            nftTransfer(55, ACCOUNT_ID1, null, 5, TOKEN_ID1), // mint
            nftTransfer(57, ACCOUNT_ID2, ACCOUNT_ID1, 1, TOKEN_ID1), // xfer 1 -> 2
            nftTransfer(57, ACCOUNT_ID2, ACCOUNT_ID1, 3, TOKEN_ID1), // xfer 1 -> 2
            nftTransfer(57, ACCOUNT_ID2, ACCOUNT_ID1, 4, TOKEN_ID1), // xfer 1 -> 2
            nftTransfer(59, ACCOUNT_ID1, ACCOUNT_ID2, 3, TOKEN_ID1), // xfer 2 -> 1
            nftTransfer(61, ACCOUNT_ID2, null, 2, TOKEN_ID2), // mint
            nftTransfer(61, ACCOUNT_ID2, null, 3, TOKEN_ID2), // mint
            nftTransfer(65, null, ACCOUNT_ID1, 5, TOKEN_ID1), // burn
            nftTransfer(66, null, ACCOUNT_ID2, 4, TOKEN_ID1), // wipe
            nftTransfer(70, ACCOUNT_ID1, ACCOUNT_ID2, 3, TOKEN_ID2) // xfer 2 -> 1
    );

    @Resource
    private NftBalanceSnapshotBuilder nftBalanceSnapshotBuilder;

    @Resource
    private NftBalanceRepository nftBalanceRepository;

    @Resource
    private NftTransferRepository nftTransferRepository;

    @Resource
    private NftBalanceSnapshotProperties properties;

    @BeforeEach
    void setup() {
        properties.setEnabled(true);
    }

    @Test
    void bothEmpty() {
        nftBalanceSnapshotBuilder.build();
        assertThat(nftBalanceRepository.count()).isZero();
    }

    @ParameterizedTest(name = "interval {0}ns transferSize {1}")
    @MethodSource("provideBuildArguments")
    void build(long interval, long transferSize, List<NftBalance> expected) {
        // given
        properties.setInterval(Duration.ofNanos(interval));
        properties.setTransferSize(transferSize);
        loadData();

        // when
        nftBalanceSnapshotBuilder.build();

        // then
        assertThat(nftBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void disabled() {
        // given
        properties.setEnabled(false);
        properties.setInterval(Duration.ofNanos(1));
        properties.setTransferSize(1);
        loadData();

        // when
        nftBalanceSnapshotBuilder.build();

        // then
        assertThat(nftBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(initialSnapshot);
    }

    private void loadData() {
        nftBalanceRepository.saveAll(initialSnapshot);
        nftTransferRepository.saveAll(nftTransfers);
    }

    private static NftTransfer nftTransfer(long consensusTimestamp, EntityId receiver, EntityId sender, long serialNumber,
            EntityId tokenId) {
        NftTransfer nftTransfer = new NftTransfer();
        nftTransfer.setId(new NftTransferId(consensusTimestamp, serialNumber, tokenId));
        nftTransfer.setReceiverAccountId(receiver);
        nftTransfer.setSenderAccountId(sender);
        return nftTransfer;
    }

    private static Stream<Arguments> provideBuildArguments() {
        Stream.Builder<Arguments> builder = Stream.builder();

        builder.add(Arguments.of(21, 1, initialSnapshot)); // not old enough, the oldest transfer is 20ns later
        builder.add(Arguments.of(10, 13, initialSnapshot)); // not enough transfers, need 13, there are 12

        // result of applying all new transfers to the initial snapshot
        List<NftBalance> expected = Lists.newArrayList(initialSnapshot);
        expected.addAll(List.of(
                // new snapshot
                new NftBalance(70, ACCOUNT_ID1, 3, TOKEN_ID1),
                new NftBalance(70, ACCOUNT_ID1, 3, TOKEN_ID2),
                new NftBalance(70, ACCOUNT_ID2, 1, TOKEN_ID1),
                new NftBalance(70, ACCOUNT_ID2, 2, TOKEN_ID1),
                new NftBalance(70, ACCOUNT_ID2, 1, TOKEN_ID2),
                new NftBalance(70, ACCOUNT_ID2, 2, TOKEN_ID2)
        ));
        builder.add(Arguments.of(20, 1, expected)); // the last transfer is exactly 20ns old relatively
        builder.add(Arguments.of(5, 12, expected)); // the last transfer is exactly the 12th relatively

        expected = Lists.newArrayList(initialSnapshot);
        expected.addAll(List.of(
                // new snapshot
                new NftBalance(61, ACCOUNT_ID1, 3, TOKEN_ID1),
                new NftBalance(61, ACCOUNT_ID1, 5, TOKEN_ID1),
                new NftBalance(61, ACCOUNT_ID2, 1, TOKEN_ID1),
                new NftBalance(61, ACCOUNT_ID2, 2, TOKEN_ID1),
                new NftBalance(61, ACCOUNT_ID2, 4, TOKEN_ID1),
                new NftBalance(61, ACCOUNT_ID2, 1, TOKEN_ID2),
                new NftBalance(61, ACCOUNT_ID2, 2, TOKEN_ID2),
                new NftBalance(61, ACCOUNT_ID2, 3, TOKEN_ID2)
        ));
        builder.add(Arguments.of(10, 1, expected)); // snapshot built at first transfer consensus timestamp >= 50 + 10
        builder.add(Arguments.of(5, 9, expected)); // the 9th transfer is at consensus timestamp 61

        return builder.build();
    }
}
