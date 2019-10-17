package com.hedera.mirror.downloader.record;

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

import com.amazonaws.services.s3.transfer.TransferManager;

import com.hedera.mirror.addressbook.NetworkAddressBook;
import com.hedera.mirror.downloader.Downloader;
import com.hedera.mirror.domain.ApplicationStatusCode;
import com.hedera.mirror.repository.ApplicationStatusRepository;
import com.hedera.mirror.parser.record.RecordFileParser;
import com.hedera.mirror.downloader.NodeSignatureVerifier;
import com.hedera.utilities.Utility;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.scheduling.annotation.Scheduled;

import javax.inject.Named;
import java.io.File;
import java.nio.file.*;
import java.util.*;

@Log4j2
@Named
public class RecordFileDownloader extends Downloader {

	public RecordFileDownloader(TransferManager transferManager, ApplicationStatusRepository applicationStatusRepository, NetworkAddressBook networkAddressBook, RecordDownloaderProperties downloaderProperties) {
		super(transferManager, applicationStatusRepository, networkAddressBook, downloaderProperties);
	}

	@Scheduled(fixedRateString = "${hedera.mirror.downloader.record.frequency:500}")
	public void download() {
		try {
			if (!downloaderProperties.isEnabled()) {
				return;
			}

			if (Utility.checkStopFile()) {
				log.info("Stop file found");
				return;
			}

			final var sigFilesMap = downloadSigFiles();
			verifySigsAndDownloadRecordFiles(sigFilesMap);
		} catch (Exception e) {
			log.error("Error downloading and verifying new record files", e);
		}
	}

	/**
	 * Verify the .rcd files to see if the file Hash matches prevFileHash
	 * @throws Exception
	 */
	private boolean verifyHashChain(File recordFile) throws Exception {
		String recordPath = recordFile.getAbsolutePath();
		String lastValidRecordFileHash = applicationStatusRepository.findByStatusCode(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE_HASH);
		String bypassMismatch = applicationStatusRepository.findByStatusCode(ApplicationStatusCode.RECORD_HASH_MISMATCH_BYPASS_UNTIL_AFTER);
		String prevFileHash = RecordFileParser.readPrevFileHash(recordPath);

		if (prevFileHash == null) {
			log.warn("Doesn't contain valid previous file hash: {}", recordPath);
			return false;
		}

		if (StringUtils.isBlank(lastValidRecordFileHash) || lastValidRecordFileHash.equals(prevFileHash) ||
				Utility.hashIsEmpty(prevFileHash) || bypassMismatch.compareTo(recordFile.getName()) > 0) {
			return true;
		}

		log.warn("File Hash Mismatch with previous: {}, expected {}, got {}", recordFile.getName(), lastValidRecordFileHash, prevFileHash);
		return false;
	}

	/**
	 *  For each group of signature Files with the same file name:
	 *  (1) verify that the signature files are signed by corresponding node's PublicKey;
	 *  (2) For valid signature files, we compare their Hashes to see if more than 2/3 Hashes matches.
	 *  If more than 2/3 Hashes matches, we download the corresponding .rcd file from a node folder which has valid signature file.
	 *  (3) compare the Hash of .rcd file with Hash which has been agreed on by valid signatures, if match, move the .rcd file into `valid` directory; else download .rcd file from other valid node folder, and compare the Hash until find a match one
	 *  return the name of directory which contains valid .rcd files
	 * @param sigFilesMap
	 */
	private void verifySigsAndDownloadRecordFiles(Map<String, List<File>> sigFilesMap) {
		// reload address book and keys
		NodeSignatureVerifier verifier = new NodeSignatureVerifier(networkAddressBook);
        Path validDir = downloaderProperties.getValidPath();

		List<String> fileNames = new ArrayList<String>(sigFilesMap.keySet());

		Collections.sort(fileNames);

		if (Utility.checkStopFile()) {
			log.info("Stop file found, stopping");
			return;
		}
		for (String fileName : fileNames) {
			boolean valid = false;
			List<File> sigFiles = sigFilesMap.get(fileName);

			// If the number of sigFiles is not greater than 2/3 of number of nodes, we don't need to verify them
			if (sigFiles == null || !Utility.greaterThanSuperMajorityNum(sigFiles.size(), nodeAccountIds.size())) {
				log.warn("Signature file count for {} does not exceed 2/3 of nodes", fileName);
				continue;
			}

			// validSigFiles are signed by node key and contains the same hash which has been agreed by more than 2/3 nodes
            final var hashAndvalidSigFiles = verifier.verifySignatureFiles(sigFiles);
			final byte[] validHash = hashAndvalidSigFiles.getLeft();
			for (File validSigFile : hashAndvalidSigFiles.getRight()) {
				if (Utility.checkStopFile()) {
					log.info("Stop file found, stopping");
					break;
				}

				try {
					Pair<Boolean, File> rcdFileResult = downloadFile(validSigFile);
					File rcdFile = rcdFileResult.getRight();
					if (rcdFile != null && Utility.hashMatch(validHash, rcdFile)) {
						if (verifyHashChain(rcdFile)) {
							// move the file to the valid directory
							String name = rcdFile.getName();
							String hash = Utility.bytesToHex(Utility.getFileHash(rcdFile.getAbsolutePath()));
							File validFile = validDir.resolve(name).toFile();

							if (moveFile(rcdFile, validFile)) {
								log.debug("Verified signature file matches at least 2/3 of nodes: {}", fileName);
								applicationStatusRepository.updateStatusValue(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE_HASH, hash);
								applicationStatusRepository.updateStatusValue(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE, name);
								valid = true;
								break;
							}
						}
					} else if (rcdFile != null) {
						log.warn("Hash of {} doesn't match the hash contained in the signature file. Will try to download a record file with same timestamp from other nodes", rcdFile);
					}
				} catch (Exception e) {
					log.error("Unable to verify signature {}", validSigFile, e);
				}
			}

			if (!valid) {
				log.error("File could not be verified by at least 2/3 of nodes: {}", fileName);
			}
		}
	}

    protected DownloadType getType() {
        return DownloadType.RCD;
    }

    protected ApplicationStatusCode getLastValidDownloadedFileKey() {
        return ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE;
    }

    protected String getDataFileName(String sigFileName) {
        return sigFileName.replace(".rcd_sig", ".rcd");
    }

    protected boolean isNeededSigFile(String s3ObjectKey) {
        return Utility.isRecordSigFile(s3ObjectKey);
    }
}
