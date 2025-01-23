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

import static com.hedera.mirror.importer.TestUtils.S3_PROXY_PORT;
import static com.hedera.mirror.importer.TestUtils.generateRandomByteArray;
import static com.hedera.mirror.importer.TestUtils.gzip;
import static org.apache.commons.lang3.StringUtils.countMatches;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.hedera.mirror.common.domain.transaction.BlockFile;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.ImporterProperties;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.addressbook.ConsensusNodeService;
import com.hedera.mirror.importer.domain.ConsensusNodeStub;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import com.hedera.mirror.importer.downloader.provider.S3StreamFileProvider;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.reader.block.ProtoBlockFileReader;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.gaul.s3proxy.S3Proxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class BlockStreamPollerTest {

    private static final String[] BLOCK_STREAM_FILENAMES =
            new String[] {BlockFile.getBlockStreamFilename(7858853L), BlockFile.getBlockStreamFilename(7858854L)};

    @TempDir
    private Path archivePath;

    private BlockStreamPoller blockStreamPoller;

    @Mock
    private BlockStreamVerifier blockStreamVerifier;

    @Mock(strictness = Strictness.LENIENT)
    private ConsensusNodeService consensusNodeService;

    @TempDir
    private Path dataPath;

    private FileCopier fileCopier;
    private ImporterProperties importerProperties;
    private List<ConsensusNode> nodes;
    private BlockPollerProperties properties;

    @Mock
    private RecordFileRepository recordFileRepository;

    private S3Proxy s3Proxy;

    @BeforeEach
    void setup() {
        if (LoggerFactory.getLogger(getClass().getPackageName()) instanceof Logger log) {
            log.setLevel(Level.DEBUG);
        }

        importerProperties = new ImporterProperties();
        importerProperties.setDataPath(archivePath);
        var commonProperties = new CommonDownloaderProperties(importerProperties);
        commonProperties.setPathType(PathType.NODE_ID);
        properties = new BlockPollerProperties(commonProperties);
        properties.setEnabled(true);

        nodes = List.of(
                ConsensusNodeStub.builder().nodeId(0).build(),
                ConsensusNodeStub.builder().nodeId(1).build(),
                ConsensusNodeStub.builder().nodeId(2).build(),
                ConsensusNodeStub.builder().nodeId(3).build());
        when(consensusNodeService.getNodes()).thenReturn(nodes);

        s3Proxy = TestUtils.startS3Proxy(dataPath);
        var s3AsyncClient = S3AsyncClient.builder()
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .endpointOverride(URI.create("http://localhost:" + S3_PROXY_PORT))
                .forcePathStyle(true)
                .region(Region.of(commonProperties.getRegion()))
                .build();
        var streamFileProvider = new S3StreamFileProvider(commonProperties, s3AsyncClient);

        blockStreamPoller = new BlockStreamPoller(
                properties,
                new ProtoBlockFileReader(),
                blockStreamVerifier,
                consensusNodeService,
                recordFileRepository,
                streamFileProvider);

        var fromPath = Path.of("data", "blockstreams");
        fileCopier = FileCopier.create(
                        TestUtils.getResource(fromPath.toString()).toPath(), dataPath)
                .to(commonProperties.getBucketName())
                .to(Long.toString(importerProperties.getShard()));
    }

    @AfterEach
    @SneakyThrows
    void teardown() {
        s3Proxy.stop();
    }

    @Test
    void disabled() {
        properties.setEnabled(false);
        blockStreamPoller.poll();
        verifyNoInteractions(blockStreamVerifier);
        verifyNoInteractions(consensusNodeService);
        verifyNoInteractions(recordFileRepository);
    }

    @SneakyThrows
    @Test
    void poll(CapturedOutput output) {
        // given
        properties.setWriteFiles(true);
        fileCopier.filterFiles(BLOCK_STREAM_FILENAMES[0]).to(Long.toString(0)).copy();
        fileCopier.filterFiles(BLOCK_STREAM_FILENAMES[1]).to(Long.toString(2)).copy();
        doNothing().when(blockStreamVerifier).verify(any());
        when(recordFileRepository.findLatest())
                .thenReturn(Optional.of(RecordFile.builder()
                        .index(7858852L)
                        .hash(DomainUtils.bytesToHex(generateRandomByteArray(48)))
                        .build()));

        // when
        blockStreamPoller.poll();

        // then
        verify(blockStreamVerifier).verify(argThat(b -> b.getBytes() == null && b.getIndex() == 7858853L));
        verify(consensusNodeService).getNodes();
        verify(recordFileRepository).findLatest();

        String logs = output.getAll();
        var nodeLogs = findAllMatches(logs, "Downloaded block file .*\\.blk\\.gz from node \\d");
        assertThat(nodeLogs).containsExactly("Downloaded block file " + BLOCK_STREAM_FILENAMES[0] + " from node 0");

        // given now persist bytes
        properties.setPersistBytes(true);
        Mockito.reset(blockStreamVerifier);

        // when
        blockStreamPoller.poll();

        // then
        byte[] expectedBytes = FileUtils.readFileToByteArray(fileCopier
                .getTo()
                .resolve(Long.toString(2))
                .resolve(BLOCK_STREAM_FILENAMES[1])
                .toFile());
        verify(blockStreamVerifier)
                .verify(argThat(b -> Arrays.equals(b.getBytes(), expectedBytes) && b.getIndex() == 7858854L));
        verify(consensusNodeService, times(2)).getNodes();
        verify(recordFileRepository).findLatest();

        logs = output.getAll();
        nodeLogs = findAllMatches(logs, "Downloaded block file .*\\.blk\\.gz from node \\d");
        assertThat(nodeLogs)
                .containsExactly(
                        "Downloaded block file " + BLOCK_STREAM_FILENAMES[0] + " from node 0",
                        "Downloaded block file " + BLOCK_STREAM_FILENAMES[1] + " from node 2");

        verifyArchivedFile(BLOCK_STREAM_FILENAMES[0], 0);
        verifyArchivedFile(BLOCK_STREAM_FILENAMES[1], 2);
    }

    @Test
    void genesisNotFound(CapturedOutput capturedOutput) {
        // given, when
        blockStreamPoller.poll();

        // then
        verifyNoInteractions(blockStreamVerifier);
        verify(consensusNodeService).getNodes();
        verify(recordFileRepository).findLatest();

        String filename = BlockFile.getBlockStreamFilename(0L);
        String logs = capturedOutput.getAll();
        var nodeLogs = findAllMatches(logs, "Error downloading block file " + filename + " from node \\d");
        var expectedNodeLogs = nodes.stream()
                .map(ConsensusNode::getNodeId)
                .map(nodeId -> String.format("Error downloading block file %s from node %d", filename, nodeId))
                .toList();
        assertThat(nodeLogs).containsExactlyInAnyOrderElementsOf(expectedNodeLogs);
        assertThat(countMatches(logs, "Failed to download block file " + filename))
                .isOne();
    }

    @SneakyThrows
    @Test
    void readerFailure(CapturedOutput capturedOutput) {
        // given
        var filename = BlockFile.getBlockStreamFilename(0L);
        var genesisBlockFile =
                fileCopier.getTo().resolve(Long.toString(0)).resolve(filename).toFile();
        FileUtils.writeByteArrayToFile(genesisBlockFile, gzip(generateRandomByteArray(1024)));

        // when
        blockStreamPoller.poll();

        // then
        verifyNoInteractions(blockStreamVerifier);
        verify(consensusNodeService).getNodes();
        verify(recordFileRepository).findLatest();

        var logs = capturedOutput.getAll();
        assertThat(countMatches(logs, "Downloaded block file " + filename + " from node 0"))
                .isOne();
        assertThat(countMatches(logs, "Error reading block file " + filename + " from node 0"))
                .isOne();
        assertThat(countMatches(logs, "Failed to download block file " + filename))
                .isOne();
    }

    @Test
    void verifyFailure(CapturedOutput capturedOutput) {
        // given
        var filename = BLOCK_STREAM_FILENAMES[0];
        doThrow(new InvalidStreamFileException("")).when(blockStreamVerifier).verify(any());
        when(recordFileRepository.findLatest())
                .thenReturn(Optional.of(RecordFile.builder()
                        .index(7858852L)
                        .hash(DomainUtils.bytesToHex(generateRandomByteArray(48)))
                        .build()));
        fileCopier.filterFiles(filename).to(Long.toString(0)).copy();
        fileCopier.filterFiles(filename).to(Long.toString(1)).copy();

        // when
        blockStreamPoller.poll();

        // then
        verify(blockStreamVerifier, times(2)).verify(argThat(b -> b.getIndex() == 7858853L));
        verify(consensusNodeService).getNodes();
        verify(recordFileRepository).findLatest();

        var logs = capturedOutput.getAll();
        var verifyFailureLogs = findAllMatches(logs, "Error verifying block file " + filename + " from node \\d");
        assertThat(verifyFailureLogs)
                .containsExactlyInAnyOrder(
                        "Error verifying block file " + filename + " from node 0",
                        "Error verifying block file " + filename + " from node 1");
        assertThat(countMatches(logs, "Failed to download block file " + filename))
                .isOne();
    }

    @Test
    void verifyFailureThenSuccess(CapturedOutput capturedOutput) {
        // given
        var filename = BLOCK_STREAM_FILENAMES[0];
        doThrow(new InvalidStreamFileException(""))
                .doNothing()
                .when(blockStreamVerifier)
                .verify(any());
        when(recordFileRepository.findLatest())
                .thenReturn(Optional.of(RecordFile.builder()
                        .index(7858852L)
                        .hash(DomainUtils.bytesToHex(generateRandomByteArray(48)))
                        .build()));
        fileCopier.filterFiles(filename).to(Long.toString(0)).copy();
        fileCopier.filterFiles(filename).to(Long.toString(1)).copy();

        // when
        blockStreamPoller.poll();

        // then
        verify(blockStreamVerifier, times(2)).verify(argThat(b -> b.getIndex() == 7858853L));
        verify(consensusNodeService).getNodes();
        verify(recordFileRepository).findLatest();

        var logs = capturedOutput.getAll();
        var downloadedLogs = findAllMatches(logs, "Downloaded block file " + filename + " from node \\d");
        assertThat(downloadedLogs)
                .containsExactlyInAnyOrder(
                        "Downloaded block file " + filename + " from node 0",
                        "Downloaded block file " + filename + " from node 1");
        assertThat(countMatches(logs, "Error verifying block file " + filename)).isOne();
        assertThat(countMatches(logs, "Failed to download block file " + filename))
                .isZero();
    }

    @SneakyThrows
    private void verifyArchivedFile(String filename, long nodeId) {
        byte[] expected = FileUtils.readFileToByteArray(fileCopier
                .getTo()
                .resolve(Long.toString(nodeId))
                .resolve(filename)
                .toFile());
        var actualFile = importerProperties
                .getStreamPath()
                .resolve(Long.toString(importerProperties.getShard()))
                .resolve(Long.toString(nodeId))
                .resolve(filename)
                .toFile();
        assertThat(actualFile).isFile();
        byte[] actual = FileUtils.readFileToByteArray(actualFile);
        assertThat(actual).isEqualTo(expected);
    }

    private Collection<String> findAllMatches(String message, String pattern) {
        var matcher = Pattern.compile(pattern).matcher(message);
        var result = new ArrayList<String>();
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }
}
