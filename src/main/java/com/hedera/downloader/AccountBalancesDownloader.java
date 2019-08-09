package com.hedera.downloader;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import com.hedera.configLoader.ConfigLoader;
import com.hedera.signatureVerifier.NodeSignatureVerifier;
import com.hedera.utilities.Utility;

public class AccountBalancesDownloader extends Downloader {


	public AccountBalancesDownloader(ConfigLoader configLoader) {
		super(configLoader);
	}

	public static void main(String[] args) {

		if (Utility.checkStopFile()) {
			log.info(MARKER, "Stop file found, exiting.");
			System.exit(0);
		}

		configLoader = new ConfigLoader();

		AccountBalancesDownloader downloader = new AccountBalancesDownloader(configLoader);
		
		while (true) {
			
			if (Utility.checkStopFile()) {
				log.info(MARKER, "Stop file found, stopping.");
				break;
			}
			
			setupCloudConnection();


			try {
				// balance files with sig verification 
				HashMap<String, List<File>> sigFilesMap = downloader.downloadSigFiles(DownloadType.BALANCE);
				//Verify signature files and download corresponding files of valid signature files
				downloader.verifySigsAndDownloadBalanceFiles(sigFilesMap);

//				downloader.downloadBalanceFiles();
				
				xfer_mgr.shutdownNow();

			} catch (IOException e) {
				log.error(MARKER, "IOException: {}", e);
			}
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
	 */
	String verifySigsAndDownloadBalanceFiles(Map<String, List<File>> sigFilesMap) {
		String validDir = null;
		String lastValidBalanceFileName = configLoader.getLastValidBalanceFileName();
		String newLastValidBalanceFileName = lastValidBalanceFileName;

		// reload address book and keys
		NodeSignatureVerifier verifier = new NodeSignatureVerifier(configLoader);
		
		for (String fileName : sigFilesMap.keySet()) {
			if (Utility.checkStopFile()) {
				log.info(MARKER, "Stop file found, stopping.");
				break;
			}
			List<File> sigFiles = sigFilesMap.get(fileName);
			// If the number of sigFiles is not greater than 2/3 of number of nodes, we don't need to verify them
			if (!Utility.greaterThanSuperMajorityNum(sigFiles.size(), nodeAccountIds.size())) {
				continue;
			} else {
				// validSigFiles are signed by node'key and contains the same Hash which has been agreed by more than 2/3 nodes
				List<File> validSigFiles = verifier.verifySignatureFiles(sigFiles);
				for (File validSigFile : validSigFiles) {
					if (Utility.checkStopFile()) {
						log.info(MARKER, "Stop file found, stopping.");
						break;
					}
					if (validDir == null) {
						validDir = validSigFile.getParentFile().getParent() + "/valid/";
					}
					Pair<Boolean, File> fileResult = downloadBalanceFile(validSigFile, validDir);
					File file = fileResult.getRight();
					if (file != null &&
							Utility.hashMatch(validSigFile, file)) {
						if (newLastValidBalanceFileName.isEmpty() ||
								fileNameComparator.compare(newLastValidBalanceFileName, file.getName()) < 0) {
							newLastValidBalanceFileName = file.getName();
						}
						break;
					} else if (file != null) {
						log.warn(MARKER, "{}'s Hash doesn't match the Hash contained in valid signature file. Will try to download a balance file with same timestamp from other nodes and check the Hash.", file.getPath());
					}
				}
			}
		}
		if (!newLastValidBalanceFileName.equals(lastValidBalanceFileName)) {
			configLoader.setLastValidBalanceFileName(newLastValidBalanceFileName);
			configLoader.saveToFile();
		}
		return validDir;
	}

	Pair<Boolean, File> downloadBalanceFile(File sigFile, String validDir) {
		String nodeAccountId = Utility.getAccountIDStringFromFilePath(sigFile.getPath());
		String sigFileName = sigFile.getName();
		String balanceFileName = sigFileName.replace("_Balances.csv_sig", "_Balances.csv");
		String s3ObjectKey = "accountBalances/balance" + nodeAccountId + "/" + balanceFileName;
		String localFileName = validDir + balanceFileName;
		return saveToLocal(bucketName, s3ObjectKey, localFileName);
	}


}
