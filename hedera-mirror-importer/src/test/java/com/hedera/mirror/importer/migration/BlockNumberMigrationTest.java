package com.hedera.mirror.importer.migration;

import com.hedera.mirror.common.domain.DigestAlgorithm;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.transaction.RecordFile;

import com.hedera.mirror.importer.IntegrationTest;

import com.hedera.mirror.importer.repository.RecordFileRepository;

import lombok.RequiredArgsConstructor;
import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.api.ObjectAssert;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
public class BlockNumberMigrationTest extends IntegrationTest {

    private final BlockNumberMigration blockNumberMigration;

    private final DomainBuilder domainBuilder;

    private final RecordFileRepository recordFileRepository;

    private static final long CORRECT_CONSENSUS_END = 1570801010552116001L;

    private static final long CORRECT_BLOCK_NUMBER = 420L;

    @Test
    public void theCorrectOffsetMustBeAddedToTheBlockNumbers() throws IllegalAccessException {
        List<RecordFile> defaultRecordFiles = insertDefaultRecordFiles();
        long offset = 412;
        List<Tuple> expectedBlockNumbersAndConsensusEnd = defaultRecordFiles.stream()
                .map(recordFile -> Tuple.tuple(recordFile.getConsensusEnd(), recordFile.getIndex() + offset))
                .collect(Collectors.toList());

        //FieldUtils.writeField(blockNumberMigration,"migrationProperties",blockNumberMigrationProperties,true);

        blockNumberMigration.doMigrate();

        assertConsensusEndAndBlockNumber(expectedBlockNumbersAndConsensusEnd);
    }

    @Test
    public void ifCorrectConsensusEndNotFoundDoNothing() {
        List<Tuple> expectedBlockNumbersAndConsensusEnd = insertDefaultRecordFiles(Set.of(1570801010552116001L))
                .stream()
                .map(recordFile -> Tuple.tuple(recordFile.getConsensusEnd(), recordFile.getIndex()))
                .collect(Collectors.toList());

        blockNumberMigration.doMigrate();

        assertConsensusEndAndBlockNumber(expectedBlockNumbersAndConsensusEnd);
    }

    @Test
    public void ifBlockNumberIsAlreadyCorrectDoNothing() {
        List<Tuple> expectedBlockNumbersAndConsensusEnd = insertDefaultRecordFiles(Set.of(CORRECT_CONSENSUS_END))
                .stream()
                .map(recordFile -> Tuple.tuple(recordFile.getConsensusEnd(), recordFile.getIndex()))
                .collect(Collectors.toList());

        final RecordFile targetRecordFile = domainBuilder.recordFile()
                .customize(builder -> builder
                        .consensusEnd(CORRECT_CONSENSUS_END)
                        .index(CORRECT_BLOCK_NUMBER)
                )
                .persist();
        expectedBlockNumbersAndConsensusEnd.add(Tuple.tuple(targetRecordFile.getConsensusEnd(), targetRecordFile.getIndex()));

        blockNumberMigration.doMigrate();

        assertConsensusEndAndBlockNumber(expectedBlockNumbersAndConsensusEnd);
    }

    private AbstractListAssert<?, List<? extends Tuple>, Tuple, ObjectAssert<Tuple>> assertConsensusEndAndBlockNumber(
            List<Tuple> expectedBlockNumbersAndConsensusEnd){
        return assertThat(recordFileRepository.findAll())
                .hasSize(expectedBlockNumbersAndConsensusEnd.size())
                .extracting(RecordFile::getConsensusEnd, RecordFile::getIndex)
                .containsExactlyInAnyOrder(expectedBlockNumbersAndConsensusEnd.toArray(Tuple[]::new));
    }

    private List<RecordFile> insertDefaultRecordFiles() {
        return insertDefaultRecordFiles(Set.of());
    }

    private List<RecordFile> insertDefaultRecordFiles(Set<Long> skipRecordFileWithConsensusEnd) {
        long[] consensusEnd = {1570800761443132000L,CORRECT_CONSENSUS_END,1570801906238879002L};
        long[] blockNumber = {0L,8L,9L};
        var recordFiles = new ArrayList<RecordFile>(consensusEnd.length);

        for (int i = 0; i < consensusEnd.length; i++) {
            if(skipRecordFileWithConsensusEnd.contains(consensusEnd[i])){
                continue;
            }
            final long currConsensusEnd = consensusEnd[i];
            final long currBlockNumber = blockNumber[i];
            RecordFile recordFile = domainBuilder.recordFile()
                    .customize(builder -> builder
                            .consensusEnd(currConsensusEnd)
                            .index(currBlockNumber)
                    )
                    .persist();
            recordFiles.add(recordFile);
        }

        return recordFiles;
    }
}
