package com.hedera.mirror.importer.downloader.balance;

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
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.downloader.AbstractDownloaderTest;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.parser.balance.BalanceParserProperties;
import com.hedera.mirror.importer.parser.record.entity.FlywayMigrationsCompleteEvent;
import com.hedera.mirror.importer.reader.balance.BalanceFileReader;
import com.hedera.mirror.importer.reader.balance.BalanceFileReaderImplV1;
import com.hedera.mirror.importer.reader.balance.ProtoBalanceFileReader;
import com.hedera.mirror.importer.reader.balance.line.AccountBalanceLineParserV1;

class AccountBalancesDownloaderTest extends AbstractDownloaderTest {

    @Override
    protected DownloaderProperties getDownloaderProperties() {
        return new BalanceDownloaderProperties(mirrorProperties, commonDownloaderProperties);
    }

    @Override
    protected Downloader getDownloader() {
        BalanceFileReader balanceFileReader = new BalanceFileReaderImplV1(new BalanceParserProperties(mirrorProperties),
                new AccountBalanceLineParserV1(mirrorProperties));
        return new AccountBalancesDownloader(s3AsyncClient, addressBookService,
                (BalanceDownloaderProperties) downloaderProperties, meterRegistry, nodeSignatureVerifier,
                signatureFileReader, balanceFileReader, streamFileNotifier, dateRangeProcessor);
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
    protected void beforeEach() throws Exception {
        super.beforeEach();
        setTestFilesAndInstants(List.of(
                "2019-08-30T18_15_00.016002001Z_Balances.csv",
                "2019-08-30T18_30_00.010147001Z_Balances.csv"
        ));
    }

    @Test
    void downloadWithMixedStreamFileExtensions() throws Exception {
        // for the mixed scenario, both .csv and .pb.gz files exist for the same timestamp; however, all .csv and
        // .csv_sig files are intentionally made empty so if two account balance files are processed, they must be
        // the .pb.gz files
        ProtoBalanceFileReader protoBalanceFileReader = new ProtoBalanceFileReader();
        downloader = new AccountBalancesDownloader(s3AsyncClient, addressBookService,
                (BalanceDownloaderProperties) downloaderProperties, meterRegistry, nodeSignatureVerifier,
                signatureFileReader, protoBalanceFileReader, streamFileNotifier, dateRangeProcessor);
        downloader.startDownloader(mock(FlywayMigrationsCompleteEvent.class));
        fileCopier = FileCopier.create(TestUtils.getResource("data").toPath(), s3Path)
                .from(Path.of("accountBalances", "mixed"))
                .to(commonDownloaderProperties.getBucketName(), streamType.getPath());
        setTestFilesAndInstants(List.of(
                "2021-03-10T22_12_56.075092Z_Balances.pb.gz",
                "2021-03-10T22_27_56.236886Z_Balances.pb.gz"
        ));
        fileCopier.copy();
        expectLastStreamFile(Instant.EPOCH);

        downloader.download();

        verifyForSuccess();
        assertThat(downloaderProperties.getSignaturesPath()).doesNotExist();
    }
}
