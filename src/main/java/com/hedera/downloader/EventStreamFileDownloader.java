package com.hedera.downloader;

import com.hedera.configLoader.ConfigLoader;
import com.hedera.downloader.Downloader.DownloadType;
import com.hedera.parser.EventStreamFileParser;
import com.hedera.signatureVerifier.NodeSignatureVerifier;
import com.hedera.utilities.Utility;

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

public class EventStreamFileDownloader extends Downloader {

	private static String validDir = null;

	public EventStreamFileDownloader(ConfigLoader configLoader) {
		super(configLoader);
	}
	
	public static void downloadNewEventfiles(EventStreamFileDownloader downloader) {
		setupCloudConnection();
		if (Utility.checkStopFile()) {
			log.info(MARKER, "Stop file found, exiting.");
			System.exit(0);
		}

		HashMap<String, List<File>> sigFilesMap;
		try {
			sigFilesMap = downloader.downloadSigFiles(DownloadType.EVENT);

			if (Utility.checkStopFile()) {
				xfer_mgr.shutdownNow();
				log.info(MARKER, "Stop file found, exiting.");
				System.exit(0);
			}
			
			// Verify signature files and download .evts files of valid signature files
			downloader.verifySigsAndDownloadEventStreamFiles(sigFilesMap);

			if (validDir != null) {
//				new Thread(() -> {
					verifyValidFiles(validDir);
//				}).start();
			}

			xfer_mgr.shutdownNow();

		} catch (IOException e) {
			log.error(MARKER, "IOException: {}", e);
		}
	}

	public static void main(String[] args) {
		if (Utility.checkStopFile()) {
			log.info(MARKER, "Stop file found, exiting.");
			System.exit(0);
		}
		configLoader = new ConfigLoader();

		EventStreamFileDownloader downloader = new EventStreamFileDownloader(configLoader);

		while (true) {
			if (Utility.checkStopFile()) {
				log.info(MARKER, "Stop file found, stopping.");
				break;
			}
			downloadNewEventfiles(downloader);
		}
		
	}

	/**
	 * Check if there is any missing .evts file:
	 * (1) Sort .evts files by timestamp,
	 * (2) Verify the .evts files to see if the file Hash matches prevFileHash
	 *
	 * @param validDir
	 */
	public static void verifyValidFiles(String validDir) {
		String lastValidEventFileName = configLoader.getLastValidEventFileName();
		String lastValidEventFileHash = configLoader.getLastValidEventFileHash();

		File validDirFile = new File(validDir);
		if (!validDirFile.exists()) {
			return;
		}
		try (Stream<Path> pathStream = Files.walk(validDirFile.toPath())) {
			List<String> fileNames = pathStream.filter(p -> Utility.isEventStreamFile(p.toString()))
					.filter(p -> lastValidEventFileName.isEmpty() ||
							fileNameComparator.compare(p.toFile().getName(), lastValidEventFileName) > 0)
					.sorted(pathComparator)
					.map(p -> p.toString()).collect(Collectors.toList());

			String newLastValidEventFileName = lastValidEventFileName;
			String newLastValidEventFileHash = lastValidEventFileHash;

			for (String fileName : fileNames) {
				String prevFileHash = EventStreamFileParser.readPrevFileHash(fileName);
				if (prevFileHash == null) {
					log.info(MARKER, "{} doesn't contain valid prevFileHash", fileName);
					break;
				}
				if (newLastValidEventFileHash.isEmpty() ||
						newLastValidEventFileHash.equals(prevFileHash) ||
						prevFileHash.equals(Hex.encodeHexString(new byte[48]))) {
					newLastValidEventFileHash = Utility.bytesToHex(Utility.getFileHash(fileName));
					newLastValidEventFileName = new File(fileName).getName();
				} else {
					break;
				}
			}

			if (!newLastValidEventFileName.equals(lastValidEventFileName)) {
				configLoader.setLastValidEventFileHash(newLastValidEventFileHash);
				configLoader.setLastValidEventFileName(newLastValidEventFileName);
				configLoader.saveEventsDataToFile();
			}

		} catch (IOException ex) {
			log.error(MARKER, "verifyValidFiles :: An Exception occurs while traverse {} : {}", validDir,
					ex.getStackTrace());
		}
	}

	/**
	 * For each group of signature Files with the same file name:
	 * (1) verify that the signature files are signed by corresponding node's PublicKey;
	 * (2) For valid signature files, we compare their Hashes to see if more than 2/3 Hashes matches.
	 * If more than 2/3 Hashes matches, we download the corresponding .evts file from a node folder which has valid
	 * signature file.
	 * (3) compare the Hash of .evts file with Hash which has been agreed on by valid signatures, if match, move the
	 * .evts file into `valid` directory; else download .evts file from other valid node folder, and compare the Hash
	 * until find a match one
	 * return the name of directory which contains valid .evts files
	 *
	 * @param sigFilesMap
	 */
	String verifySigsAndDownloadEventStreamFiles(Map<String, List<File>> sigFilesMap) {

		NodeSignatureVerifier verifier = new NodeSignatureVerifier(configLoader);
		for (String fileName : sigFilesMap.keySet()) {
			List<File> sigFiles = sigFilesMap.get(fileName);
			// If the number of sigFiles is not greater than 2/3 of number of nodes, we don't need to verify them
			if (!Utility.greaterThanSuperMajorityNum(sigFiles.size(), nodeAccountIds.size())) {
				continue;
			} else {
				// validSigFiles are signed by node'key and contains the same Hash which has been agreed by more than
				// 2/3 nodes
				List<File> validSigFiles = verifier.verifySignatureFiles(sigFiles);
				if (validSigFiles != null) {
					for (File validSigFile : validSigFiles) {
						if (validDir == null) {
							validDir = validSigFile.getParentFile().getParent() + "/valid/";
						}
						Pair<Boolean, File> fileResult = downloadFile(validSigFile, validDir);
						File file = fileResult.getRight();
						if (file != null &&
								Utility.hashMatch(validSigFile, file)) {
							break;
						} else if (file != null) {
							log.warn(MARKER,
									"{}'s Hash doesn't match the Hash contained in valid signature file. Will try to " +
											"download a .evts file with same timestamp from other nodes and check the" +
											" " +
											"Hash.",
									file.getPath());
						}
					}
				} else {
					log.info(MARKER, "No valid signature files");
				}
			}
		}
		return validDir;
	}

	/**
	 * Download .evts file
	 *
	 * @param sigFile
	 * @param validDir
	 * @return
	 */
	Pair<Boolean, File> downloadFile(File sigFile, String validDir) {
		String nodeAccountId = Utility.getAccountIDStringFromFilePath(sigFile.getPath());
		String sigFileName = sigFile.getName();
		String fileName = sigFileName.replace(".evts_sig", ".evts");
		String s3ObjectKey = configLoader.getEventFilesS3Location() + nodeAccountId + "/" + fileName;
		String localFileName = validDir + fileName;
		return saveToLocal(bucketName, s3ObjectKey, localFileName);
	}
}
