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
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
public class RecordFileDownloader extends Downloader {

	private static String validDir = ConfigLoader.getDefaultParseDir(OPERATION_TYPE.RECORDS);
	private static String tmpDir = ConfigLoader.getDefaultTmpDir(OPERATION_TYPE.RECORDS);

	public RecordFileDownloader() throws Exception {
		Utility.createDirIfNotExists(validDir);
		Utility.createDirIfNotExists(tmpDir);
		Utility.purgeDirectory(tmpDir);
	}

	public static void downloadNewRecordfiles(RecordFileDownloader downloader) throws Exception {
		setupCloudConnection();

		HashMap<String, List<File>> sigFilesMap;
		try {
			sigFilesMap = downloader.downloadSigFiles(DownloadType.RCD);

			// Verify signature files and download .rcd files of valid signature files
			downloader.verifySigsAndDownloadRecordFiles(sigFilesMap);

			xfer_mgr.shutdownNow();

		} catch (IOException e) {
			log.error("Error downloading and verifying new record files", e);
		}
	}

	public static void main(String[] args) throws Exception {
		RecordFileDownloader downloader = new RecordFileDownloader();

		while (true) {
			if (Utility.checkStopFile()) {
				log.info("Stop file found, stopping");
				break;
			}
			downloadNewRecordfiles(downloader);
		}
	}

	/**
	 * Verify the .rcd files to see if the file Hash matches prevFileHash
	 * @param fileToCheck
	 * @throws Exception 
	 */
	public static boolean verifyValidRecordFile(File objfileToCheck) throws Exception {
		if (!objfileToCheck.exists()) {
			return false;
		}
		String lastValidRecordFileName =  applicationStatus.getLastValidDownloadedRecordFileName();
		String lastValidRecordFileHash = applicationStatus.getLastValidDownloadedRecordFileHash();
		String newLastValidRcdFileName = lastValidRecordFileName;
		String newLastValidRcdFileHash = lastValidRecordFileHash;

		String prevFileHash = RecordFileParser.readPrevFileHash(objfileToCheck.getPath());
		if (prevFileHash == null) {
			log.warn("Doesn't contain valid prevFileHash: {}", objfileToCheck.getPath());
			return false;
		}
		if (newLastValidRcdFileHash.isEmpty() ||
				newLastValidRcdFileHash.equals(prevFileHash) ||
				prevFileHash.equals(Hex.encodeHexString(new byte[48]))) {
			newLastValidRcdFileHash = Utility.bytesToHex(Utility.getRecordFileHash(objfileToCheck.getPath()));
			newLastValidRcdFileName = objfileToCheck.getName();
		} else if (applicationStatus.getBypassRecordHashMismatchUntilAfter().compareTo(objfileToCheck.getName()) > 0) {
			newLastValidRcdFileName = objfileToCheck.getName();
			newLastValidRcdFileHash = Utility.bytesToHex(Utility.getRecordFileHash(objfileToCheck.getPath()));
		} else {
			log.warn("File Hash Mismatch with previous : {}, expected {}, got {}", objfileToCheck.getPath(), newLastValidRcdFileHash, prevFileHash);
			return false;
		}

		if (!newLastValidRcdFileName.equals(lastValidRecordFileName)) {
			applicationStatus.updateLastValidDownloadedRecordFileHash(newLastValidRcdFileHash);
			applicationStatus.updateLastValidDownloadedRecordFileName(newLastValidRcdFileName);
		}

		return true;
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

		for (String fileName : fileNames) {
			if (Utility.checkStopFile()) {
				log.info("Stop file found, stopping");
				break;
			}
			boolean valid = false;
			List<File> sigFiles = sigFilesMap.get(fileName);
			
			// If the number of sigFiles is not greater than 2/3 of number of nodes, we don't need to verify them
			if (sigFiles == null || !Utility.greaterThanSuperMajorityNum(sigFiles.size(), nodeAccountIds.size())) {
				log.warn("Signature file count does not exceed 2/3 of nodes");
				continue;
			}

			// validSigFiles are signed by node key and contains the same hash which has been agreed by more than 2/3 nodes
			List<File> validSigFiles = verifier.verifySignatureFiles(sigFiles);
			for (File validSigFile : validSigFiles) {
				if (Utility.checkStopFile()) {
					log.info("Stop file found, stopping");
					break;
				}

				Pair<Boolean, File> rcdFileResult = downloadFile(DownloadType.RCD, validSigFile, tmpDir);
				File rcdFile = rcdFileResult.getRight();
				if (rcdFile != null && Utility.hashMatch(validSigFile, rcdFile)) {
					try {
						if (verifyValidRecordFile(rcdFile)) {
							// move the file to the valid directory
							File fTo = new File(validDir + "/" + rcdFile.getName());

							if (moveFile(rcdFile, fTo)) {
								log.debug("Verified signature file matches at least 2/3 of nodes: {}", fileName);
								valid = true;
								break;
							}
						} else {
							break;
						}
					} catch (Exception e) {
						break;
					}
						
				} else if (rcdFile != null) {
					log.warn("Hash of {} doesn't match the hash contained in the signature file. Will try to download a record file with same timestamp from other nodes", rcdFile);
				}
			}

			if (!valid) {
				log.error("File could not be verified by at least 2/3 of nodes: {}", fileName);
			}
		}
	}

}
