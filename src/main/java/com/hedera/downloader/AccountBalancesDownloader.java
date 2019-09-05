package com.hedera.downloader;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;

import com.hedera.configLoader.ConfigLoader;
import com.hedera.configLoader.ConfigLoader.OPERATION_TYPE;
import com.hedera.databaseUtilities.ApplicationStatus;
import com.hedera.signatureVerifier.NodeSignatureVerifier;
import com.hedera.utilities.Utility;

@Log4j2
public class AccountBalancesDownloader extends Downloader {

	private final String validDir = ConfigLoader.getDefaultParseDir(OPERATION_TYPE.BALANCE);
	private final String tmpDir = ConfigLoader.getDefaultTmpDir(OPERATION_TYPE.BALANCE);

	public AccountBalancesDownloader() throws Exception {
		Utility.ensureDirectory(validDir);
		Utility.ensureDirectory(tmpDir);
		Utility.purgeDirectory(tmpDir);
	}

	public static void main(String[] args) throws Exception {
		AccountBalancesDownloader downloader = new AccountBalancesDownloader();

		while (!Utility.checkStopFile()) {
			downloader.download();
		}

		log.info("Stop file found, stopping");
		xfer_mgr.shutdownNow();
	}

	public void download() {
		try {
			if (ConfigLoader.getBalanceVerifySigs()) {
				// balance files with sig verification
				Map<String, List<File>> sigFilesMap = downloadSigFiles(DownloadType.BALANCE);

				// Verify signature files and download corresponding files of valid signature files
				verifySigsAndDownloadBalanceFiles(sigFilesMap);
			} else {
				downloadBalanceFiles();
			}
		} catch (Exception e) {
			log.error("Error downloading balance files", e);
		}
	}

	/**
	 *  For each group of signature Files with the same file name:
	 *  (1) verify that the signature files are signed by corresponding node's PublicKey;
	 *  (2) For valid signature files, we compare their Hashes to see if more than 2/3 Hashes matches.
	 *  If more than 2/3 Hashes matches, we download the corresponding _Balances.csv file from a node folder which has valid signature file.
	 *  (3) compare the Hash of _Balances.csv file with Hash which has been agreed on by valid signatures, if match, move the _Balances.csv file into `valid` directory; else download _Balances.csv file from other valid node folder, and compare the Hash until find a match one
	 *  return the name of directory which contains valid _Balances.csv files
	 * @param sigFilesMap
	 * @throws Exception 
	 */
	private void verifySigsAndDownloadBalanceFiles(Map<String, List<File>> sigFilesMap) throws Exception {
		String lastValidBalanceFileName = applicationStatus.getLastValidDownloadedBalanceFileName();
		String newLastValidBalanceFileName = lastValidBalanceFileName;

		// reload address book and keys
		NodeSignatureVerifier verifier = new NodeSignatureVerifier();

		List<String> fileNames = new ArrayList<String>(sigFilesMap.keySet());
		Collections.sort(fileNames);
		
		for (String fileName : fileNames) {
			if (Utility.checkStopFile()) {
				log.info("Stop file found, stopping");
				break;
			}

			List<File> sigFiles = sigFilesMap.get(fileName);
			boolean valid = false;

			// If the number of sigFiles is not greater than 2/3 of number of nodes, we don't need to verify them
			if (sigFiles == null || !Utility.greaterThanSuperMajorityNum(sigFiles.size(), nodeAccountIds.size())) {
				log.warn("Signature file count does not exceed 2/3 of nodes");
				continue;
			}

			// validSigFiles are signed by node'key and contains the same Hash which has been agreed by more than 2/3 nodes
			List<File> validSigFiles = verifier.verifySignatureFiles(sigFiles);
			for (File validSigFile : validSigFiles) {
				if (Utility.checkStopFile()) {
					log.info("Stop file found, stopping");
					break;
				}

				Pair<Boolean, File> fileResult = downloadFile(DownloadType.BALANCE, validSigFile, tmpDir);
				File file = fileResult.getRight();
				if (file != null && Utility.hashMatch(validSigFile, file)) {

					// move the file to the valid directory
					File fTo = new File(validDir + file.getName());

					if (moveFile(file, fTo)) {
						if (newLastValidBalanceFileName.isEmpty() ||
								fileNameComparator.compare(newLastValidBalanceFileName, file.getName()) < 0) {
							newLastValidBalanceFileName = file.getName();
							log.debug("Verified signature file matches at least 2/3 of nodes: {}", fileName);
						}
						valid = true;
						break;
					}
				} else if (file != null) {
					log.warn("Hash doesn't match the hash contained in valid signature file. Will try to download a balance file with same timestamp from other nodes and check the Hash: {}", file);
				}
			}

			if (!valid) {
				log.error("File could not be verified by at least 2/3 of nodes: {}", fileName);
			}
		}
		if (!newLastValidBalanceFileName.equals(lastValidBalanceFileName)) {
			applicationStatus.updateLastValidDownloadedBalanceFileName(newLastValidBalanceFileName);
		}
	}
}
