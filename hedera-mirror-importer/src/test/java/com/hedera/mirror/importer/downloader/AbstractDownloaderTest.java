/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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
import static com.hedera.mirror.importer.domain.StreamFilename.FileType.SIGNATURE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Iterables;
import com.hedera.mirror.common.domain.StreamFile;
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
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import com.hedera.mirror.importer.reader.signature.CompositeSignatureFileReader;
import com.hedera.mirror.importer.reader.signature.ProtoSignatureFileReader;
import com.hedera.mirror.importer.reader.signature.SignatureFileReader;
import com.hedera.mirror.importer.reader.signature.SignatureFileReaderV2;
import com.hedera.mirror.importer.reader.signature.SignatureFileReaderV5;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
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
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.CustomLog;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.ResourceUtils;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@CustomLog
@ExtendWith(MockitoExtension.class)
public abstract class AbstractDownloaderTest<T extends StreamFile<?>> {

    private static final int S3_PROXY_PORT = 8001;
    private static final Pattern STREAM_FILENAME_INSTANT_PATTERN =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}_\\d{2}_\\d{2}(\\.\\d{1,9})?Z");

    @Mock(strictness = LENIENT)
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
    protected Downloader<T, ?> downloader;
    protected MeterRegistry meterRegistry = new SimpleMeterRegistry();
    protected String file1;
    protected String file2;
    protected Instant file1Instant;
    protected Instant file2Instant;
    protected List<Pair<Instant, String>> instantFilenamePairs;
    protected EntityId corruptedNodeAccountId;
    protected NodeSignatureVerifier nodeSignatureVerifier;
    protected Collection<ConsensusNode> nodes;
    protected SignatureFileReader signatureFileReader;
    protected StreamType streamType;
    protected long firstIndex = 0L;

    @Captor
    private ArgumentCaptor<T> streamFileCaptor;

    @TempDir
    private Path dataPath;

    protected static void corruptFile(Path p) {
        try {
            File file = p.toFile();
            if (file.isFile()) {
                FileUtils.writeStringToFile(file, "corrupt", "UTF-8", true);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void truncateFile(Path p) {
        try {
            File file = p.toFile();
            if (file.isFile()) {
                try (FileOutputStream fileOutputStream = new FileOutputStream(file, true);
                        FileChannel channel = fileOutputStream.getChannel()) {
                    if (channel.size() <= 48) {
                        channel.truncate(channel.size() / 2);
                    } else {
                        channel.truncate(48);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    protected abstract Downloader<T, ?> getDownloader();

    protected abstract Path getTestDataDir();

    protected abstract Duration getCloseInterval();

    boolean isSigFile(Path path) {
        return path.toString().contains(StreamType.SIGNATURE_SUFFIX);
    }

    boolean isStreamFile(Path path) {
        StreamType streamType = downloaderProperties.getStreamType();

        for (StreamType.Extension extension : streamType.getDataExtensions()) {
            if (path.toString().contains("." + extension.getName())) {
                return true;
            }
        }

        return false;
    }

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

        fileCopier = FileCopier.create(TestUtils.getResource("data").toPath(), s3Path)
                .from(getTestDataDir())
                .to(commonDownloaderProperties.getBucketName(), streamType.getPath());

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

    @ParameterizedTest(name = "Download and verify files with path type: {0}")
    @EnumSource(value = PathType.class, mode = Mode.EXCLUDE, names = "NODE_ID")
    void download(PathType pathType) {
        mirrorProperties.setStartBlockNumber(null);
        commonDownloaderProperties.setPathType(pathType);

        fileCopier.copy();
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();

        verifyForSuccess();
        assertThat(downloaderProperties.getStreamPath()).isEmptyDirectory();
    }

    @Test
    @DisplayName("Non-unanimous consensus reached")
    void partialConsensus() throws IOException {
        mirrorProperties.setStartBlockNumber(null);

        fileCopier.copy();
        var nodePath =
                fileCopier.getTo().resolve(downloaderProperties.getStreamType().getNodePrefix() + "0.0.6");
        FileUtils.deleteDirectory(nodePath.toFile());
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();

        verifyForSuccess();
    }

    @Test
    @DisplayName("Exactly 1/3 consensus")
    void oneThirdConsensus() {
        nodes.forEach(c -> ((ConsensusNodeStub) c).setTotalStake(3));
        nodes.remove(Iterables.getLast(nodes));
        mirrorProperties.setStartBlockNumber(null);
        var nodeAccountId = nodes.iterator().next().getNodeAccountId();

        var nodePath = downloaderProperties.getStreamType().getNodePrefix() + nodeAccountId;
        fileCopier.from(nodePath).to(nodePath).copy();
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();

        verifyForSuccess();
    }

    @Test
    @DisplayName("Less than 1/3 consensus")
    void lessThanOneThirdConsensus() {
        nodes.forEach(c -> ((ConsensusNodeStub) c).setTotalStake(4));
        nodes.remove(Iterables.getLast(nodes));
        mirrorProperties.setStartBlockNumber(null);
        var nodeAccountId = nodes.iterator().next().getNodeAccountId();

        var nodePath = downloaderProperties.getStreamType().getNodePrefix() + nodeAccountId;
        fileCopier.from(nodePath).to(nodePath).copy();
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();
        verifyUnsuccessful();
    }

    @Test
    @DisplayName("Missing signatures")
    void missingSignatures() {
        fileCopier.filterFiles(file -> !isSigFile(file.toPath())).copy(); // only copy data files
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();
        verifyUnsuccessful();
    }

    @Test
    @DisplayName("Missing data files")
    void missingDataFiles() {
        fileCopier.filterFiles("*_sig*").copy();
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();
        verifyUnsuccessful();
    }

    @Test
    @DisplayName("Less than 1/3 signatures")
    void lessThanOneThirdSignatures() {
        fileCopier.filterDirectories("*0.0.3").copy();
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();
        verifyUnsuccessful();
    }

    @Test
    @DisplayName("Signature doesn't match file")
    void signatureMismatch() throws Exception {
        fileCopier.copy();
        Files.walk(s3Path).filter(this::isSigFile).forEach(AbstractDownloaderTest::corruptFile);
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();
        verifyUnsuccessful();
    }

    @Test
    @DisplayName("Invalid or incomplete file with garbage data appended")
    void invalidFileWithGarbageAppended() throws Exception {
        fileCopier.copy();
        Files.walk(s3Path).filter(file -> !isSigFile(file)).forEach(AbstractDownloaderTest::corruptFile);
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();
        verifyUnsuccessful();
    }

    @Test
    @DisplayName("Invalid or incomplete file with data truncated")
    void invalidFileWithDataTruncated() throws Exception {
        fileCopier.copy();
        Files.walk(s3Path).filter(file -> !isSigFile(file)).forEach(AbstractDownloaderTest::truncateFile);
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();
        verifyUnsuccessful();
    }

    @Test
    @DisplayName("Write stream files")
    void writeFiles() throws Exception {
        downloaderProperties.setWriteFiles(true);
        fileCopier.copy();
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();
        assertThat(Files.walk(downloaderProperties.getStreamPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSizeGreaterThan(0)
                .allMatch(this::isStreamFile);
    }

    @Test
    @DisplayName("Write signature files")
    void writeSignatureFiles() throws Exception {
        downloaderProperties.setWriteSignatures(true);
        fileCopier.copy();
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();
        assertThat(Files.walk(downloaderProperties.getStreamPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSizeGreaterThan(0)
                .allMatch(this::isSigFile);
    }

    @Test
    @DisplayName("Different filenames, same interval")
    void differentFilenamesSameInterval() {
        differentFilenames(Duration.ofNanos(1L));
    }

    @Test
    @DisplayName("Different filenames, same interval, lower bound")
    void differentFilenamesSameIntervalLower() {
        differentFilenames(getCloseInterval().dividedBy(2L).negated());
    }

    @Test
    @DisplayName("Different filenames, same interval, upper bound")
    void differentFilenamesSameIntervalUpper() {
        differentFilenames(getCloseInterval().dividedBy(2L).minusNanos(1));
    }

    @Test
    @DisplayName("Different filenames, previous interval")
    void differentFilenamesPreviousInterval() {
        differentFilenames(getCloseInterval().dividedBy(2L).negated().minusNanos(2));
    }

    @Test
    @DisplayName("Different filenames, next interval")
    void differentFilenamesNextInterval() {
        differentFilenames(getCloseInterval().dividedBy(2L));
    }

    @Test
    @DisplayName("Download and verify two group of files in the same bucket")
    void downloadValidFilesInSameBucket() {
        mirrorProperties.setStartBlockNumber(null);

        // last valid downloaded file's timestamp is set to file1's timestamp - (I/2 + 1ns), so both file1 and file2
        // will be in the bucket [lastTimestamp + I/2, lastTimestamp + 3*I/2). Note the interval I is set to twice of
        // the difference between file1 and file2.
        Duration interval = getCloseInterval().multipliedBy(2);
        Instant lastFileInstant = file1Instant.minus(interval.dividedBy(2).plusNanos(1));
        expectLastStreamFile(lastFileInstant);

        fileCopier.copy();
        downloader.download();

        verifyForSuccess();
        assertThat(downloaderProperties.getStreamPath()).isEmptyDirectory();
    }

    @Test
    @DisplayName("startDate not set, default to now, no files should be downloaded")
    void startDateDefaultNow() {
        expectLastStreamFile(Instant.now());
        fileCopier.copy();
        downloader.download();
        verifyForSuccess(List.of());
    }

    @ParameterizedTest(name = "startDate set to {0}s after {1}")
    @CsvSource({"-1,file1", "0,file1", "1,file1", "0,file2", "1,file2"})
    void startDate(long seconds, String fileChoice) {
        Instant startDate = chooseFileInstant(fileChoice).plusSeconds(seconds);
        expectLastStreamFile(null, 100L, startDate);
        List<String> expectedFiles = instantFilenamePairs.stream()
                .filter(pair -> pair.getLeft().isAfter(startDate))
                .map(Pair::getRight)
                .collect(Collectors.toList());

        fileCopier.copy();
        downloader.download();
        verifyForSuccess(expectedFiles);
    }

    @ParameterizedTest(name = "endDate set to {0}s after {1}")
    @CsvSource({
        "-1, file1",
        "0, file1",
        "1, file1",
        "0, file2",
        "1, file2",
    })
    void endDate(long seconds, String fileChoice) {
        mirrorProperties.setEndDate(chooseFileInstant(fileChoice).plusSeconds(seconds));
        mirrorProperties.setStartBlockNumber(null);
        commonDownloaderProperties.setBatchSize(1);
        List<String> expectedFiles = instantFilenamePairs.stream()
                .filter(pair -> !pair.getLeft().isAfter(mirrorProperties.getEndDate()))
                .map(Pair::getRight)
                .collect(Collectors.toList());
        expectLastStreamFile(Instant.EPOCH);

        fileCopier.copy();

        downloader.download();
        downloader.download();

        verifyForSuccess(expectedFiles, expectedFiles.size() == 2);
    }

    @Test
    void singleNodeSigFileCorrupted() throws Exception {
        corruptedNodeAccountId = nodes.iterator().next().getNodeAccountId();
        mirrorProperties.setStartBlockNumber(null);
        fileCopier.copy();
        Files.walk(s3Path)
                .filter(this::isSigFile)
                .filter(p -> p.toString().contains(corruptedNodeAccountId.toString()))
                .forEach(AbstractDownloaderTest::corruptFile);
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();
        verifyForSuccess();
    }

    @Test
    void singleNodeStreamFileCorrupted() throws Exception {
        corruptedNodeAccountId = nodes.iterator().next().getNodeAccountId();
        mirrorProperties.setStartBlockNumber(null);
        fileCopier.copy();
        Files.walk(s3Path)
                .filter(Predicate.not(this::isSigFile))
                .filter(p -> p.toString().contains(corruptedNodeAccountId.toString()))
                .forEach(AbstractDownloaderTest::corruptFile);
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();
        verifyForSuccess();
    }

    @Test
    @DisplayName("Max download items reached")
    void maxDownloadItemsReached() {
        commonDownloaderProperties.setBatchSize(1);
        mirrorProperties.setStartBlockNumber(null);
        fileCopier.copy();
        expectLastStreamFile(Instant.EPOCH);

        downloader.download();

        verifyStreamFiles(List.of(file1));
    }

    @Test
    void noDataFiles() throws IOException {
        fileCopier.copy();
        Files.walk(s3Path)
                .filter(Files::isRegularFile)
                .filter(Predicate.not(this::isSigFile))
                .map(Path::toFile)
                .forEach(FileUtils::deleteQuietly);
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();

        verifyForSuccess(Collections.emptyList(), true);
    }

    @Test
    void noSigFiles() throws IOException {
        fileCopier.copy();
        Files.walk(s3Path)
                .filter(Files::isRegularFile)
                .filter(this::isSigFile)
                .map(Path::toFile)
                .forEach(FileUtils::deleteQuietly);
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();

        verifyForSuccess(Collections.emptyList(), true);
    }

    @Test
    void persistBytes() {
        downloaderProperties.setPersistBytes(true);
        mirrorProperties.setStartBlockNumber(null);
        fileCopier.copy();
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();

        verifyForSuccess();
        assertThat(downloaderProperties.getStreamPath()).isEmptyDirectory();
    }

    @SneakyThrows
    private void differentFilenames(Duration offset) {
        mirrorProperties.setStartBlockNumber(null);

        // Copy all files and modify only node 0.0.3's files to have a different timestamp
        fileCopier.filterFiles(getStreamFilenameInstantString(file2) + "*").copy();
        Path basePath = fileCopier.getTo().resolve(streamType.getNodePrefix() + "0.0.3");

        // Construct a new filename with the offset added to the last valid file
        long nanoOffset = getCloseInterval().plus(offset).toNanos();
        Instant instant = file1Instant.plusNanos(nanoOffset);

        // Rename the good files to have a bad timestamp
        String data = StreamFilename.getFilename(streamType, DATA, instant);
        String signature = StreamFilename.getFilename(streamType, SIGNATURE, instant);
        Files.move(basePath.resolve(file2), basePath.resolve(data));
        Files.move(basePath.resolve(getSigFilename(file2)), basePath.resolve(signature));

        RecordFile recordFile = new RecordFile();
        recordFile.setName(file1);
        expectLastStreamFile(Instant.EPOCH);

        downloader.download();

        verifyStreamFiles(List.of(file2));
    }

    private String getSigFilename(String dataFilename) {
        var streamFilename = StreamFilename.from(dataFilename);
        var dataExtension = streamFilename.getExtension().getName();
        // take into account that data files may be compressed so the filename has an additional compression suffix,
        // while signature files won't be compressed.
        return dataFilename.replaceAll(dataExtension + ".*$", dataExtension + "_sig");
    }

    protected String getStreamFilenameInstantString(String filename) {
        var matcher = STREAM_FILENAME_INSTANT_PATTERN.matcher(filename);
        if (matcher.find()) {
            return matcher.group();
        }

        throw new IllegalArgumentException("Invalid stream filename " + filename);
    }

    protected void verifyUnsuccessful() {
        verifyStreamFiles(Collections.emptyList());
    }

    protected void verifyForSuccess() {
        verifyForSuccess(List.of(file1, file2));
    }

    protected void verifyForSuccess(List<String> files) {
        verifyForSuccess(files, true);
    }

    private void verifyForSuccess(List<String> files, boolean expectEnabled) {
        verifyStreamFiles(files, s -> {});
        assertThat(downloaderProperties.isEnabled()).isEqualTo(expectEnabled);
    }

    protected void verifyStreamFiles(List<String> files) {
        verifyStreamFiles(files, s -> {});
    }

    @SuppressWarnings({"java:S6103"})
    protected void verifyStreamFiles(List<String> files, Consumer<T> extraAssert) {
        var expectedFileIndexMap = getExpectedFileIndexMap();
        var index = new AtomicLong(firstIndex);

        verify(streamFileNotifier, times(files.size())).verified(streamFileCaptor.capture());
        assertThat(streamFileCaptor.getAllValues())
                .allMatch(s -> files.contains(s.getName()))
                .allMatch(s -> {
                    var expected = expectedFileIndexMap.get(s.getName());
                    if (expected != null) {
                        return Objects.equals(s.getIndex(), expected);
                    } else {
                        return s.getIndex() == null || s.getIndex() == index.getAndIncrement();
                    }
                })
                .allMatch(s -> downloaderProperties.isPersistBytes() ^ (s.getBytes() == null))
                .allSatisfy(t -> extraAssert.accept(t));

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

    private Instant chooseFileInstant(String choice) {
        switch (choice) {
            case "file1":
                return file1Instant;
            case "file2":
                return file2Instant;
            default:
                throw new RuntimeException("Invalid choice " + choice);
        }
    }

    protected void setTestFilesAndInstants(List<String> files) {
        file1 = files.get(0);
        file2 = files.get(1);

        file1Instant = StreamFilename.from(file1).getInstant();
        file2Instant = StreamFilename.from(file2).getInstant();
        instantFilenamePairs = List.of(Pair.of(file1Instant, file1), Pair.of(file2Instant, file2));
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
