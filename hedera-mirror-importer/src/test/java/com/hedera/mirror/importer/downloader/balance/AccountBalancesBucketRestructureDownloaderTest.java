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

package com.hedera.mirror.importer.downloader.balance;

import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.importer.downloader.AbstractBucketRestructureDownloaderTest;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.downloader.provider.S3StreamFileProvider;
import com.hedera.mirror.importer.reader.balance.ProtoBalanceFileReader;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountBalancesBucketRestructureDownloaderTest extends AbstractBucketRestructureDownloaderTest {

    @Override
    protected DownloaderProperties getDownloaderProperties() {
        return new BalanceDownloaderProperties(mirrorProperties, commonDownloaderProperties);
    }

    @Override
    protected Downloader<AccountBalanceFile, AccountBalance> getDownloader() {
        ProtoBalanceFileReader protoBalanceFileReader = new ProtoBalanceFileReader();
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
                protoBalanceFileReader);
    }

    @Override
    protected Path getTestDataDir() {
        return Path.of("accountBalances", "proto.accountId");
    }

    @Override
    protected Duration getCloseInterval() {
        return Duration.ofMinutes(15L);
    }

    @Override
    @BeforeEach
    protected void beforeEach() {
        super.beforeEach();
        setTestFilesAndInstants(List.of(
                "2023-04-06T21_45_00.000494Z_Balances.pb.gz",
                "2023-04-06T22_00_00.067585Z_Balances.pb.gz",
                "2023-04-06T22_15_00.134616Z_Balances.pb.gz",
                "2023-04-06T22_30_00.104554Z_Balances.pb.gz"));
    }

    @Test
    @DisplayName("Download and verify files from new bucket in Auto Mode")
    void downloadFilesFromNewPathAutoMode() {
        // Changing bucket Path
        commonDownloaderProperties.setPathType(CommonDownloaderProperties.PathType.AUTO);
        // Reducing the pathRefresh interval to realistically test the node_id based path
        commonDownloaderProperties.setPathRefreshInterval(Duration.ofMillis(100));
        fileCopier
                .from(getTestDataDir())
                .to(commonDownloaderProperties.getBucketName(), streamType.getPath())
                .copy();
        fileCopier
                .from(testnet)
                .to(commonDownloaderProperties.getBucketName(), testnet.toString())
                .copy();
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();
        verifyStreamFiles(List.of(file1, file2));
        expectLastStreamFile(file2Instant);
        downloader.download();
        verifyStreamFiles(List.of(file3, file4));
    }
}
