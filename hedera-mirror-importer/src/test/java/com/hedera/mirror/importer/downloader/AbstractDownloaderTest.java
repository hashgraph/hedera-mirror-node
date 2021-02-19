package com.hedera.mirror.importer.downloader;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.logging.LoggingMeterRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.gaul.s3proxy.S3Proxy;
import org.gaul.shaded.org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStoreContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cglib.core.ReflectUtils;
import org.springframework.util.ResourceUtils;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.config.MetricsExecutionInterceptor;
import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;
import com.hedera.mirror.importer.config.MirrorImporterConfiguration;
import com.hedera.mirror.importer.converter.InstantConverter;
import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.AddressBookEntry;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamFile;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.reader.signature.CompositeSignatureFileReader;
import com.hedera.mirror.importer.reader.signature.SignatureFileReader;
import com.hedera.mirror.importer.reader.signature.SignatureFileReaderV2;
import com.hedera.mirror.importer.reader.signature.SignatureFileReaderV5;
import com.hedera.mirror.importer.util.Utility;

@ExtendWith(MockitoExtension.class)
@Log4j2
public abstract class AbstractDownloaderTest {
    private static final int S3_PROXY_PORT = 8001;

    @Mock
    protected StreamFileNotifier streamFileNotifier;
    @Mock
    protected MirrorDateRangePropertiesProcessor dateRangeProcessor;
    @Mock(lenient = true)
    protected AddressBookService addressBookService;
    @TempDir
    protected Path s3Path;
    protected S3Proxy s3Proxy;
    protected FileCopier fileCopier;
    protected CommonDownloaderProperties commonDownloaderProperties;
    protected MirrorProperties mirrorProperties;
    protected S3AsyncClient s3AsyncClient;
    protected DownloaderProperties downloaderProperties;
    protected Downloader downloader;
    protected MeterRegistry meterRegistry = new LoggingMeterRegistry();
    protected String file1;
    protected String file2;
    protected Instant file1Instant;
    protected Instant file2Instant;
    protected EntityId corruptedNodeAccountId;
    protected NodeSignatureVerifier nodeSignatureVerifier;
    protected SignatureFileReader signatureFileReader;
    protected StreamType streamType;

    protected static Set<EntityId> allNodeAccountIds;
    protected static AddressBook addressBook;

    @TempDir
    Path dataPath;

    private static void corruptFile(Path p) {
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
                FileChannel outChan = new FileOutputStream(file, true).getChannel();
                if (outChan.size() <= 48) {
                    outChan.truncate(outChan.size() / 2);
                } else {
                    outChan.truncate(48);
                }
                outChan.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Implementation can assume that mirrorProperties and commonDownloaderProperties have been initialized.
    protected abstract DownloaderProperties getDownloaderProperties();

    protected abstract Downloader getDownloader();

    protected abstract Path getTestDataDir();

    protected abstract Duration getCloseInterval();

    boolean isSigFile(Path path) {
        return path.toString().endsWith("_sig");
    }

    @BeforeAll
    static void beforeAll() throws IOException {
        addressBook = loadAddressBook("testnet");
        allNodeAccountIds = addressBook.getNodeSet();
    }

    protected void beforeEach() throws Exception {
        initProperties();
        s3AsyncClient = new MirrorImporterConfiguration(
                mirrorProperties, commonDownloaderProperties, new MetricsExecutionInterceptor(meterRegistry),
                AnonymousCredentialsProvider.create())
                .s3CloudStorageClient();

        signatureFileReader = new CompositeSignatureFileReader(new SignatureFileReaderV2(),
                new SignatureFileReaderV5());
        nodeSignatureVerifier = new NodeSignatureVerifier(addressBookService);
        downloader = getDownloader();
        streamType = downloaderProperties.getStreamType();

        fileCopier = FileCopier.create(Utility.getResource("data").toPath(), s3Path)
                .from(getTestDataDir())
                .to(commonDownloaderProperties.getBucketName(), streamType.getPath());

        startS3Proxy();

        doReturn(addressBook).when(addressBookService).getCurrent();
    }

    @AfterEach
    void after() throws Exception {
        s3Proxy.stop();
    }

    private void initProperties() {
        mirrorProperties = new MirrorProperties();
        mirrorProperties.setDataPath(dataPath);
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.TESTNET);

        commonDownloaderProperties = new CommonDownloaderProperties(mirrorProperties);
        commonDownloaderProperties.setEndpointOverride("http://localhost:" + S3_PROXY_PORT);

        downloaderProperties = getDownloaderProperties();
    }

