/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.downloader;

import static com.hedera.mirror.common.domain.entity.EntityType.FILE;
import static com.hedera.mirror.importer.domain.StreamFilename.FileType.DATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.StreamFile;
import com.hedera.mirror.common.domain.StreamItem;
import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.addressbook.AddressBookEntry;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.addressbook.ConsensusNodeService;
import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;
import com.hedera.mirror.importer.domain.ConsensusNodeStub;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.parser.record.sidecar.SidecarProperties;
import com.hedera.mirror.importer.reader.signature.CompositeSignatureFileReader;
import com.hedera.mirror.importer.reader.signature.ProtoSignatureFileReader;
import com.hedera.mirror.importer.reader.signature.SignatureFileReader;
import com.hedera.mirror.importer.reader.signature.SignatureFileReaderV2;
import com.hedera.mirror.importer.reader.signature.SignatureFileReaderV5;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.CustomLog;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.gaul.s3proxy.S3Proxy;
import org.gaul.shaded.org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStoreContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.ResourceUtils;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@CustomLog
@ExtendWith(MockitoExtension.class)
public abstract class AbstractBucketRestructureDownloaderTest {

    private static final int S3_PROXY_PORT = 8001;
    private static final Pattern STREAM_FILENAME_INSTANT_PATTERN =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}_\\d{2}_\\d{2}(\\.\\d{1,9})?Z");

    @Mock(lenient = true)
    protected ConsensusNodeService consensusNodeService;

    @Mock
    protected StreamFileNotifier streamFileNotifier;

    @Mock
    protected MirrorDateRangePropertiesProcessor dateRangeProcessor;

    @TempDir
    protected Path s3Path;

    protected S3Proxy s3Proxy;
    protected FileCopier fileCopier;
    protected CommonDownloaderProperties commonDownloaderProperties;
    protected MirrorProperties mirrorProperties;
    protected S3AsyncClient s3AsyncClient;
    protected DownloaderProperties downloaderProperties;
    protected Downloader<? extends StreamFile<? extends StreamItem>, ? extends StreamItem> downloader;
    protected MeterRegistry meterRegistry = new SimpleMeterRegistry();
    protected String file1, file2, file3, file4;
    protected Instant file1Instant;
    protected Instant file2Instant;
    protected List<Pair<Instant, String>> instantFilenamePairs;
    protected EntityId corruptedNodeAccountId;
    protected NodeSignatureVerifier nodeSignatureVerifier;
    protected Collection<ConsensusNode> nodes;
    protected SignatureFileReader signatureFileReader;
    protected StreamType streamType;

    protected Path testnet = Path.of("testnet", "0");
    protected long firstIndex = 0L;

    protected Map<String, RecordFile> recordFileMap;

    protected SidecarProperties sidecarProperties;

    @TempDir
    private Path dataPath;

    protected void setTestFilesAndInstants(List<String> files) {
        file1 = files.get(0);
        file2 = files.get(1);
        file3 = files.get(2);
        file4 = files.get(3);

        file1Instant = new StreamFilename(file1).getInstant();
        file2Instant = new StreamFilename(file2).getInstant();
        instantFilenamePairs = List.of(Pair.of(file1Instant, file1), Pair.of(file2Instant, file2));
    }

    protected void loadAddressBook(String filename) {
        try {
            Path path = ResourceUtils.getFile(String.format("classpath:addressbook/%s", filename))
                    .toPath();
            byte[] bytes = Files.readAllBytes(path);
            var addressBook = NodeAddressBook.parseFrom(bytes);
            nodes = addressBook.getNodeAddressList().stream()
                    .map(e -> {
                        var entry = AddressBookEntry.builder()
                                .publicKey(e.getRSAPubKey())
                                .build();
                        @SuppressWarnings("deprecation")
                        var id = e.hasNodeAccountId()
                                ? EntityId.of(e.getNodeAccountId())
                                : EntityId.of(e.getMemo().toStringUtf8(), FILE);
                        return ConsensusNodeStub.builder()
                                .nodeAccountId(id)
                                .nodeId(id.getEntityNum() - 3)
                                .publicKey(entry.getPublicKeyObject())
                                .stake(1L)
                                .totalStake(addressBook.getNodeAddressCount())
                                .build();
                    })
                    .collect(Collectors.toList());
            when(consensusNodeService.getNodes()).thenReturn(nodes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Implementation can assume that mirrorProperties and commonDownloaderProperties have been initialized.
    protected abstract DownloaderProperties getDownloaderProperties();

    protected Map<String, Long> getExpectedFileIndexMap() {
        return Collections.emptyMap();
    }

    protected abstract Downloader<? extends StreamFile<? extends StreamItem>, ? extends StreamItem> getDownloader();

    protected abstract Path getTestDataDir();

    protected abstract Duration getCloseInterval();

    @SneakyThrows
    protected void beforeEach() {
        loadAddressBook("testnet");
        initProperties();
        s3AsyncClient = S3AsyncClient.builder()
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .endpointOverride(URI.create(commonDownloaderProperties.getEndpointOverride()))
                .forcePathStyle(true)
                .region(Region.of(commonDownloaderProperties.getRegion()))
                .build();

        signatureFileReader = new CompositeSignatureFileReader(
                new SignatureFileReaderV2(), new SignatureFileReaderV5(), new ProtoSignatureFileReader());
        var consensusValidator = new ConsensusValidatorImpl(commonDownloaderProperties);
        nodeSignatureVerifier = new NodeSignatureVerifier(consensusValidator);
        downloader = getDownloader();
        streamType = downloaderProperties.getStreamType();

        fileCopier = FileCopier.create(TestUtils.getResource("data").toPath(), s3Path, streamType);
        startS3Proxy();
    }

    @AfterEach
    void after() throws Exception {
        s3Proxy.stop();
    }

    private void initProperties() {
        mirrorProperties = new MirrorProperties();
        mirrorProperties.setDataPath(dataPath);
        mirrorProperties.setStartBlockNumber(101L);
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.TESTNET);
        mirrorProperties.setNetworkPrefix("testnet");

        commonDownloaderProperties = new CommonDownloaderProperties(mirrorProperties);
        commonDownloaderProperties.setEndpointOverride("http://localhost:" + S3_PROXY_PORT);

        downloaderProperties = getDownloaderProperties();
    }

    private void startS3Proxy() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(
                "jclouds.filesystem.basedir", s3Path.toAbsolutePath().toString());

        BlobStoreContext context =
                ContextBuilder.newBuilder("filesystem").overrides(properties).build(BlobStoreContext.class);

        s3Proxy = S3Proxy.builder()
                .blobStore(context.getBlobStore())
                .endpoint(URI.create("http://localhost:" + S3_PROXY_PORT))
                .ignoreUnknownHeaders(true)
                .build();
        s3Proxy.start();

        await("S3Proxy")
                .dontCatchUncaughtExceptions()
                .atMost(Duration.ofMillis(500))
                .pollDelay(Duration.ofMillis(1))
                .until(() -> AbstractLifeCycle.STARTED.equals(s3Proxy.getState()));
    }

    @Test
    @DisplayName("Download and verify files from old path")
    void download() {
        mirrorProperties.setStartBlockNumber(null);

        fileCopier
                .from(getTestDataDir())
                .to(commonDownloaderProperties.getBucketName(), streamType.getPath())
                .copy();
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();

        verifyStreamFiles(List.of(file1, file2));
        assertThat(downloaderProperties.getStreamPath()).doesNotExist();
    }

    @Test
    @DisplayName("Download and verify files from new bucket")
    void downloadFilesFromNewPath() {
        commonDownloaderProperties.setPathType(CommonDownloaderProperties.PathType.NODE_ID);
        fileCopier
                .from(testnet)
                .to(commonDownloaderProperties.getBucketName(), testnet.toString())
                .copy();
        expectLastStreamFile(file2Instant);
        downloader.download();
        verifyStreamFiles(List.of(file3, file4));
    }

    @SuppressWarnings("java:S6103")
    @SafeVarargs
    protected final void verifyStreamFiles(List<String> files, Consumer<StreamFile<?>>... extraAsserts) {
        var captor = ArgumentCaptor.forClass(StreamFile.class);
        var expectedFileIndexMap = getExpectedFileIndexMap();
        var index = new AtomicLong(firstIndex);

        verify(streamFileNotifier, times(files.size())).verified(captor.capture());
        var streamFileAssert = assertThat(captor.getAllValues())
                .allMatch(s -> files.contains(s.getName()))
                .allMatch(s -> {
                    var expected = expectedFileIndexMap.get(s.getName());
                    if (expected != null) {
                        return Objects.equals(s.getIndex(), expected);
                    } else {
                        return s.getIndex() == null || s.getIndex() == index.getAndIncrement();
                    }
                })
                .allMatch(s -> downloaderProperties.isPersistBytes() ^ (s.getBytes() == null));

        for (var extraAssert : extraAsserts) {
            streamFileAssert.allSatisfy(extraAssert::accept);
        }

        if (!files.isEmpty()) {
            var lastFilename = files.get(files.size() - 1);
            var lastStreamFile = downloader.lastStreamFile.get();
            assertThat(lastStreamFile)
                    .isNotEmpty()
                    .get()
                    .returns(null, StreamFile::getBytes)
                    .returns(null, StreamFile::getItems)
                    .returns(lastFilename, StreamFile::getName);
        }
    }

    /**
     * Sets the expected last stream file. If the precondition is there is no stream files in db, pass in a null index.
     *
     * @param hash    hash of the StreamFile
     * @param index   the index of the StreamFile
     * @param instant the instant of the StreamFile
     */
    protected void expectLastStreamFile(String hash, Long index, Instant instant) {
        var streamFile = streamType.newStreamFile();
        streamFile.setName(StreamFilename.getFilename(streamType, DATA, instant));
        streamFile.setConsensusStart(DomainUtils.convertToNanosMax(instant));
        streamFile.setHash(hash);
        streamFile.setIndex(index);

        if (hash != null) {
            downloaderProperties.getMirrorProperties().setVerifyHashAfter(instant);
        }
        firstIndex = index == null ? 0L : index + 1;
        doReturn(Optional.of(streamFile)).when(dateRangeProcessor).getLastStreamFile(streamType);
    }

    /**
     * Sets the last stream file based on instant, with the assumption that the stream file db table is empty.
     *
     * @param instant the instant of the stream file
     */
    protected void expectLastStreamFile(Instant instant) {
        expectLastStreamFile(null, 0L, instant);
    }
}
