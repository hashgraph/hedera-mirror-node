package com.hedera.downloader;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
public class RecordFileDownloader extends Downloader {

	private static String validDir = ConfigLoader.getDefaultParseDir(OPERATION_TYPE.RECORDS);
	private static String tmpDir = ConfigLoader.getDefaultTmpDir(OPERATION_TYPE.RECORDS);

	public RecordFileDownloader() {
		Utility.createDirIfNotExists(validDir);
		Utility.createDirIfNotExists(tmpDir);
	}

	public static void downloadNewRecordfiles(RecordFileDownloader downloader) {
		setupCloudConnection();

		HashMap<String, List<File>> sigFilesMap;
		try {
			sigFilesMap = downloader.downloadSigFiles(DownloadType.RCD);

			// Verify signature files and download .rcd files of valid signature files
			downloader.verifySigsAndDownloadRecordFiles(sigFilesMap);

			if (validDir != null) {
//				new Thread(() -> {
					verifyValidRecordFiles(validDir);
//				}).start();
			} else {
			}

			xfer_mgr.shutdownNow();

		} catch (IOException e) {
			log.error("Error downloading and verifying new record files", e);
		}
	}

	public static void main(String[] args) {
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
	 * Check if there is any missing .rcd file:
	 * (1) Sort .rcd files by timestamp,
	 * (2) Verify the .rcd files to see if the file Hash matches prevFileHash
	 * @param validDir
	 */
	public static void verifyValidRecordFiles(String validDir) {
		String lastValidRcdFileName =  ConfigLoader.getLastValidRcdFileName();
		String lastValidRcdFileHash = ConfigLoader.getLastValidRcdFileHash();
		File validDirFile = new File(validDir);
		if (!validDirFile.exists()) {
			return;
		}
		try (Stream<Path> pathStream = Files.walk(validDirFile.toPath())) {
			List<String> fileNames = pathStream.filter(p -> Utility.isRecordFile(p.toString()))
					.filter(p -> lastValidRcdFileName.isEmpty() ||
							fileNameComparator.compare(p.toFile().getName(), lastValidRcdFileName) > 0)
					.sorted(pathComparator)
					.map(p -> p.toString()).collect(Collectors.toList());

			String newLastValidRcdFileName = lastValidRcdFileName;
			String newLastValidRcdFileHash = lastValidRcdFileHash;

			for (String rcdName : fileNames) {
				if (Utility.checkStopFile()) {
					log.info("Stop file found, stopping");
					break;
				}
				String prevFileHash = RecordFileParser.readPrevFileHash(rcdName);
				if (prevFileHash == null) {
					log.warn("Doesn't contain valid prevFileHash: {}", rcdName);
					break;
				}
				if (newLastValidRcdFileHash.isEmpty() ||
						newLastValidRcdFileHash.equals(prevFileHash) ||
						prevFileHash.equals(Hex.encodeHexString(new byte[48]))) {
					newLastValidRcdFileHash = Utility.bytesToHex(Utility.getFileHash(rcdName));
					newLastValidRcdFileName = new File(rcdName).getName();
				} else {
					break;
				}
			}

			if (!newLastValidRcdFileName.equals(lastValidRcdFileName)) {
				ConfigLoader.setLastValidRcdFileHash(newLastValidRcdFileHash);
				ConfigLoader.setLastValidRcdFileName(newLastValidRcdFileName);
				ConfigLoader.saveRecordsDataToFile();
			}

		} catch (Exception ex) {
			log.error("Failed to verify record files in {}", validDir, ex);
		}
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
	String verifySigsAndDownloadRecordFiles(Map<String, List<File>> sigFilesMap) {

		// reload address book and keys
		NodeSignatureVerifier verifier = new NodeSignatureVerifier();

		for (String fileName : sigFilesMap.keySet()) {
			if (Utility.checkStopFile()) {
				log.info("Stop file found, stopping");
				break;
			}
			List<File> sigFiles = sigFilesMap.get(fileName);
			// If the number of sigFiles is not greater than 2/3 of number of nodes, we don't need to verify them
			if (sigFiles == null || !Utility.greaterThanSuperMajorityNum(sigFiles.size(), nodeAccountIds.size())) {
				log.warn("Signature file count does not exceed 2/3 of nodes");
				continue;
			} else {
				// validSigFiles are signed by node'key and contains the same Hash which has been agreed by more than 2/3 nodes
				List<File> validSigFiles = verifier.verifySignatureFiles(sigFiles);
				if (validSigFiles != null) {
					for (File validSigFile : validSigFiles) {
						if (Utility.checkStopFile()) {
							log.info("Stop file found, stopping");
							break;
						}
						
						Pair<Boolean, File> rcdFileResult = downloadFile(DownloadType.RCD, validSigFile, tmpDir);
						File rcdFile = rcdFileResult.getRight();
						if (rcdFile != null && Utility.hashMatch(validSigFile, rcdFile)) {
							// move the file to the valid directory
					        File fTo = new File(validDir + "/" + rcdFile.getName());

					        if (moveFile(rcdFile, fTo)) {
					        	break;
					        }
						} else if (rcdFile != null) {
							log.warn("Hash of {} doesn't match the hash contained in the signature file. Will try to download a record file with same timestamp from other nodes", rcdFile);
						}
					}
				} else {
					log.info("No valid signature files");
				}
			}
		}
		return validDir;
	}

}
