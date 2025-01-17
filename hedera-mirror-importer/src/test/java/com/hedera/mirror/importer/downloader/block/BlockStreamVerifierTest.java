/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.downloader.block;

import static com.hedera.mirror.common.domain.DigestAlgorithm.SHA_384;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.StreamFile;
import com.hedera.mirror.common.domain.transaction.BlockFile;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.downloader.StreamFileNotifier;
import com.hedera.mirror.importer.exception.HashMismatchException;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import java.time.Instant;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockStreamVerifierTest {

    @Mock(strictness = LENIENT)
    private BlockFileTransformer blockFileTransformer;

    @Mock
    private RecordFileRepository recordFileRepository;

    @Mock
    private StreamFileNotifier streamFileNotifier;

    private RecordFile expectedRecordFile;
    private BlockStreamVerifier verifier;

    @BeforeEach
    void setup() {
        verifier = new BlockStreamVerifier(blockFileTransformer, recordFileRepository, streamFileNotifier);
        expectedRecordFile = RecordFile.builder().build();
        when(blockFileTransformer.transform(any())).thenReturn(expectedRecordFile);
    }

    @Test
    void verifyWithEmptyDb() {
        // given
        when(recordFileRepository.findLatest()).thenReturn(Optional.empty());
        var blockFile = getBlockFile(null);

        // when
        verifier.verify(blockFile);

        // then
        verify(blockFileTransformer).transform(blockFile);
        verify(recordFileRepository).findLatest();
        verify(streamFileNotifier).verified(expectedRecordFile);

        // given next block file
        blockFile = getBlockFile(blockFile);

        // when
        verifier.verify(blockFile);

        // then
        verify(blockFileTransformer).transform(blockFile);
        verify(recordFileRepository).findLatest();
        verify(streamFileNotifier, times(2)).verified(expectedRecordFile);
    }

    @Test
    void verifyWithPreviousFileInDb() {
        // given
        var previous = getRecordFile();
        when(recordFileRepository.findLatest()).thenReturn(Optional.of(previous));
        var blockFile = getBlockFile(previous);

        // when
        verifier.verify(blockFile);

        // then
        verify(blockFileTransformer).transform(blockFile);
        verify(recordFileRepository).findLatest();
        verify(streamFileNotifier).verified(expectedRecordFile);

        // given next block file
        blockFile = getBlockFile(blockFile);

        // when
        verifier.verify(blockFile);

        // then
        verify(blockFileTransformer).transform(blockFile);
        verify(recordFileRepository).findLatest();
        verify(streamFileNotifier, times(2)).verified(expectedRecordFile);
    }

    @Test
    void blockNumberMismatch() {
        // given
        when(recordFileRepository.findLatest()).thenReturn(Optional.empty());
        var blockFile = getBlockFile(null);
        blockFile.setIndex(blockFile.getIndex() + 1);

        // when, then
        assertThatThrownBy(() -> verifier.verify(blockFile))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Block number mismatch");
        verifyNoInteractions(blockFileTransformer);
        verify(recordFileRepository).findLatest();
        verifyNoInteractions(streamFileNotifier);
    }

    @Test
    void hashMismatch() {
        // given
        var previous = getRecordFile();
        when(recordFileRepository.findLatest()).thenReturn(Optional.of(previous));
        var blockFile = getBlockFile(previous);
        blockFile.setPreviousHash(sha384Hash());

        // when, then
        assertThatThrownBy(() -> verifier.verify(blockFile))
                .isInstanceOf(HashMismatchException.class)
                .hasMessageContaining("Previous hash mismatch");
        verifyNoInteractions(blockFileTransformer);
        verify(recordFileRepository).findLatest();
        verifyNoInteractions(streamFileNotifier);
    }

    private BlockFile getBlockFile(StreamFile<?> previous) {
        long blockNumber = previous != null ? previous.getIndex() + 1 : DomainUtils.convertToNanosMax(Instant.now());
        String previousHash = previous != null ? previous.getHash() : sha384Hash();
        return BlockFile.builder()
                .hash(sha384Hash())
                .index(blockNumber)
                .name(getBlockFilename(blockNumber))
                .previousHash(previousHash)
                .build();
    }

    @Test
    void malformedFilename() {
        // given
        when(recordFileRepository.findLatest()).thenReturn(Optional.empty());
        var blockFile = getBlockFile(null);
        blockFile.setName(DomainUtils.bytesToHex(TestUtils.generateRandomByteArray(4)));

        // when, then
        assertThatThrownBy(() -> verifier.verify(blockFile))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Failed to parse block number from filename");
        verifyNoInteractions(blockFileTransformer);
        verify(recordFileRepository).findLatest();
        verifyNoInteractions(streamFileNotifier);
    }

    @Test
    void nonConsecutiveBlockNumber() {
        // given
        var previous = getRecordFile();
        when(recordFileRepository.findLatest()).thenReturn(Optional.of(previous));
        var blockFile = getBlockFile(null);

        // when, then
        assertThatThrownBy(() -> verifier.verify(blockFile))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Non-consecutive block number");
        verifyNoInteractions(blockFileTransformer);
        verify(recordFileRepository).findLatest();
        verifyNoInteractions(streamFileNotifier);
    }

    private String getBlockFilename(long blockNumber) {
        return StringUtils.leftPad(String.valueOf(blockNumber), 36, "0") + ".blk.gz";
    }

    private RecordFile getRecordFile() {
        long index = DomainUtils.convertToNanosMax(Instant.now());
        return RecordFile.builder().hash(sha384Hash()).index(index).build();
    }

    private String sha384Hash() {
        return DomainUtils.bytesToHex(TestUtils.generateRandomByteArray(SHA_384.getSize()));
    }
}