    private void startS3Proxy() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("jclouds.filesystem.basedir", s3Path.toAbsolutePath().toString());

        BlobStoreContext context = ContextBuilder
                .newBuilder("filesystem")
                .overrides(properties)
                .build(BlobStoreContext.class);

        s3Proxy = S3Proxy.builder()
                .blobStore(context.getBlobStore())
                .endpoint(URI.create("http://localhost:" + S3_PROXY_PORT))
                .ignoreUnknownHeaders(true)
                .build();
        s3Proxy.start();

        for (int i = 0; i < 500; i++) {
            if (s3Proxy.getState().equals(AbstractLifeCycle.STARTED)) {
                return;
            }

            Thread.sleep(1);
        }

        throw new RuntimeException("Timeout starting S3Proxy, state " + s3Proxy.getState());
    }

    @Test
    @DisplayName("Download and verify files")
    void download() throws Exception {
        fileCopier.copy();
        expectLastSignature(Instant.EPOCH);
        downloader.download();

        verifyForSuccess();
        assertThat(downloaderProperties.getSignaturesPath()).doesNotExist();
    }

    @Test
    @DisplayName("Non-unanimous consensus reached")
    void partialConsensus() throws Exception {
        fileCopier.filterDirectories("*0.0.3").filterDirectories("*0.0.4").filterDirectories("*0.0.5").copy();
        expectLastSignature(Instant.EPOCH);
        downloader.download();

        verifyForSuccess();
    }

    @Test
    @DisplayName("Exactly 1/3 consensus")
    void oneThirdConsensus() throws Exception {
        List<AddressBookEntry> entries = addressBook.getEntries().stream().limit(3).collect(Collectors.toList());
        AddressBook addressBookWith3Nodes = addressBook.toBuilder().entries(entries).nodeCount(entries.size()).build();
        doReturn(addressBookWith3Nodes).when(addressBookService).getCurrent();

        String nodeAccountId = entries.get(0).getNodeAccountIdString();
        log.info("Only copy node {}'s stream files and signature files for a 3-node network", nodeAccountId);
        fileCopier.filterDirectories("*" + nodeAccountId).copy();
        expectLastSignature(Instant.EPOCH);
        downloader.download();

        verifyForSuccess();
    }

    @Test
    @DisplayName("Missing signatures")
    void missingSignatures() throws Exception {
        fileCopier.filterFiles(file -> !isSigFile(file.toPath())).copy();  // only copy data files
        expectLastSignature(Instant.EPOCH);
        downloader.download();
        verifyUnsuccessful();
    }

    @Test
    @DisplayName("Missing data files")
    void missingDataFiles() throws Exception {
        fileCopier.filterFiles("*_sig").copy();
        expectLastSignature(Instant.EPOCH);
        downloader.download();
        verifyUnsuccessful();
    }

    @Test
    @DisplayName("Less than 1/3 signatures")
    void lessThanOneThirdSignatures() throws Exception {
        fileCopier.filterDirectories("*0.0.3").copy();
        expectLastSignature(Instant.EPOCH);
        downloader.download();
        verifyUnsuccessful();
    }

    @Test
    @DisplayName("Signature doesn't match file")
    void signatureMismatch() throws Exception {
        fileCopier.copy();
        Files.walk(s3Path).filter(this::isSigFile).forEach(AbstractDownloaderTest::corruptFile);
        expectLastSignature(Instant.EPOCH);
        downloader.download();
        verifyUnsuccessful();
    }

    @Test
    @DisplayName("Invalid or incomplete file with garbage data appended")
    void invalidFileWithGarbageAppended() throws Exception {
        fileCopier.copy();
        Files.walk(s3Path).filter(file -> !isSigFile(file)).forEach(AbstractDownloaderTest::corruptFile);
        expectLastSignature(Instant.EPOCH);
        downloader.download();
        verifyUnsuccessful();
    }

    @Test
    @DisplayName("Invalid or incomplete file with data truncated")
    void invalidFileWithDataTruncated() throws Exception {
        fileCopier.copy();
        Files.walk(s3Path).filter(file -> !isSigFile(file)).forEach(AbstractDownloaderTest::truncateFile);
        expectLastSignature(Instant.EPOCH);
        downloader.download();
        verifyUnsuccessful();
    }

    @Test
    @DisplayName("Keep signature files")
    void keepSignatureFiles() throws Exception {
        downloaderProperties.setKeepSignatures(true);
        fileCopier.copy();
        expectLastSignature(Instant.EPOCH);
        downloader.download();
        assertThat(Files.walk(downloaderProperties.getSignaturesPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSizeGreaterThan(0)
                .allMatch(p -> isSigFile(p));
    }

    @Test
    @DisplayName("overwrite on download")
    void overwriteOnDownload() throws Exception {
        downloaderProperties.setKeepSignatures(true);
        fileCopier.copy();
        expectLastSignature(Instant.EPOCH);
        downloader.download();
        verifyForSuccess();

        Mockito.reset(dateRangeProcessor);
        // Corrupt the downloaded signatures to test that they get overwritten by good ones on re-download.
        Files.walk(downloaderProperties.getSignaturesPath())
                .filter(this::isSigFile)
                .forEach(AbstractDownloaderTest::corruptFile);

        downloader.download();
        verifyForSuccess();
    }

    @Test
    @DisplayName("Different filenames, same interval")
    void differentFilenamesSameInterval() throws Exception {
        differentFilenames(Duration.ofNanos(1L));
    }

    @Test
    @DisplayName("Different filenames, same interval, lower bound")
    void differentFilenamesSameIntervalLower() throws Exception {
        differentFilenames(getCloseInterval().dividedBy(2L).negated());
    }

    @Test
    @DisplayName("Different filenames, same interval, upper bound")
    void differentFilenamesSameIntervalUpper() throws Exception {
        differentFilenames(getCloseInterval().dividedBy(2L).minusNanos(1));
    }

    @Test
    @DisplayName("Different filenames, previous interval")
    void differentFilenamesPreviousInterval() throws Exception {
        differentFilenames(getCloseInterval().dividedBy(2L).negated().minusNanos(2));
    }

    @Test
    @DisplayName("Different filenames, next interval")
    void differentFilenamesNextInterval() throws Exception {
        differentFilenames(getCloseInterval().dividedBy(2L));
    }

    @Test
    @DisplayName("Download and verify two group of files in the same bucket")
    void downloadValidFilesInSameBucket() throws Exception {
        // last valid downloaded file's timestamp is set to file1's timestamp - (I/2 + 1ns), so both file1 and file2
        // will be in the bucket [lastTimestamp + I/2, lastTimestamp + 3*I/2). Note the interval I is set to twice of
        // the difference between file1 and file2.
        Duration interval = getCloseInterval().multipliedBy(2);
        Instant lastFileInstant = file1Instant.minus(interval.dividedBy(2).plusNanos(1));
        expectLastSignature(lastFileInstant);

        fileCopier.copy();
        downloader.download();

        verifyForSuccess();
        assertThat(downloaderProperties.getSignaturesPath()).doesNotExist();
    }

    @Test
    @DisplayName("startDate not set, default to now, no files should be downloaded")
    void startDateDefaultNow() throws Exception {
        expectLastSignature(Instant.now());
        fileCopier.copy();
        downloader.download();
        verifyForSuccess(List.of());
    }

    @ParameterizedTest(name = "startDate set to {0}s after {1}")
    @CsvSource({
            "-1,file1",
            "0,file1",
            "1,file1",
            "0,file2",
            "1,file2"
    })
    void startDate(long seconds, String fileChoice) throws Exception {
        Instant startDate = chooseFileInstant(fileChoice).plusSeconds(seconds);
        expectLastSignature(startDate);
        List<String> expectedFiles = List.of(file1, file2)
                .stream()
                .filter(name -> Utility.getInstantFromFilename(name).isAfter(startDate))
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
    void endDate(long seconds, String fileChoice) throws Exception {
        mirrorProperties.setEndDate(chooseFileInstant(fileChoice).plusSeconds(seconds));
        downloaderProperties.setBatchSize(1);
        List<String> expectedFiles = List.of(file1, file2)
                .stream()
                .filter(name -> !Utility.getInstantFromFilename(name).isAfter(mirrorProperties.getEndDate()))
                .collect(Collectors.toList());
        expectLastSignature(Instant.EPOCH);

        fileCopier.copy();

        downloader.download();
        downloader.download();

        verifyForSuccess(expectedFiles, expectedFiles.size() == 2);
    }

    @ParameterizedTest(name = "node {0} signature file is corrupted")
    @MethodSource("provideAllNodeAccountIds")
    void singleNodeSigFileCorrupted(EntityId nodeAccountId) throws Exception {
        corruptedNodeAccountId = nodeAccountId;
        fileCopier.copy();
        Files.walk(s3Path).filter(this::isSigFile)
                .filter(p -> p.toString().contains(nodeAccountId.entityIdToString()))
                .forEach(AbstractDownloaderTest::corruptFile);
        expectLastSignature(Instant.EPOCH);
        downloader.download();
        verifyForSuccess();
    }

    @ParameterizedTest(name = "node {0} stream file is corrupted")
    @MethodSource("provideAllNodeAccountIds")
    void singleNodeStreamFileCorrupted(EntityId nodeAccountId) throws Exception {
        corruptedNodeAccountId = nodeAccountId;
        fileCopier.copy();
        Files.walk(s3Path).filter(Predicate.not(this::isSigFile))
                .filter(p -> p.toString().contains(nodeAccountId.entityIdToString()))
                .forEach(AbstractDownloaderTest::corruptFile);
        expectLastSignature(Instant.EPOCH);
        downloader.download();
        verifyForSuccess();
    }

    @Test
    @DisplayName("Max download items reached")
    void maxDownloadItemsReached() throws Exception {
        downloaderProperties.setBatchSize(1);
        fileCopier.copy();
        expectLastSignature(Instant.EPOCH);

        downloader.download();

        verifyStreamFiles(List.of(file1));
    }

    @ParameterizedTest(name = "verifyHashChain {5}")
    @CsvSource({
            // @formatter:off
            "'', '', 1970-01-01T00:00:00Z,        2000-01-01T10_00_00.000000Z.stream, true,  passes if both hashes are empty",
            "xx, '', 1970-01-01T00:00:00Z,        2000-01-01T10_00_00.000000Z.stream, true,  passes if hash mismatch and expected hash is empty", // starting stream in middle
            "'', xx, 1970-01-01T00:00:00Z,        2000-01-01T10_00_00.000000Z.stream, false, fails if hash mismatch and actual hash is empty", // bad db state
            "xx, yy, 1970-01-01T00:00:00Z,        2000-01-01T10_00_00.000000Z.stream, false, fails if hash mismatch and hashes are non-empty",
            "xx, yy, 2000-01-01T10:00:00.000001Z, 2000-01-01T10_00_00.000000Z.stream, true,  passes if hash mismatch but verifyHashAfter is after filename",
            "xx, yy, 2000-01-01T10:00:00.000001Z, 2000-01-01T10_00_00.000000Z.stream, true,  passes if hash mismatch but verifyHashAfter is same as filename",
            "xx, yy, 2000-01-01T09:59:59.999999Z, 2000-01-01T10_00_00.000000Z.stream, false, fails if hash mismatch and verifyHashAfter is before filename",
            "xx, xx, 1970-01-01T00:00:00Z,        2000-01-01T10_00_00.000000Z.stream, true,  passes if hashes are equal"
            // @formatter:on
    })
    void testVerifyHashChain(String actualPrevFileHash, String expectedPrevFileHash,
                             @ConvertWith(InstantConverter.class) Instant verifyHashAfter, String fileName,
                             Boolean expectedResult, String testName) {
        downloaderProperties.getMirrorProperties().setVerifyHashAfter(verifyHashAfter);
        RecordFile streamFile = new RecordFile();
        streamFile.setName(fileName);
        streamFile.setPreviousHash(actualPrevFileHash);
        assertThat(downloader.verifyHashChain(streamFile, expectedPrevFileHash))
                .as(testName)
                .isEqualTo(expectedResult);
    }

    private void differentFilenames(Duration offset) throws Exception {
        // Copy all files and modify only node 0.0.3's files to have a different timestamp
        fileCopier.filterFiles(file2 + "*").copy();
        Path basePath = fileCopier.getTo().resolve(streamType.getNodePrefix() + "0.0.3");

        // Construct a new filename with the offset added to the last valid file
        long nanoOffset = getCloseInterval().plus(offset).toNanos();
        long timestamp = Utility.getTimestampFromFilename(file1) + nanoOffset;
        String baseFilename = Instant.ofEpochSecond(0, timestamp) + streamType.getSuffix() + ".";
        baseFilename = baseFilename.replace(':', '_');

        // Rename the good files to have a bad timestamp
        String signature = baseFilename + streamType.getSignatureExtension();
        String signed = baseFilename + streamType.getExtension();
        Files.move(basePath.resolve(file2 + "_sig"), basePath.resolve(signature));
        Files.move(basePath.resolve(file2), basePath.resolve(signed));

        RecordFile recordFile = new RecordFile();
        recordFile.setName(file1);
        expectLastSignature(Instant.EPOCH);

        downloader.download();

        verifyStreamFiles(List.of(file2));
    }

    protected void verifyUnsuccessful() {
        verifyStreamFiles(Collections.emptyList());
    }

    private void verifyForSuccess() throws Exception {
        verifyForSuccess(List.of(file1, file2));
    }

    private void verifyForSuccess(List<String> files) throws Exception {
        verifyForSuccess(files, true);
    }

    private void verifyForSuccess(List<String> files, boolean expectEnabled) {
        verifyStreamFiles(files);
        assertThat(downloaderProperties.isEnabled()).isEqualTo(expectEnabled);
    }

    protected void verifyStreamFiles(List<String> files) {
        verify(streamFileNotifier, times(files.size()))
                .verified(ArgumentMatchers.argThat(s -> files.contains(s.getName())));
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
        this.file1 = files.get(0);
        this.file2 = files.get(1);

        file1Instant = Utility.getInstantFromFilename(file1);
        file2Instant = Utility.getInstantFromFilename(file2);
    }

    protected void expectLastSignature(Instant instant) {
        StreamFile streamFile = (StreamFile) ReflectUtils.newInstance(streamType.getStreamFileClass());
        streamFile.setName(Utility.getStreamFilenameFromInstant(streamType, instant));
        doReturn(Optional.of(streamFile)).when(dateRangeProcessor).getLastStreamFile(streamType);
    }

    protected static AddressBook loadAddressBook(String filename) throws IOException {
        Path addressBookPath = ResourceUtils.getFile(String.format("classpath:addressbook/%s", filename)).toPath();
        byte[] addressBookBytes = Files.readAllBytes(addressBookPath);
        EntityId entityId = EntityId.of(0, 0, 102, EntityTypeEnum.FILE);
        long now = Instant.now().getEpochSecond();
        return addressBookFromBytes(addressBookBytes, now, entityId);
    }

    protected static AddressBook addressBookFromBytes(byte[] contents, long consensusTimestamp, EntityId entityId)
            throws InvalidProtocolBufferException {
        AddressBook.AddressBookBuilder addressBookBuilder = AddressBook.builder()
                .fileData(contents)
                .startConsensusTimestamp(consensusTimestamp + 1)
                .fileId(entityId);

        NodeAddressBook nodeAddressBook = NodeAddressBook.parseFrom(contents);
        List<AddressBookEntry> addressBookEntries = new ArrayList<>();
        addressBookBuilder.nodeCount(nodeAddressBook.getNodeAddressCount());
        for (com.hederahashgraph.api.proto.java.NodeAddress nodeAddressProto : nodeAddressBook
                .getNodeAddressList()) {
            AddressBookEntry addressBookEntry = AddressBookEntry.builder()
                    .consensusTimestamp(consensusTimestamp)
                    .memo(nodeAddressProto.getMemo().toStringUtf8())
                    .ip(nodeAddressProto.getIpAddress().toStringUtf8())
                    .port(nodeAddressProto.getPortno())
                    .publicKey(nodeAddressProto.getRSAPubKey())
                    .nodeCertHash(nodeAddressProto.getNodeCertHash().toByteArray())
                    .nodeId(nodeAddressProto.getNodeId())
                    .nodeAccountId(EntityId.of(nodeAddressProto.getNodeAccountId()))
                    .build();
            addressBookEntries.add(addressBookEntry);
        }

        addressBookBuilder.entries(addressBookEntries);

        return addressBookBuilder.build();
    }

    private static Stream<Arguments> provideAllNodeAccountIds() {
        return allNodeAccountIds.stream().map(Arguments::of);
    }
}
