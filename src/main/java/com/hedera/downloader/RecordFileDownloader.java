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

import com.hedera.configLoader.ConfigLoader;
import com.hedera.configLoader.ConfigLoader.OPERATION_TYPE;
import com.hedera.parser.RecordFileParser;
import com.hedera.signatureVerifier.NodeSignatureVerifier;
import com.hedera.utilities.Utility;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
public class RecordFileDownloader extends Downloader {

	private static final String EMPTY_HASH = Hex.encodeHexString(new byte[48]);
	private final String validDir = ConfigLoader.getDefaultParseDir(OPERATION_TYPE.RECORDS);
	private final String tmpDir = ConfigLoader.getDefaultTmpDir(OPERATION_TYPE.RECORDS);

	public RecordFileDownloader() throws Exception {
		Utility.ensureDirectory(validDir);
		Utility.ensureDirectory(tmpDir);
		Utility.purgeDirectory(tmpDir);
	}

	public static void main(String[] args) throws Exception {
		RecordFileDownloader downloader = new RecordFileDownloader();

		while (!Utility.checkStopFile()) {
			downloader.download();
		}

		xfer_mgr.shutdownNow();
	}

	public void download() {
		try {
			var sigFilesMap = downloadSigFiles(DownloadType.RCD);

			// Verify signature files and download .rcd files of valid signature files
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
		String lastValidRecordFileHash = applicationStatus.getLastValidDownloadedRecordFileHash();
		String bypassMismatch = StringUtils.defaultIfBlank(applicationStatus.getBypassRecordHashMismatchUntilAfter(), "");
		String prevFileHash = RecordFileParser.readPrevFileHash(recordPath);

		if (prevFileHash == null) {
			log.warn("Doesn't contain valid previous file hash: {}", recordPath);
			return false;
		}

		if (StringUtils.isBlank(lastValidRecordFileHash) || lastValidRecordFileHash.equals(prevFileHash) ||
				EMPTY_HASH.equals(prevFileHash) || bypassMismatch.compareTo(recordFile.getName()) > 0) {
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
		NodeSignatureVerifier verifier = new NodeSignatureVerifier();

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
			List<File> validSigFiles = verifier.verifySignatureFiles(sigFiles);
			for (File validSigFile : validSigFiles) {
				if (Utility.checkStopFile()) {
					log.info("Stop file found, stopping");
					break;
				}

				try {
					Pair<Boolean, File> rcdFileResult = downloadFile(DownloadType.RCD, validSigFile, tmpDir);
					File rcdFile = rcdFileResult.getRight();
					if (rcdFile != null && Utility.hashMatch(validSigFile, rcdFile)) {
						if (verifyHashChain(rcdFile)) {
							// move the file to the valid directory
							String name = rcdFile.getName();
							String hash = Utility.bytesToHex(Utility.getFileHash(rcdFile.getAbsolutePath()));
							File validFile = Paths.get(validDir, name).toFile();

							if (moveFile(rcdFile, validFile)) {
								log.debug("Verified signature file matches at least 2/3 of nodes: {}", fileName);
								applicationStatus.updateLastValidDownloadedRecordFileHash(hash);
								applicationStatus.updateLastValidDownloadedRecordFileName(name);
								valid = true;
								break;
							}
						}
					} else if (rcdFile != null) {
						log.warn("Hash of {} doesn't match the hash contained in the signature file. Will try to download a record file with same timestamp from other nodes", validSigFile);
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

}
