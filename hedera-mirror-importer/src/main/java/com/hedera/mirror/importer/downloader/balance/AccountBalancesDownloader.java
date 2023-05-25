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

import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.importer.addressbook.ConsensusNodeService;
import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.downloader.NodeSignatureVerifier;
import com.hedera.mirror.importer.downloader.StreamFileNotifier;
import com.hedera.mirror.importer.downloader.provider.StreamFileProvider;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.reader.balance.BalanceFileReader;
import com.hedera.mirror.importer.reader.signature.SignatureFileReader;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;

@Log4j2
@Named
public class AccountBalancesDownloader extends Downloader<AccountBalanceFile, AccountBalance> {

    @SuppressWarnings("java:S107")
    public AccountBalancesDownloader(
            ConsensusNodeService consensusNodeService,
            BalanceDownloaderProperties downloaderProperties,
            MeterRegistry meterRegistry,
            MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor,
            NodeSignatureVerifier nodeSignatureVerifier,
            SignatureFileReader signatureFileReader,
            StreamFileNotifier streamFileNotifier,
            StreamFileProvider streamFileProvider,
            BalanceFileReader streamFileReader) {
        super(
                consensusNodeService,
                downloaderProperties,
                meterRegistry,
                mirrorDateRangePropertiesProcessor,
                nodeSignatureVerifier,
                signatureFileReader,
                streamFileNotifier,
                streamFileProvider,
                streamFileReader);
    }

    @Override
    @Leader
    @Scheduled(fixedDelayString = "#{@balanceDownloaderProperties.getFrequency().toMillis()}")
    public void download() {
        downloadNextBatch();
    }
}
