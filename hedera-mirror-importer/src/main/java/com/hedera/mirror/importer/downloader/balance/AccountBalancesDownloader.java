/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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
import com.hedera.mirror.importer.ImporterProperties;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.addressbook.ConsensusNodeService;
import com.hedera.mirror.importer.config.DateRangeCalculator;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.downloader.NodeSignatureVerifier;
import com.hedera.mirror.importer.downloader.StreamFileNotifier;
import com.hedera.mirror.importer.downloader.provider.StreamFileProvider;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.reader.balance.BalanceFileReader;
import com.hedera.mirror.importer.reader.signature.SignatureFileReader;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.scheduling.annotation.Scheduled;

@Named
public class AccountBalancesDownloader extends Downloader<AccountBalanceFile, AccountBalance> {

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final AtomicBoolean accountBalanceFileExists = new AtomicBoolean(false);

    @SuppressWarnings("java:S107")
    public AccountBalancesDownloader(
            AccountBalanceFileRepository accountBalanceFileRepository,
            ConsensusNodeService consensusNodeService,
            BalanceDownloaderProperties downloaderProperties,
            ImporterProperties importerProperties,
            MeterRegistry meterRegistry,
            DateRangeCalculator dateRangeCalculator,
            NodeSignatureVerifier nodeSignatureVerifier,
            SignatureFileReader signatureFileReader,
            StreamFileNotifier streamFileNotifier,
            StreamFileProvider streamFileProvider,
            BalanceFileReader streamFileReader) {
        super(
                consensusNodeService,
                downloaderProperties,
                importerProperties,
                meterRegistry,
                dateRangeCalculator,
                nodeSignatureVerifier,
                signatureFileReader,
                streamFileNotifier,
                streamFileProvider,
                streamFileReader);
        this.accountBalanceFileRepository = accountBalanceFileRepository;
    }

    @Override
    @Leader
    @Scheduled(fixedDelayString = "#{@balanceDownloaderProperties.getFrequency().toMillis()}")
    public void download() {
        downloadNextBatch();
    }

    @Override
    protected void onVerified(StreamFileData streamFileData, AccountBalanceFile streamFile, ConsensusNode node) {
        super.onVerified(streamFileData, streamFile, node);
        accountBalanceFileExists.set(true);
    }

    @Override
    protected boolean shouldDownload() {
        if (downloaderProperties.isEnabled()) {
            return true;
        }

        if (accountBalanceFileExists.get()) {
            return false;
        }

        if (accountBalanceFileRepository.findLatest().isPresent()) {
            accountBalanceFileExists.set(true);
            return false;
        }

        return true;
    }
}
