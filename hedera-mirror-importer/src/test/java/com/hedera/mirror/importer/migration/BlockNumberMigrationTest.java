package com.hedera.mirror.importer.migration;

import com.hedera.mirror.common.domain.transaction.RecordFile;

import com.hedera.mirror.importer.IntegrationTest;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcOperations;
import org.testcontainers.shaded.com.fasterxml.jackson.core.type.TypeReference;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
public class BlockNumberMigrationTest extends IntegrationTest {

    private final JdbcOperations jdbcOperations;

    private final BlockNumberMigrationProperties blockNumberMigrationProperties;

    private final BlockNumberMigration blockNumberMigration;

    @Test
    public void theCorrectOffsetMustBeAddedToTheBlockNumbers() throws IOException, IllegalAccessException {
        List<RecordFile> defaultRecordFiles = insertDefaultRecordFiles();
        FieldUtils.writeField(blockNumberMigration,"migrationProperties",blockNumberMigrationProperties,true);

        blockNumberMigration.doMigrate();
        long offset = 412;
        List<RecordFile> migratedRecordFiles = getAllRecordFiles();
        assertEquals(defaultRecordFiles.size(), migratedRecordFiles.size());
        for (int i = 0; i < defaultRecordFiles.size(); i++) {
            assertEquals(defaultRecordFiles.get(i).getIndex() + offset, migratedRecordFiles.get(i).getIndex());
        }
    }

    @Test
    public void ifCorrectConsensusEndNotFoundDoNothing() throws IOException, IllegalAccessException {
        List<RecordFile> defaultRecordFiles = insertDefaultRecordFiles();
        FieldUtils.writeField(
                blockNumberMigration,
                "migrationProperties",
                copyPropertiesOverwriteConsensusEnd(blockNumberMigrationProperties, -1),
                true);

        blockNumberMigration.doMigrate();

        List<RecordFile> migratedRecordFiles = getAllRecordFiles();
        assertEquals(defaultRecordFiles.size(), migratedRecordFiles.size());
        for (int i = 0; i < defaultRecordFiles.size(); i++) {
            assertEquals(defaultRecordFiles.get(i).getIndex(), migratedRecordFiles.get(i).getIndex());
        }
    }

    @Test
    public void ifBlockNumberIsAlreadyCorrectDoNothing() throws IOException, IllegalAccessException {
        List<RecordFile> defaultRecordFiles = insertDefaultRecordFiles();
        FieldUtils.writeField(
                blockNumberMigration,
                "migrationProperties",
                copyPropertiesOverwriteBlockNumber(blockNumberMigrationProperties, 8),
                true);

        blockNumberMigration.doMigrate();

        List<RecordFile> migratedRecordFiles = getAllRecordFiles();
        assertEquals(defaultRecordFiles.size(), migratedRecordFiles.size());
        for (int i = 0; i < defaultRecordFiles.size(); i++) {
            assertEquals(defaultRecordFiles.get(i).getIndex(), migratedRecordFiles.get(i).getIndex());
        }
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

    private List<RecordFile> getDefaultRecordFiles() throws IOException {
        String recordFilesFile = getClass()
                .getClassLoader()
                .getResource("blocks" + File.separator + "record_files.json")
                .getFile();


        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(new File(recordFilesFile), new TypeReference<List<RecordFile>>(){});
    }

    private List<RecordFile> insertDefaultRecordFiles() throws IOException {
        final List<RecordFile> defaultRecordFiles = getDefaultRecordFiles();

        String insertRecordFileSql = "insert into record_file (name, load_start, load_end, hash, prev_hash," +
                "consensus_start, consensus_end, node_account_id," +
                "count, digest_algorithm, version, file_hash, index)" +
                "values (?,?,?,?,?,?,?,3,?,0,?,?,?);";

        for (RecordFile recordFile : defaultRecordFiles) {
            jdbcOperations.update(ps -> {
                PreparedStatement preparedStatement = ps.prepareStatement(insertRecordFileSql);
                preparedStatement.setString(1, recordFile.getName());
                preparedStatement.setLong(2, recordFile.getLoadStart());
                preparedStatement.setLong(3, recordFile.getLoadEnd());
                preparedStatement.setString(4, recordFile.getHash());
                preparedStatement.setString(5, recordFile.getPreviousHash());
                preparedStatement.setLong(6, recordFile.getConsensusStart());
                preparedStatement.setLong(7, recordFile.getConsensusEnd());
                preparedStatement.setLong(8, recordFile.getCount());
                preparedStatement.setInt(9, recordFile.getVersion());
                preparedStatement.setString(10, recordFile.getFileHash());
                preparedStatement.setLong(11, recordFile.getIndex());
                return preparedStatement;
            });
        }

        return defaultRecordFiles;
    }

    private List<RecordFile> getAllRecordFiles(){
        return jdbcOperations.query("select * from record_file order by index", rowMapper(RecordFile.class));
    }
}
