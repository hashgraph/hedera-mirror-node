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

package com.hedera.mirror.importer.downloader.balance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.downloader.AbstractDownloaderTest;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.downloader.provider.S3StreamFileProvider;
import com.hedera.mirror.importer.parser.balance.BalanceParserProperties;
import com.hedera.mirror.importer.reader.balance.BalanceFileReader;
import com.hedera.mirror.importer.reader.balance.BalanceFileReaderImplV1;
import com.hedera.mirror.importer.reader.balance.ProtoBalanceFileReader;
import com.hedera.mirror.importer.reader.balance.line.AccountBalanceLineParserV1;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountBalancesDownloaderTest extends AbstractDownloaderTest<AccountBalanceFile> {

    @Override
    protected DownloaderProperties getDownloaderProperties() {
        return new BalanceDownloaderProperties(mirrorProperties, commonDownloaderProperties);
    }

    @Override
    protected Downloader<AccountBalanceFile, AccountBalance> getDownloader() {
        BalanceFileReader balanceFileReader = new BalanceFileReaderImplV1(
                new BalanceParserProperties(), new AccountBalanceLineParserV1(mirrorProperties));
        var streamFileProvider = new S3StreamFileProvider(commonDownloaderProperties, s3AsyncClient);
        return new AccountBalancesDownloader(
                consensusNodeService,
                (BalanceDownloaderProperties) downloaderProperties,
                meterRegistry,
                dateRangeProcessor,
                nodeSignatureVerifier,
                signatureFileReader,
                streamFileNotifier,
                streamFileProvider,
                balanceFileReader);
    }

    @Override
    protected Path getTestDataDir() {
        return Path.of("accountBalances", "v1");
    }

    @Override
    protected Duration getCloseInterval() {
        return Duration.ofMinutes(15L);
    }

    @Override
    @BeforeEach
    protected void beforeEach() {
        super.beforeEach();
        setTestFilesAndInstants(
                List.of("2019-08-30T18_15_00.016002001Z_Balances.csv", "2019-08-30T18_30_00.010147001Z_Balances.csv"));
    }

    @Test
    void downloadWithMixedStreamFileExtensions() {
        // for the mixed scenario, both .csv and .pb.gz files exist for the same timestamp; however, all .csv and
        // .csv_sig files are intentionally made empty so if two account balance files are processed, they must be
        // the .pb.gz files
        ProtoBalanceFileReader protoBalanceFileReader = new ProtoBalanceFileReader();
        var streamFileProvider = new S3StreamFileProvider(commonDownloaderProperties, s3AsyncClient);
        downloader = new AccountBalancesDownloader(
                consensusNodeService,
                (BalanceDownloaderProperties) downloaderProperties,
                meterRegistry,
                dateRangeProcessor,
                nodeSignatureVerifier,
                signatureFileReader,
                streamFileNotifier,
                streamFileProvider,
                protoBalanceFileReader);
        fileCopier = FileCopier.create(TestUtils.getResource("data").toPath(), s3Path)
                .from(Path.of("accountBalances", "mixed"))
                .to(commonDownloaderProperties.getBucketName(), streamType.getPath());
        setTestFilesAndInstants(
                List.of("2021-03-10T22_12_56.075092Z_Balances.pb.gz", "2021-03-10T22_27_56.236886Z_Balances.pb.gz"));
        fileCopier.copy();
        expectLastStreamFile(Instant.EPOCH);

        downloader.download();

        verifyForSuccess();
        assertThat(downloaderProperties.getStreamPath()).isEmptyDirectory();
    }
}
