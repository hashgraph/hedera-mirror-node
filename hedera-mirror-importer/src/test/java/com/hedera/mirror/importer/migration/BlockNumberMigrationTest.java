package com.hedera.mirror.importer.migration;

import com.hedera.mirror.common.domain.DigestAlgorithm;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.transaction.RecordFile;

import com.hedera.mirror.importer.IntegrationTest;

import com.hedera.mirror.importer.repository.RecordFileRepository;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.api.ObjectAssert;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
public class BlockNumberMigrationTest extends IntegrationTest {

    private final BlockNumberMigrationProperties blockNumberMigrationProperties;

    private final BlockNumberMigration blockNumberMigration;

    private final DomainBuilder domainBuilder;

    private final RecordFileRepository recordFileRepository;

    @Test
    public void theCorrectOffsetMustBeAddedToTheBlockNumbers() throws IllegalAccessException {
        List<RecordFile> defaultRecordFiles = insertDefaultRecordFiles();
        long offset = 412;
        List<Tuple> expectedBlockNumbersAndConsensusEnd = defaultRecordFiles.stream()
                .map(recordFile -> Tuple.tuple(recordFile.getConsensusEnd(), recordFile.getIndex() + offset))
                .collect(Collectors.toList());

        FieldUtils.writeField(blockNumberMigration,"migrationProperties",blockNumberMigrationProperties,true);

        blockNumberMigration.doMigrate();

        assertConsensusEndAndBlockNumber(expectedBlockNumbersAndConsensusEnd);
    }

    @Test
    public void ifCorrectConsensusEndNotFoundDoNothing() throws IllegalAccessException {
        List<Tuple> expectedBlockNumbersAndConsensusEnd = insertDefaultRecordFiles()
                .stream()
                .map(recordFile -> Tuple.tuple(recordFile.getConsensusEnd(), recordFile.getIndex()))
                .collect(Collectors.toList());

        FieldUtils.writeField(
                blockNumberMigration,
                "migrationProperties",
                copyPropertiesOverwriteConsensusEnd(blockNumberMigrationProperties, -1),
                true);

        blockNumberMigration.doMigrate();

        assertConsensusEndAndBlockNumber(expectedBlockNumbersAndConsensusEnd);
    }

    @Test
    public void ifBlockNumberIsAlreadyCorrectDoNothing() throws IllegalAccessException {
        List<Tuple> expectedBlockNumbersAndConsensusEnd = insertDefaultRecordFiles()
                .stream()
                .map(recordFile -> Tuple.tuple(recordFile.getConsensusEnd(), recordFile.getIndex()))
                .collect(Collectors.toList());

        FieldUtils.writeField(
                blockNumberMigration,
                "migrationProperties",
                copyPropertiesOverwriteBlockNumber(blockNumberMigrationProperties, 8),
                true);

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

    private BlockNumberMigrationProperties copyPropertiesOverwriteConsensusEnd(BlockNumberMigrationProperties src, long consensusEnd){
        var newProps = new BlockNumberMigrationProperties();
        newProps.setCorrectBlockNumber(src.getCorrectBlockNumber());
        newProps.setCorrectConsensusEnd(consensusEnd);
        return newProps;
    }

    private BlockNumberMigrationProperties copyPropertiesOverwriteBlockNumber(BlockNumberMigrationProperties src, long blockNumber){
        var newProps = new BlockNumberMigrationProperties();
        newProps.setCorrectBlockNumber(blockNumber);
        newProps.setCorrectConsensusEnd(src.getCorrectConsensusEnd());
        return newProps;
    }

    private List<RecordFile> insertDefaultRecordFiles() {
        var recordFiles = new ArrayList<RecordFile>(3);
        var recordFile = domainBuilder.recordFile()
                .customize(recordFileBuilder ->
                    recordFileBuilder.name("2019-10-11T13_32_41.443132Z.rcd")
                        .loadStart(1650382134L)
                        .loadEnd(1650382134L)
                        .hash("495dc50d6323d4d5263d34dbefe7c20a0657a2fa5fe0d8c011a01f4a27757a259987dad210fb9596bc5a8cc7fbdbba33")
                        .previousHash("000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")
                        .consensusEnd(1570800761443132000L)
                        .consensusStart(1570800761443132000L)
                        .count(1L)
                        .digestAlgorithm(DigestAlgorithm.SHA384)
                        .version(2)
                        .fileHash("495dc50d6323d4d5263d34dbefe7c20a0657a2fa5fe0d8c011a01f4a27757a259987dad210fb9596bc5a8cc7fbdbba33")
                        .index(0L)
                )
                .persist();
        recordFiles.add(recordFile);

        recordFile = domainBuilder.recordFile()
                .customize(recordFileBuilder ->
                        recordFileBuilder.name("2019-10-11T13_36_50.108392Z.rcd")
                                .loadStart(1650382135L)
                                .loadEnd(1650382136L)
                                .hash("acb6960c300df4948f72a47074a6fdfecba8755dad561af5f8f16a0183478dddb682135a6b90df4747fb366f80a034f7")
                                .previousHash("1b0dfd6c1deb4e808c98e390dadc15152906bf61965612dd89bbcc12aca480a37a562bcc59888eb8f614df54d244973c")
                                .consensusEnd(1570801010552116001L)
                                .consensusStart(1570801010108392000L)
                                .count(3L)
                                .digestAlgorithm(DigestAlgorithm.SHA384)
                                .version(2)
                                .fileHash("acb6960c300df4948f72a47074a6fdfecba8755dad561af5f8f16a0183478dddb682135a6b90df4747fb366f80a034f7")
                                .index(8L)
                )
                .persist();
        recordFiles.add(recordFile);

        recordFile = domainBuilder.recordFile()
                .customize(recordFileBuilder ->
                        recordFileBuilder.name("2019-10-11T13_51_46.238879002Z.rcd")
                                .loadStart(1650382136L)
                                .loadEnd(1650382136L)
                                .hash("d1b751c6190a3e2c923e3feeae9865bc7aa384c92e27ef2fd841c668098f771eefebd1564b2c3886765e0e0336de6d02")
                                .previousHash("acb6960c300df4948f72a47074a6fdfecba8755dad561af5f8f16a0183478dddb682135a6b90df4747fb366f80a034f7")
                                .consensusEnd(1570801906238879002L)
                                .consensusStart(1570801908102556000L)
                                .count(2L)
                                .digestAlgorithm(DigestAlgorithm.SHA384)
                                .version(2)
                                .fileHash("d1b751c6190a3e2c923e3feeae9865bc7aa384c92e27ef2fd841c668098f771eefebd1564b2c3886765e0e0336de6d02")
                                .index(9L)
                )
                .persist();
        recordFiles.add(recordFile);

        return recordFiles;
    }
}
