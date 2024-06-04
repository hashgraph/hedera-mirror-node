package com.hedera.mirror.web3.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.hedera.mirror.rest.model.Block;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.exception.BlockNumberOutOfRangeException;
import com.hedera.mirror.web3.viewmodel.BlockType;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
public class RecordFileServiceTest extends Web3IntegrationTest {
    private final RecordFileService recordFileService;

    @Test
    void testFindByTimestamp() {
        var timestamp = domainBuilder.timestamp();
        var recordFile = domainBuilder.recordFile()
                .customize(e -> e.consensusEnd(timestamp))
                .persist();
        assertThat(recordFileService.findByTimestamp(timestamp)).get().isEqualTo(recordFile);
    }

    @Test
    void testFindByBlockTypeEarliest() {
        var recordFileEarliest = domainBuilder.recordFile().customize(f -> f.index(0L)).persist();
        domainBuilder.recordFile().customize(f -> f.index(1L)).persist();
        domainBuilder.recordFile().customize(f -> f.index(2L)).persist();
        assertThat(recordFileService.findByBlockType(BlockType.EARLIEST)).get().isEqualTo(recordFileEarliest);
    }

    @Test
    void testFindByBlockTypeLatest() {
        domainBuilder.recordFile().customize(f -> f.index(0L)).persist();
        domainBuilder.recordFile().customize(f -> f.index(1L)).persist();
        domainBuilder.recordFile().customize(f -> f.index(2L)).persist();
        var recordFileLatest = domainBuilder.recordFile().customize(f -> f.index(3L)).persist();
        assertThat(recordFileService.findByBlockType(BlockType.LATEST)).get().isEqualTo(recordFileLatest);
    }

    @Test
    void testFindByBlockTypeIndex() {
        domainBuilder.recordFile().customize(f -> f.index(0L)).persist();
        domainBuilder.recordFile().customize(f -> f.index(1L)).persist();
        var recordFile = domainBuilder.recordFile().customize(f -> f.index(2L)).persist();
        domainBuilder.recordFile().customize(f -> f.index(3L)).persist();
        var blockTypeToSearch = BlockType.of(recordFile.getIndex().toString());
        assertThat(recordFileService.findByBlockType(blockTypeToSearch)).get().isEqualTo(recordFile);
    }

    @Test
    void testFindByBlockTypeIndexOutOfRange() {
        domainBuilder.recordFile().customize(f -> f.index(0L)).persist();
        domainBuilder.recordFile().customize(f -> f.index(1L)).persist();
        domainBuilder.recordFile().customize(f -> f.index(2L)).persist();
        var recordFileLatest = domainBuilder.recordFile().customize(f -> f.index(3L)).persist();
        var numberOutOfRange = recordFileLatest.getIndex() + 1L;
        var blockTypeToSearch = BlockType.of(String.valueOf(numberOutOfRange));
        assertThrows(BlockNumberOutOfRangeException.class, () -> recordFileService.findByBlockType(blockTypeToSearch));
    }
}
