/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.migration;

import static com.hedera.mirror.importer.MirrorProperties.HederaNetwork.PREVIEWNET;
import static com.hedera.mirror.importer.MirrorProperties.HederaNetwork.TESTNET;
import static com.hedera.mirror.importer.migration.BlockNumberMigration.BLOCK_NUMBER_MAPPING;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
class BlockNumberMigrationTest extends IntegrationTest {

    private static final long CORRECT_CONSENSUS_END =
            BLOCK_NUMBER_MAPPING.get(TESTNET).getKey();
    private static final long CORRECT_BLOCK_NUMBER =
            BLOCK_NUMBER_MAPPING.get(TESTNET).getValue();

    private final BlockNumberMigration blockNumberMigration;
    private final MirrorProperties mirrorProperties;
    private final RecordFileRepository recordFileRepository;

    @BeforeEach
    void setup() {
        mirrorProperties.setNetwork(TESTNET);
    }

    @Test
    void checksum() {
        assertThat(blockNumberMigration.getChecksum()).isEqualTo(4);
    }

    @Test
    void unsupportedNetwork() {
        var previousNetwork = mirrorProperties.getNetwork();
        mirrorProperties.setNetwork(PREVIEWNET);
        List<Tuple> expectedBlockNumbersAndConsensusEnd =
                insertDefaultRecordFiles(Set.of(CORRECT_CONSENSUS_END)).stream()
                        .map(recordFile -> Tuple.tuple(recordFile.getConsensusEnd(), recordFile.getIndex()))
                        .collect(Collectors.toList());

        blockNumberMigration.doMigrate();

        assertConsensusEndAndBlockNumber(expectedBlockNumbersAndConsensusEnd);
        mirrorProperties.setNetwork(previousNetwork);
    }

    @Test
    void theCorrectOffsetMustBeAddedToTheBlockNumbers() {
        List<RecordFile> defaultRecordFiles = insertDefaultRecordFiles();
        long offset = CORRECT_BLOCK_NUMBER - 8L;
        List<Tuple> expectedBlockNumbersAndConsensusEnd = defaultRecordFiles.stream()
                .map(recordFile -> Tuple.tuple(recordFile.getConsensusEnd(), recordFile.getIndex() + offset))
                .collect(Collectors.toList());

        blockNumberMigration.doMigrate();

        assertConsensusEndAndBlockNumber(expectedBlockNumbersAndConsensusEnd);
    }

    @Test
    void ifCorrectConsensusEndNotFoundDoNothing() {
        List<Tuple> expectedBlockNumbersAndConsensusEnd =
                insertDefaultRecordFiles(Set.of(CORRECT_CONSENSUS_END)).stream()
                        .map(recordFile -> Tuple.tuple(recordFile.getConsensusEnd(), recordFile.getIndex()))
                        .collect(Collectors.toList());

        blockNumberMigration.doMigrate();

        assertConsensusEndAndBlockNumber(expectedBlockNumbersAndConsensusEnd);
    }

    @Test
    void ifBlockNumberIsAlreadyCorrectDoNothing() {
        List<Tuple> expectedBlockNumbersAndConsensusEnd =
                insertDefaultRecordFiles(Set.of(CORRECT_CONSENSUS_END)).stream()
                        .map(recordFile -> Tuple.tuple(recordFile.getConsensusEnd(), recordFile.getIndex()))
                        .collect(Collectors.toList());

        final RecordFile targetRecordFile = domainBuilder
                .recordFile()
                .customize(
                        builder -> builder.consensusEnd(CORRECT_CONSENSUS_END).index(CORRECT_BLOCK_NUMBER))
                .persist();
        expectedBlockNumbersAndConsensusEnd.add(
                Tuple.tuple(targetRecordFile.getConsensusEnd(), targetRecordFile.getIndex()));

        blockNumberMigration.doMigrate();

        assertConsensusEndAndBlockNumber(expectedBlockNumbersAndConsensusEnd);
    }

    private void assertConsensusEndAndBlockNumber(List<Tuple> expectedBlockNumbersAndConsensusEnd) {
        assertThat(recordFileRepository.findAll())
                .hasSize(expectedBlockNumbersAndConsensusEnd.size())
                .extracting(RecordFile::getConsensusEnd, RecordFile::getIndex)
                .containsExactlyInAnyOrder(expectedBlockNumbersAndConsensusEnd.toArray(Tuple[]::new));
    }

    private List<RecordFile> insertDefaultRecordFiles() {
        return insertDefaultRecordFiles(Set.of());
    }

    private List<RecordFile> insertDefaultRecordFiles(Set<Long> skipRecordFileWithConsensusEnd) {
        long[] consensusEnd = {1570800761443132000L, CORRECT_CONSENSUS_END, CORRECT_CONSENSUS_END + 1L};
        long[] blockNumber = {0L, 8L, 9L};
        var recordFiles = new ArrayList<RecordFile>(consensusEnd.length);

        for (int i = 0; i < consensusEnd.length; i++) {
            if (skipRecordFileWithConsensusEnd.contains(consensusEnd[i])) {
                continue;
            }
            final long currConsensusEnd = consensusEnd[i];
            final long currBlockNumber = blockNumber[i];
            RecordFile recordFile = domainBuilder
                    .recordFile()
                    .customize(builder -> builder.consensusEnd(currConsensusEnd).index(currBlockNumber))
                    .persist();
            recordFiles.add(recordFile);
        }

        return recordFiles;
    }
}
