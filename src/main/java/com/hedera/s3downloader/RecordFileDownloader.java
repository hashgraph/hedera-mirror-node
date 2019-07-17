package com.hedera.s3downloader;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.hedera.configLoader.ConfigLoader;
import com.hedera.configLoader.ConfigLoader.CLOUD_PROVIDER;
import com.hedera.mirrorNodeProxy.Utility;
import com.hedera.recordFileParser.RecordFileParser;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RecordFileDownloader extends Downloader {

	private static String validRcdDir = null;

	public RecordFileDownloader(ConfigLoader configLoader) {
		super(configLoader);
	}

	public static void main(String[] args) {
		configLoader = new ConfigLoader("./config/config.json");

		RecordFileDownloader downloader = new RecordFileDownloader(configLoader);

		while (true) {

			if (configLoader.getCloudProvider() == CLOUD_PROVIDER.S3) {
				s3Client = AmazonS3ClientBuilder.standard()
						.withCredentials(new AWSStaticCredentialsProvider(
								new BasicAWSCredentials(configLoader.getAccessKey(),
										configLoader.getSecretKey())))
						.withRegion(configLoader.getClientRegion())
						.withClientConfiguration(clientConfiguration)
						.build();
			} else {
				s3Client = AmazonS3ClientBuilder.standard()
						.withEndpointConfiguration(
				                new AwsClientBuilder.EndpointConfiguration(
				                    "https://storage.googleapis.com", "auto"))
						.withCredentials(new AWSStaticCredentialsProvider(
								new BasicAWSCredentials(configLoader.getAccessKey(),
										configLoader.getSecretKey())))
						.withRegion(configLoader.getClientRegion())
						.withClientConfiguration(clientConfiguration)
						.build();
			}
			xfer_mgr = TransferManagerBuilder.standard()
					.withS3Client(s3Client).build();

			HashMap<String, List<File>> sigFilesMap;
			try {
				sigFilesMap = downloader.downloadSigFiles(DownloadType.RCD);

				// Verify signature files and download .rcd files of valid signature files
				downloader.verifySigsAndDownloadRCFiles(sigFilesMap);

				if (validRcdDir != null) {
					new Thread(() -> {
						verifyValidRcdFiles(validRcdDir);
					}).start();
				}

				xfer_mgr.shutdownNow();

				if (configLoader.getDownloadPeriodSec() == 0) {
					break;
				} else {
					Thread.sleep(configLoader.getDownloadPeriodSec() * 1000);
				}
			} catch (IOException e) {
				log.error(MARKER, "IOException: {}", e.getStackTrace());
			} catch (InterruptedException e) {
				log.error(MARKER, "InterruptedException: {}", e.getStackTrace());
			}
		}
	}

	/**
	 * Check if there is any missing .rcd file:
	 * (1) Sort .rcd files by timetamp,
	 * (2) Verify the .rcd files to see if the file Hash matches prevFileHash
	 * @param validDir
	 */
	public static void verifyValidRcdFiles(String validDir) {
		String lastValidRcdFileName = configLoader.getLastValidRcdFileName();
		String lastValidRcdFileHash = configLoader.getLastValidRcdFileHash();

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
				String prevFileHash = RecordFileParser.readPrevFileHash(rcdName);
				if (prevFileHash == null) {
					log.info(MARKER, "{} doesn't contain valid prevFileHash", rcdName);
					break;
				}
				if (newLastValidRcdFileHash.isEmpty() ||
						newLastValidRcdFileHash.equals(prevFileHash) ||
						prevFileHash.equals(Hex.encodeHexString(new byte[48]))) {
					newLastValidRcdFileHash = Utility.bytesToHex(RecordFileParser.getFileHash(rcdName));
					newLastValidRcdFileName = new File(rcdName).getName();
				} else {
					break;
				}
			}

			if (!newLastValidRcdFileName.equals(lastValidRcdFileName)) {
				configLoader.setLastValidRcdFileHash(newLastValidRcdFileHash);
				configLoader.setLastValidRcdFileName(newLastValidRcdFileName);
				configLoader.saveToFile();
			}

		} catch (IOException ex) {
			log.error(MARKER, "verifyValidRcdFiles :: An Exception occurs while traverse {} : {}", validDir, ex.getStackTrace());
		}
	}

	/**
	 * Download all .rcd and .rcd_sig files with timestamp later than lastDownloadedRcdSigName
	 */
	private void downloadRecordStreamFiles() {
		String lastDownloadedRcdSigName = configLoader.getLastDownloadedRcdSigName();

		String lastDownloadedRcdSigName_new = null;

		for (String nodeAccountId : nodeAccountIds) {
			String latestRcdSigKey = null;

			log.info(MARKER, "Start downloading RecordStream files of node " + nodeAccountId);
			long count = 0;
			// Get a list of objects in the bucket, 100 at a time
			String prefix = "recordstreams/record" + nodeAccountId + "/";

			ListObjectsRequest listRequest = new ListObjectsRequest()
					.withBucketName(bucketName)
					.withPrefix(prefix)
					.withDelimiter("/")
					.withMarker(prefix + lastDownloadedRcdSigName)
					.withMaxKeys(100);
			ObjectListing objects = s3Client.listObjects(listRequest);
			try {
				while(true) {
					List<S3ObjectSummary> summaries = objects.getObjectSummaries();
					for(S3ObjectSummary summary : summaries) {
						String s3ObjectKey = summary.getKey();
						Pair<Boolean, File> result;
						try {
							result = saveToLocal(bucketName, s3ObjectKey);
							if (result.getLeft()) {
								count++;
							}
							if (result.getRight() != null && Utility.isRecordSigFile(s3ObjectKey)
									&& (latestRcdSigKey == null || s3KeyComparator.compare(s3ObjectKey, latestRcdSigKey) > 0)){
								latestRcdSigKey = s3ObjectKey;
							}
						} catch (IOException e) {
							log.error(MARKER, "IOException: {}", e.getStackTrace());
						}
					}
					if(objects.isTruncated()) {
						objects = s3Client.listNextBatchOfObjects(objects);
					}
					else {
						break;
					}
				}
				log.info(MARKER, "Downloaded {} RecordStream files of node {}", count, nodeAccountId);
				if (latestRcdSigKey != null) {
					String latestRcdSigKey_fileName = Utility.getFileNameFromS3SummaryKey(latestRcdSigKey);
					if (lastDownloadedRcdSigName_new == null || fileNameComparator.compare(lastDownloadedRcdSigName_new, latestRcdSigKey_fileName) > 0){
						lastDownloadedRcdSigName_new = latestRcdSigKey_fileName;
					}
				} else {
					// If no new file for this node, we should set lastDownloadedRcdSigName_new equal to lastDownloadedRcdSigName, because for this node, the lastDownloadedRcdSigName remains the same.
					lastDownloadedRcdSigName_new = lastDownloadedRcdSigName;
				}
			} catch(AmazonServiceException e) {
				// The call was transmitted successfully, but Amazon S3 couldn't process
				// it, so it returned an error response.
	            log.error(MARKER, "Record download failed, Exception: {}", e.getStackTrace());
			} catch(SdkClientException e) {
				// Amazon S3 couldn't be contacted for a response, or the client
				// couldn't parse the response from Amazon S3.
	            log.error(MARKER, "Record download failed, Exception: {}", e.getStackTrace());
			}
		}

		if (lastDownloadedRcdSigName_new != null && lastDownloadedRcdSigName_new != lastDownloadedRcdSigName) {
			configLoader.setLastDownloadedRcdSigName(lastDownloadedRcdSigName_new);
			configLoader.saveToFile();
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
	String verifySigsAndDownloadRCFiles(Map<String, List<File>> sigFilesMap) {

		for (String fileName : sigFilesMap.keySet()) {
			List<File> sigFiles = sigFilesMap.get(fileName);
			// If the number of sigFiles is not greater than 2/3 of number of nodes, we don't need to verify them
			if (!Utility.greaterThanSuperMajorityNum(sigFiles.size(), nodeAccountIds.size())) {
				continue;
			} else {
				// validSigFiles are signed by node'key and contains the same Hash which has been agreed by more than 2/3 nodes
				List<File> validSigFiles = verifier.verifySignatureFiles(sigFiles);
				if (validSigFiles != null) {
					for (File validSigFile : validSigFiles) {
						if (validRcdDir == null) {
							validRcdDir = validSigFile.getParentFile().getParent() + "/valid/";
						}
						Pair<Boolean, File> rcdFileResult = downloadRcdFile(validSigFile, validRcdDir);
						File rcdFile = rcdFileResult.getRight();
						if (rcdFile != null &&
								verifier.hashMatch(validSigFile, rcdFile)) {
							break;
						} else if (rcdFile != null) {
							log.warn(MARKER, "{}'s Hash doesn't match the Hash contained in valid signature file. Will try to download a rcd file with same timestamp from other nodes and check the Hash.", rcdFile.getPath());
						}
					}
				} else {
					log.info(MARKER, "No valid signature files");
				}
			}
		}
		return validRcdDir;
	}

	Pair<Boolean, File> downloadRcdFile(File sigFile, String validRcdDir) {
		String nodeAccountId = Utility.getAccountIDStringFromFilePath(sigFile.getPath());
		String sigFileName = sigFile.getName();
		String rcdFileName = sigFileName.replace(".rcd_sig", ".rcd");
		String s3ObjectKey = "recordstreams/record" + nodeAccountId + "/" + rcdFileName;
		String localFileName = validRcdDir + rcdFileName;
		return saveToLocal(bucketName, s3ObjectKey, localFileName);
	}

	/**
	 * Because the files are exported from nodes to the repository periodically, the latest signature files of all
	 * nodes might not have the same timestamp in their names. For example, node0.0.3 might have a signature file
	 * 2019-06-06T**22:00**:50.914972Z.rcd_sig in the repository, while node0.0.4's latest signature file in the
	 * repository is 2019-06-06T**21:30**:50.914972Z.rcd_sig because node0.0.4 haven't finished the sync process.
	 * Thus, we need to get the latest signature file for each node, figure out the smallest timestamp t and download signature files with that timestamp from all nodes.
	 * @return
	 */
	private List<File> downloadTheLatestSignatureFilesOfAllNodes() {
		String smallest_LatestSigKey = null;
		for (String nodeAccountId : nodeAccountIds) {
			// Get a list of objects in the bucket, 100 at a time
			ListObjectsRequest listRequest = new ListObjectsRequest()
					.withBucketName(bucketName)
					.withPrefix("record" + nodeAccountId).withMaxKeys(100);
			ObjectListing objects = s3Client.listObjects(listRequest);
			String latestSigKey = null;
			try {
				while(true) {
					List<S3ObjectSummary> summaries = objects.getObjectSummaries();
					for(S3ObjectSummary summary : summaries) {
						String s3ObjectKey = summary.getKey();
						// If the file is not RecordStream Signature file, skip
						if (!Utility.isRecordSigFile(s3ObjectKey)) continue;
						if (latestSigKey == null || s3KeyComparator.compare(latestSigKey, s3ObjectKey) < 0) {
							latestSigKey = s3ObjectKey;
						}
					}
					if(objects.isTruncated()) {
						objects = s3Client.listNextBatchOfObjects(objects);
					}
					else {
						break;
					}
				}
				log.info(MARKER, "Latest RecordStream Signature File on node {} is: {}", nodeAccountId, latestSigKey);
				if (latestSigKey != null) {
					if (smallest_LatestSigKey == null || s3KeyComparator.compare(latestSigKey, smallest_LatestSigKey) < 0) {
						smallest_LatestSigKey = latestSigKey;
					}
				}
			} catch(AmazonServiceException e) {
				// The call was transmitted successfully, but Amazon S3 couldn't process
				// it, so it returned an error response.
	            log.error(MARKER, "Record download failed, Exception: {}", e.getStackTrace());
			} catch(SdkClientException e) {
				// Amazon S3 couldn't be contacted for a response, or the client
				// couldn't parse the response from Amazon S3.
	            log.error(MARKER, "Record download failed, Exception: {}", e.getStackTrace());
			}
		}

		if (smallest_LatestSigKey == null) return null;
		String fileName = Utility.getFileNameFromS3SummaryKey(smallest_LatestSigKey);
		log.info(MARKER, "Will download Latest RecordStream Signature File {} from all nodes", fileName);

		// Download Latest RecordStream Signature Files from all nodes
		List<File> files = new ArrayList<>();
		for (String nodeAccountID : nodeAccountIds) {
			String key = "record" + nodeAccountID + "/" + fileName;
			File file;
			try {
				file = saveToLocal(bucketName, key).getRight();
				if (file != null) {
					files.add(file);
				}
			} catch (IOException e) {
				log.error(MARKER, "IOException: {}", e.getStackTrace());
			}
		}

		return files;
	}

}
