package com.hedera.mirror.importer.downloader;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.primitives.Bytes;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import io.findify.s3mock.S3Mock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.logging.LoggingMeterRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.springframework.util.ResourceUtils;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.config.MetricsExecutionInterceptor;
import com.hedera.mirror.importer.config.MirrorImporterConfiguration;
import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.AddressBookEntry;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;
import com.hedera.mirror.importer.util.Utility;

public abstract class AbstractDownloaderTest {
    private static final int S3_MOCK_PORT = 8001;

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    protected ApplicationStatusRepository applicationStatusRepository;
    @Mock(lenient = true)
    protected AddressBookService addressBookService;
    @TempDir
    protected Path s3Path;
    protected S3Mock s3;
    protected FileCopier fileCopier;
    protected CommonDownloaderProperties commonDownloaderProperties;
    protected MirrorProperties mirrorProperties;
    protected S3AsyncClient s3AsyncClient;
    protected DownloaderProperties downloaderProperties;
    protected Downloader downloader;
    protected Path validPath;
    protected MeterRegistry meterRegistry = new LoggingMeterRegistry();
    protected String file1;
    protected String file2;
    protected Instant file1Instant;
    protected Instant file2Instant;

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

    // Implementations can assume that s3AsyncClient, applicationStatusRepository, addressBookService and
    // downloaderProperties have been initialized.
    protected abstract Downloader getDownloader();

    protected abstract Path getTestDataDir();

    protected abstract Duration getCloseInterval();

    boolean isSigFile(Path path) {
        return path.toString().endsWith("_sig");
    }

    @BeforeEach
    void beforeEach(TestInfo testInfo) throws IOException {
        System.out.println("Before test: " + testInfo.getTestMethod().get().getName());

        initProperties();
        s3AsyncClient = new MirrorImporterConfiguration(
                mirrorProperties, commonDownloaderProperties, new MetricsExecutionInterceptor(meterRegistry))
                .s3CloudStorageClient();
        downloader = getDownloader();

        fileCopier = FileCopier.create(Utility.getResource("data").toPath(), s3Path)
                .from(getTestDataDir())
                .to(commonDownloaderProperties.getBucketName(), downloaderProperties.getStreamType().getPath());

        validPath = downloaderProperties.getValidPath();

        s3 = S3Mock.create(S3_MOCK_PORT, s3Path.toString());
        s3.start();

        MirrorProperties.HederaNetwork hederaNetwork = mirrorProperties.getNetwork();
        Path addressBookPath = ResourceUtils.getFile(String
                .format("classpath:addressbook/%s", hederaNetwork.name().toLowerCase())).toPath();
        byte[] addressBookBytes = Files.readAllBytes(addressBookPath);
        EntityId entityId = EntityId.of(0, 0, 102, EntityTypeEnum.FILE);
        long now = Instant.now().getEpochSecond();
        doReturn(addressBookFromBytes(addressBookBytes, now, entityId)).when(addressBookService).getCurrent();

        // the default is start pulling data from NOW, set it to EPOCH
        mirrorProperties.setStartDate(Instant.EPOCH);
    }

    @AfterEach
    void after() {
        s3.shutdown();
    }

    private void initProperties() {
        mirrorProperties = new MirrorProperties();
        mirrorProperties.setDataPath(dataPath);
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.TESTNET);

        commonDownloaderProperties = new CommonDownloaderProperties(mirrorProperties);
        commonDownloaderProperties.setEndpointOverride("http://localhost:" + S3_MOCK_PORT);
        commonDownloaderProperties.setAccessKey("x"); // https://github.com/findify/s3mock/issues/147
        commonDownloaderProperties.setSecretKey("x");

