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
import static com.hedera.mirror.importer.reader.block.ProtoBlockFileReaderTest.TEST_BLOCK_FILES;
import static org.apache.commons.lang3.StringUtils.countMatches;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.hedera.mirror.common.domain.transaction.BlockFile;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.ImporterProperties;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.addressbook.ConsensusNodeService;
import com.hedera.mirror.importer.domain.ConsensusNodeStub;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import com.hedera.mirror.importer.downloader.StreamFileNotifier;
import com.hedera.mirror.importer.downloader.provider.S3StreamFileProvider;
import com.hedera.mirror.importer.downloader.provider.StreamFileProvider;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.reader.block.ProtoBlockFileReader;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
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
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class BlockStreamPollerTest {

    @TempDir
    private Path archivePath;

    private BlockStreamVerifier blockStreamVerifier;
    private BlockStreamPoller blockStreamPoller;

    @Mock(strictness = Strictness.LENIENT)
    private ConsensusNodeService consensusNodeService;

    @TempDir
    private Path dataPath;

    private CommonDownloaderProperties commonProperties;
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
        commonProperties = new CommonDownloaderProperties(importerProperties);
        commonProperties.setPathType(PathType.NODE_ID);
        properties = new BlockPollerProperties();
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
        var blockFileTransformer = mock(BlockFileTransformer.class);
        lenient()
                .doAnswer(invocation -> {
                    var blockFile = invocation.getArgument(0, BlockFile.class);
                    // Only the minimal set: hash and index
                    return RecordFile.builder()
                            .hash(blockFile.getHash())
                            .index(blockFile.getIndex())
                            .build();
                })
                .when(blockFileTransformer)
                .transform(any(BlockFile.class));
        blockStreamVerifier = spy(
                new BlockStreamVerifier(blockFileTransformer, recordFileRepository, mock(StreamFileNotifier.class)));
        blockStreamPoller = new BlockStreamPoller(
                new ProtoBlockFileReader(),
                blockStreamVerifier,
                commonProperties,
                consensusNodeService,
                properties,
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

    @ParameterizedTest(name = "startBlockNumber={0}")
    @NullSource
    @ValueSource(longs = {7858855L})
    @SneakyThrows
    void poll(Long startBlockNumber, CapturedOutput output) {
        // given
        importerProperties.setStartBlockNumber(startBlockNumber);
        properties.setWriteFiles(true);
        fileCopier.filterFiles(blockFile(0).getName()).to("0").copy();
        fileCopier.filterFiles(blockFile(1).getName()).to("2").copy();
        when(recordFileRepository.findLatest())
                .thenReturn(Optional.of(RecordFile.builder()
                        .index(blockFile(0).getIndex() - 1)
                        .hash(blockFile(0).getPreviousHash())
                        .build()));

        // when
        blockStreamPoller.poll();

        // then
        verify(blockStreamVerifier)
                .verify(argThat(b -> b.getBytes() == null && b.getIndex() == 7858853L && b.getNodeId() == 0L));
        verify(consensusNodeService).getNodes();
        verify(recordFileRepository).findLatest();

        String logs = output.getAll();
        var nodeLogs = findAllMatches(logs, "Downloaded block file .*\\.blk\\.gz from node \\d");
        assertThat(nodeLogs)
                .containsExactly("Downloaded block file " + blockFile(0).getName() + " from node 0");

        // given now persist bytes
        properties.setPersistBytes(true);
        Mockito.reset(blockStreamVerifier);

        // when
        blockStreamPoller.poll();

        // then
        byte[] expectedBytes = FileUtils.readFileToByteArray(
                fileCopier.getTo().resolve("2").resolve(blockFile(1).getName()).toFile());
        verify(blockStreamVerifier)
                .verify(argThat(b ->
                        Arrays.equals(b.getBytes(), expectedBytes) && b.getIndex() == 7858854L && b.getNodeId() == 2L));
        verify(consensusNodeService, times(2)).getNodes();
        verify(recordFileRepository).findLatest();

        logs = output.getAll();
        nodeLogs = findAllMatches(logs, "Downloaded block file .*\\.blk\\.gz from node \\d");
        assertThat(nodeLogs)
                .containsExactly(
                        "Downloaded block file " + blockFile(0).getName() + " from node 0",
                        "Downloaded block file " + blockFile(1).getName() + " from node 2");
        assertThat(countMatches(logs, "Failed to download block file ")).isZero();

        verifyArchivedFile(blockFile(0).getName(), 0);
        verifyArchivedFile(blockFile(1).getName(), 2);
    }

    @Test
    void genesisNotFound(CapturedOutput output) {
        // given, when
        blockStreamPoller.poll();

        // then
        verify(blockStreamVerifier, never()).verify(any(BlockFile.class));
        verify(consensusNodeService).getNodes();
        verify(recordFileRepository).findLatest();

        String filename = BlockFile.getBlockStreamFilename(0L);
        String logs = output.getAll();
        assertThat(countMatches(logs, "Downloaded block file " + filename)).isZero();
        var nodeLogs = findAllMatches(logs, "Failed to process block file " + filename + " from node \\d");
        var expectedNodeLogs = nodes.stream()
                .map(ConsensusNode::getNodeId)
                .map(nodeId -> "Failed to process block file %s from node %d".formatted(filename, nodeId))
                .toList();
        assertThat(nodeLogs).containsExactlyInAnyOrderElementsOf(expectedNodeLogs);
        assertThat(countMatches(logs, "Failed to download block file " + filename))
                .isOne();
    }

    @SneakyThrows
    @Test
    void readerFailure(CapturedOutput output) {
        // given
        var filename = BlockFile.getBlockStreamFilename(0L);
        var genesisBlockFile = fileCopier.getTo().resolve("0").resolve(filename).toFile();
        FileUtils.writeByteArrayToFile(genesisBlockFile, gzip(generateRandomByteArray(1024)));

        // when
        blockStreamPoller.poll();

        // then
        verify(blockStreamVerifier, never()).verify(any(BlockFile.class));
        verify(consensusNodeService).getNodes();
        verify(recordFileRepository).findLatest();

        var logs = output.getAll();
        assertThat(countMatches(logs, "Downloaded block file " + filename + " from node 0"))
                .isOne();
        var errorLogs = findAllMatches(logs, "Failed to process block file " + filename + " from node \\d");
        var expected = nodes.stream()
                .map(ConsensusNode::getNodeId)
                .map(nodeId -> "Failed to process block file %s from node %d".formatted(filename, nodeId))
                .toList();
        assertThat(errorLogs).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(countMatches(logs, "Failed to download block file " + filename))
                .isOne();
    }

    @Test
    void startBlockNumber(CapturedOutput output) {
        // given
        var filename = blockFile(0).getName();
        importerProperties.setStartBlockNumber(blockFile(0).getIndex());
        fileCopier.filterFiles(filename).to("0").copy();
        doNothing().when(blockStreamVerifier).verify(any());

        // when
        blockStreamPoller.poll();

        // then
        verify(blockStreamVerifier)
                .verify(argThat(b -> b.getBytes() == null && b.getIndex() == 7858853 && b.getNodeId() == 0L));
        verify(consensusNodeService).getNodes();
        verify(recordFileRepository).findLatest();

        String logs = output.getAll();
        assertThat(findAllMatches(logs, "Downloaded block file .*\\.blk\\.gz from node \\d"))
                .containsExactly("Downloaded block file " + filename + " from node 0");
        assertThat(countMatches(logs, "Failed to download block file " + filename))
                .isZero();
    }

    @Test
    void timeout(CapturedOutput output) {
        // given
        String filename = BlockFile.getBlockStreamFilename(0L);
        commonProperties.setTimeout(Duration.ofMillis(100L));
        var streamFileProvider = mock(StreamFileProvider.class);
        when(streamFileProvider.get(any(), any()))
                .thenReturn(Mono.delay(Duration.ofMillis(120L)).then(Mono.empty()));
        var poller = new BlockStreamPoller(
                new ProtoBlockFileReader(),
                blockStreamVerifier,
                commonProperties,
                consensusNodeService,
                properties,
                streamFileProvider);

        // when
        poller.poll();

        // then
        verify(blockStreamVerifier, never()).verify(any(BlockFile.class));
        verify(consensusNodeService).getNodes();
        verify(recordFileRepository).findLatest();
        verify(streamFileProvider).get(any(), any());

        String logs = output.getAll();
        assertThat(countMatches(logs, "Downloaded block file " + filename)).isZero();
        assertThat(countMatches(logs, "Failed to download block file " + filename + "from node"))
                .isZero();
        assertThat(countMatches(logs, "Failed to process block file " + filename))
                .isOne();
        assertThat(countMatches(logs, "Failed to download block file " + filename))
                .isOne();
    }

    @Test
    void verifyFailure(CapturedOutput output) {
        // given
        var filename = blockFile(0).getName();
        doThrow(new InvalidStreamFileException("")).when(blockStreamVerifier).verify(any());
        when(recordFileRepository.findLatest())
                .thenReturn(Optional.of(RecordFile.builder()
                        .index(blockFile(0).getIndex() - 1)
                        .hash(blockFile(0).getPreviousHash())
                        .build()));
        fileCopier.filterFiles(filename).to("0").copy();
        fileCopier.filterFiles(filename).to("1").copy();

        // when
        blockStreamPoller.poll();

        // then
        verify(blockStreamVerifier, times(2)).verify(argThat(b -> b.getIndex() == 7858853L));
        verify(consensusNodeService).getNodes();
        verify(recordFileRepository).findLatest();

        var logs = output.getAll();
        var downloadedLogs = findAllMatches(logs, "Downloaded block file .*\\.blk\\.gz from node \\d");
        assertThat(downloadedLogs)
                .containsExactlyInAnyOrder(
                        "Downloaded block file " + filename + " from node 0",
                        "Downloaded block file " + filename + " from node 1");
        var errorLogs = findAllMatches(logs, "Failed to process block file " + filename + " from node \\d");
        var expected = nodes.stream()
                .map(ConsensusNode::getNodeId)
                .map(nodeId -> "Failed to process block file %s from node %d".formatted(filename, nodeId))
                .toList();
        assertThat(errorLogs).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(countMatches(logs, "Failed to download block file " + filename))
                .isOne();
    }

    @RepeatedTest(5)
    void verifyFailureThenSuccess(CapturedOutput output) {
        // given
        var filename = blockFile(0).getName();
        doThrow(new InvalidStreamFileException(""))
                .doCallRealMethod()
                .when(blockStreamVerifier)
                .verify(any(BlockFile.class));
        when(recordFileRepository.findLatest())
                .thenReturn(Optional.of(RecordFile.builder()
                        .index(blockFile(0).getIndex() - 1)
                        .hash(blockFile(0).getPreviousHash())
                        .build()));
        fileCopier.filterFiles(filename).to("0").copy();
        fileCopier.filterFiles(filename).to("1").copy();

        // when
        blockStreamPoller.poll();

        // then
        verify(blockStreamVerifier, times(2)).verify(argThat(b -> b.getIndex() == 7858853L));
        verify(consensusNodeService).getNodes();
        verify(recordFileRepository).findLatest();

        var logs = output.getAll();
        var downloadedLogs = findAllMatches(logs, "Downloaded block file " + filename + " from node \\d");
        assertThat(downloadedLogs)
                .containsExactlyInAnyOrder(
                        "Downloaded block file " + filename + " from node 0",
                        "Downloaded block file " + filename + " from node 1");
        assertThat(countMatches(logs, "Failed to process block file " + filename))
                .isBetween(1, 3);
        assertThat(countMatches(logs, "Failed to download block file " + filename))
                .isZero();
    }

    private static BlockFile blockFile(int index) {
        return TEST_BLOCK_FILES.get(index);
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
