package com.hedera.mirror.downloader;

import com.hedera.FileCopier;
import com.hedera.mirror.MirrorProperties;
import com.hedera.mirror.addressbook.NetworkAddressBook;
import com.hedera.mirror.config.MirrorNodeConfiguration;
import com.hedera.mirror.domain.HederaNetwork;
import com.hedera.mirror.repository.ApplicationStatusRepository;

import com.hedera.utilities.Utility;

import io.findify.s3mock.S3Mock;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.mockito.Mock;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class DownloaderTestingBase {
    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    protected ApplicationStatusRepository applicationStatusRepository;

    @TempDir
    Path dataPath;

    @TempDir
    protected Path s3Path;

    protected S3Mock s3;
    protected FileCopier fileCopier;
    protected CommonDownloaderProperties commonDownloaderProperties;
    protected MirrorProperties mirrorProperties;
    protected NetworkAddressBook networkAddressBook;
    protected S3AsyncClient s3AsyncClient;
    protected DownloaderProperties downloaderProperties;
    protected Downloader downloader;
    protected Path validPath;

    // Implementation can assume that mirrorProperties and commonDownloaderProperties have been initialized.
    protected abstract DownloaderProperties getDownloaderProperties();
    // Implementations can assume that s3AsyncClient, applicationStatusRepository, networkAddressBook and
    // downloaderProperties have been initialized.
    protected abstract Downloader getDownloader();
    protected abstract boolean isSigFile(String file);
    protected abstract boolean isDataFile(String file);

    protected void beforeEach(TestInfo testInfo, String testDataDir) {
        System.out.println("Before test: " + testInfo.getTestMethod().get().getName());

        initProperties();
        s3AsyncClient = (new MirrorNodeConfiguration()).s3AsyncClient(commonDownloaderProperties);
        networkAddressBook = new NetworkAddressBook(mirrorProperties);
        downloader = getDownloader();

        fileCopier = FileCopier.create(Utility.getResource("data").toPath(), s3Path)
                .from(testDataDir)
                .to(commonDownloaderProperties.getBucketName(), downloaderProperties.getStreamType().getPath());

        validPath = downloaderProperties.getValidPath();

        s3 = S3Mock.create(8001, s3Path.toString());
        s3.start();
    }

    private void initProperties() {
        mirrorProperties = new MirrorProperties();
        mirrorProperties.setDataPath(dataPath);
        mirrorProperties.setNetwork(HederaNetwork.TESTNET);

        commonDownloaderProperties = new CommonDownloaderProperties();
        commonDownloaderProperties.setBucketName("test");
        commonDownloaderProperties.setCloudProvider(CommonDownloaderProperties.CloudProvider.LOCAL);
        commonDownloaderProperties.setAccessKey("x"); // https://github.com/findify/s3mock/issues/147
        commonDownloaderProperties.setSecretKey("x");

        downloaderProperties = getDownloaderProperties();
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
                .allMatch(p -> isDataFile(p.toString()))
                .extracting(p -> p.getFileName().toString())
                .containsAll(filenames);
    }

    @Test
    @DisplayName("Missing address book")
    void testMissingAddressBook() throws Exception {
        Files.delete(mirrorProperties.getAddressBookPath());
        fileCopier.copy();
        downloader.download();
        assertNoFilesinValidPath();
    }

    protected void testMaxDownloadItemsReached(String filename) throws Exception {
        fileCopier.copy();
        downloader.download();
        assertValidFiles(List.of(filename));
    }

    @Test
    @DisplayName("Missing signatures")
    void missingSignatures() throws Exception {
        fileCopier.filterFiles(file -> isDataFile(file.getName())).copy();  // only copy data files
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
    @DisplayName("Less than 2/3 signatures")
    void lessThanTwoThirdSignatures() throws Exception {
        fileCopier.filterDirectories("*0.0.3").filterDirectories("*0.0.4").copy();
        downloader.download();
        assertNoFilesinValidPath();
    }

    @Test
    @DisplayName("Signature doesn't match file")
    void signatureMismatch() throws Exception {
        fileCopier.copy();
        Files.walk(s3Path).filter(p -> isSigFile(p.toString())).forEach(DownloaderTestingBase::corruptFile);
        downloader.download();
        assertNoFilesinValidPath();
    }

    @Test
    @DisplayName("Invalid or incomplete file")
    void invalidBalanceFile() throws Exception {
        fileCopier.copy();
        Files.walk(s3Path).filter(p -> isDataFile(p.toString())).forEach(DownloaderTestingBase::corruptFile);
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
}
