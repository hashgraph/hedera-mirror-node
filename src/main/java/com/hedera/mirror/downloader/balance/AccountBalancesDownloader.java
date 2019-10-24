package com.hedera.mirror.downloader.balance;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.s3.transfer.TransferManager;

import com.hedera.mirror.addressbook.NetworkAddressBook;
import com.hedera.mirror.domain.ApplicationStatusCode;
import com.hedera.mirror.downloader.Downloader;
import com.hedera.mirror.repository.ApplicationStatusRepository;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;

import com.hedera.mirror.downloader.NodeSignatureVerifier;
import com.hedera.utilities.Utility;
import org.springframework.scheduling.annotation.Scheduled;

import javax.inject.Named;

@Log4j2
@Named
public class AccountBalancesDownloader extends Downloader {

	public AccountBalancesDownloader(TransferManager transferManager, ApplicationStatusRepository applicationStatusRepository, NetworkAddressBook networkAddressBook, BalanceDownloaderProperties downloaderProperties) {
		super(transferManager, applicationStatusRepository, networkAddressBook, downloaderProperties);
	}

	@Scheduled(fixedRateString = "${hedera.mirror.downloader.balance.frequency:500}")
	public void download() {
		try {
			if (!downloaderProperties.isEnabled()) {
                return;
            }

			if (Utility.checkStopFile()) {
				log.info("Stop file found");
				return;
			}

			// balance files with sig verification
			final var sigFilesMap = downloadSigFiles();

			// Verify signature files and download corresponding files of valid signature files
			verifySigsAndDownloadDataFiles(sigFilesMap);
		} catch (Exception e) {
			log.error("Error downloading balance files", e);
		}
	}

    protected ApplicationStatusCode getLastValidDownloadedFileKey() {
        return ApplicationStatusCode.LAST_VALID_DOWNLOADED_BALANCE_FILE;
    }

    protected ApplicationStatusCode getLastValidDownloadedFileHashKey() {
        return null; // Not used since shouldVerifyHashChain returns false;
    }

    protected ApplicationStatusCode getBypassHashKey() {
        return null; // Not used since shouldVerifyHashChain returns false;
    }

    protected boolean shouldVerifyHashChain() {
        return false;
    }

    protected String getPrevFileHash(String filePath) {
        return null;  // Only used if verifyHashChain() return true
    }
}