        downloaderProperties = getDownloaderProperties();
        downloaderProperties.init();
    }

    protected void assertNoFilesinValidPath() throws Exception {
        assertThat(Files.walk(validPath))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(0);
    }

    protected void assertValidFiles(List<String> filenames) throws Exception {
        assertThat(Files.walk(validPath))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(filenames.size())
                .allMatch(p -> !isSigFile(p))
                .extracting(p -> p.getFileName().toString())
                .containsAll(filenames);
    }

    protected void testMaxDownloadItemsReached(String filename) throws Exception {
        fileCopier.copy();
        downloader.download();
        assertValidFiles(List.of(filename));
    }

    @Test
    @DisplayName("Download and verify files")
    void download() throws Exception {
        fileCopier.copy();
        downloader.download();

        verifyForSuccess();
        assertThat(downloaderProperties.getSignaturesPath()).doesNotExist();
    }

    @Test
    @DisplayName("Non-unanimous consensus reached")
    void partialConsensus() throws Exception {
        fileCopier.filterDirectories("*0.0.3").filterDirectories("*0.0.4").filterDirectories("*0.0.5").copy();
        downloader.download();

        verifyForSuccess();
    }

    @Test
    @DisplayName("Exactly 1/3 consensus")
    void oneThirdConsensus() throws Exception {
        MirrorProperties.HederaNetwork hederaNetwork = mirrorProperties.getNetwork();
        Path addressBookPath = ResourceUtils.getFile(String
                .format("classpath:addressbook/%s", hederaNetwork.name().toLowerCase())).toPath();
        byte[] addressBookBytes = Files.readAllBytes(addressBookPath);
        int index = Bytes.lastIndexOf(addressBookBytes, (byte) '\n');
        addressBookBytes = Arrays.copyOfRange(addressBookBytes, 0, index);
        EntityId entityId = EntityId.of(0, 0, 102, EntityTypeEnum.FILE);
        long now = Instant.now().getEpochSecond();
        doReturn(addressBookFromBytes(addressBookBytes, now, entityId)).when(addressBookService).getCurrent();

        fileCopier.filterDirectories("*0.0.3").copy();
        getDownloader().download();

        verifyForSuccess();
    }

    @Test
    @DisplayName("Missing signatures")
    void missingSignatures() throws Exception {
        fileCopier.filterFiles(file -> !isSigFile(file.toPath())).copy();  // only copy data files
        downloader.download();
        assertNoFilesinValidPath();
    }

    @Test
    @DisplayName("Missing data files")
    void missingDataFiles() throws Exception {
        fileCopier.filterFiles("*_sig").copy();
        downloader.download();
        assertNoFilesinValidPath();
    }

    @Test
    @DisplayName("Less than 1/3 signatures")
    void lessThanOneThirdSignatures() throws Exception {
        fileCopier.filterDirectories("*0.0.3").copy();
        downloader.download();
        assertNoFilesinValidPath();
    }

    @Test
    @DisplayName("Signature doesn't match file")
    void signatureMismatch() throws Exception {
        fileCopier.copy();
        Files.walk(s3Path).filter(this::isSigFile).forEach(AbstractDownloaderTest::corruptFile);
        downloader.download();
        assertNoFilesinValidPath();
    }

    @Test
    @DisplayName("Invalid or incomplete file with garbage data appended")
    void invalidFileWithGarbageAppended() throws Exception {
        fileCopier.copy();
        Files.walk(s3Path).filter(file -> !isSigFile(file)).forEach(AbstractDownloaderTest::corruptFile);
        downloader.download();
        assertNoFilesinValidPath();
    }

    @Test
    @DisplayName("Invalid or incomplete file with data truncated")
    void invalidFileWithDataTruncated() throws Exception {
        fileCopier.copy();
        Files.walk(s3Path).filter(file -> !isSigFile(file)).forEach(AbstractDownloaderTest::truncateFile);
        downloader.download();
        assertNoFilesinValidPath();
    }

    @Test
    @DisplayName("Error moving record to valid folder")
    void errorMovingFile() {
        fileCopier.copy();
        validPath.toFile().delete();
        downloader.download();
        assertThat(validPath).doesNotExist();
    }

    @Test
    @DisplayName("Keep signature files")
    void keepSignatureFiles() throws Exception {
        downloaderProperties.setKeepSignatures(true);
        fileCopier.copy();
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
        downloader.download();
        verifyForSuccess();

        reset(applicationStatusRepository);
        // Corrupt the downloaded signatures to test that they get overwritten by good ones on re-download.
        Files.walk(downloaderProperties.getSignaturesPath())
                .filter(this::isSigFile)
                .forEach(AbstractDownloaderTest::corruptFile);
        doReturn("").when(applicationStatusRepository).findByStatusCode(downloader.getLastValidDownloadedFileKey());
        downloader.download();
        verifyForSuccess(null);
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
        String lastFileName = Utility.getStreamFilenameForInstant(downloaderProperties.getStreamType(), lastFileInstant);

        doReturn(lastFileName).when(applicationStatusRepository)
                .findByStatusCode(downloader.getLastValidDownloadedFileKey());

        fileCopier.copy();
        downloader.download();

        verifyForSuccess(null);
        assertThat(downloaderProperties.getSignaturesPath()).doesNotExist();
    }

    @Disabled("Expect no file downloaded, however both files will be downloaded because the findify s3mock does not " +
            "support marker with listObject: https://github.com/findify/s3mock/pull/171")
    @Test
    @DisplayName("startDate not set, default to now")
    void startDateDefaultNow() throws Exception {
        configMockApplicationStatusRepository();
        fileCopier.copy();
        downloader.download();
        verifyForSuccess(List.of(), MirrorProperties.getStartDateNow());
    }

    @ParameterizedTest(name = "startDate set to {0}ns after {1}")
    @CsvSource({
            "-1,file1",
            "0,file1",
            // "1,file1",
            // "1,file1",
            // "1,file2" // disable the 3 test cases because findify s3mock does not support marker with listObject
    })
    void startDate(long nanos, String fileChoice) throws Exception {
        final Instant startDate = chooseFileInstant(fileChoice).plusNanos(nanos);
        mirrorProperties.setStartDate(startDate);
        configMockApplicationStatusRepository();
        List<String> expectedFiles = List.of(file1, file2)
                .stream()
                .filter(name -> !startDate.isAfter(Utility.getInstantFromFilename(name)))
                .collect(Collectors.toList());

        fileCopier.copy();
        downloader.download();
        verifyForSuccess(expectedFiles, startDate);
    }

    @ParameterizedTest(name = "endDate set to {0}ns after {1}")
    @CsvSource({
            "-1, file1",
            "1, file1",
            "1, file2",
    })
    void endDate(long nanos, String fileChoice) throws Exception {
        mirrorProperties.setEndDate(chooseFileInstant(fileChoice).plusNanos(nanos));
        fileCopier.copy();
        downloader.download();
        verifyForSuccess();
    }

    private void differentFilenames(Duration offset) throws Exception {
        // Copy all files and modify only node 0.0.3's files to have a different timestamp
        StreamType type = downloaderProperties.getStreamType();
        fileCopier.filterFiles(file2 + "*").copy();
        Path basePath = fileCopier.getTo().resolve(type.getNodePrefix() + "0.0.3");

        // Construct a new filename with the offset added to the last valid file
        long nanoOffset = getCloseInterval().plus(offset).toNanos();
        long timestamp = Utility.getTimestampFromFilename(file1) + nanoOffset;
        String baseFilename = Instant.ofEpochSecond(0, timestamp).toString().replace(':', '_') + type.getSuffix() + ".";

        // Rename the good files to have a bad timestamp
        String signature = baseFilename + type.getSignatureExtension();
        String signed = baseFilename + type.getExtension();
        Files.move(basePath.resolve(file2 + "_sig"), basePath.resolve(signature));
        Files.move(basePath.resolve(file2), basePath.resolve(signed));

        doReturn(file1).when(applicationStatusRepository).findByStatusCode(downloader.getLastValidDownloadedFileKey());

        downloader.download();

        // The file with the different timestamp than all other nodes should not be processed
        verify(applicationStatusRepository).updateStatusValue(downloader.getLastValidDownloadedFileKey(), file2);
        assertValidFiles(List.of(file2));
    }

    private void verifyForSuccess() throws Exception {
        verifyForSuccess(List.of(file1, file2), Instant.EPOCH);
    }

    private void verifyForSuccess(Instant startDate) throws Exception {
        verifyForSuccess(List.of(file1, file2), startDate);
    }

    private void verifyForSuccess(List<String> files, Instant startDate) throws Exception {
        boolean startDateFilenameVerified = false;
        Instant lastDownloadedFileInstant = null;
        for (String filename : files) {
            if (Utility.getInstantFromFilename(filename).equals(startDate)) {
                verify(applicationStatusRepository, times(2)).updateStatusValue(downloader.getLastValidDownloadedFileKey(), filename);
                startDateFilenameVerified = true;
            } else {
                verify(applicationStatusRepository).updateStatusValue(downloader.getLastValidDownloadedFileKey(), filename);
            }
            lastDownloadedFileInstant = Utility.getInstantFromFilename(filename);
        }

        if (startDate != null && !startDateFilenameVerified) {
            verify(applicationStatusRepository)
                    .updateStatusValue(downloader.getLastValidDownloadedFileKey(), Utility.getStreamFilenameForInstant(downloaderProperties.getStreamType(), startDate));
        }

        if (downloader.getLastValidDownloadedFileHashKey() != null) {
            verify(applicationStatusRepository, times(files.size()))
                    .updateStatusValue(eq(downloader.getLastValidDownloadedFileHashKey()), any());
        }

        if (startDate != null) {
            assertThat(mirrorProperties.getVerifyHashAfter()).isEqualTo(startDate);
        }

        Instant endDate = mirrorProperties.getEndDate();
        if (lastDownloadedFileInstant != null && endDate != null && !lastDownloadedFileInstant.isBefore(endDate)) {
            assertThat(downloaderProperties.isEnabled()).isFalse();
        } else {
            assertThat(downloaderProperties.isEnabled()).isTrue();
        }

        assertValidFiles(files);
    }

    protected AddressBook addressBookFromBytes(byte[] contents, long consensusTimestamp, EntityId entityId) throws InvalidProtocolBufferException {
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

    private void configMockApplicationStatusRepository() {
        ApplicationStatus applicationStatus = new ApplicationStatus();
        doAnswer(invocation -> {
                    String value = invocation.getArgument(1);
                    applicationStatus.setStatus(value);
                    return null;
                }
        ).when(applicationStatusRepository).updateStatusValue(eq(downloader.getLastValidDownloadedFileKey()), any());
        doAnswer(invocation -> applicationStatus.getStatus()).when(applicationStatusRepository)
                .findByStatusCode(downloader.getLastValidDownloadedFileKey());
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

    protected void setTestFilesAndInstants(String file1, String file2) {
        this.file1 = file1;
        this.file2 = file2;

        file1Instant = Utility.getInstantFromFilename(file1);
        file2Instant = Utility.getInstantFromFilename(file2);
    }

    @Data
    private static class ApplicationStatus {
        String status = "";
    }
}
