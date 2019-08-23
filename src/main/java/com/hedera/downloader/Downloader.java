package com.hedera.downloader;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.retry.PredefinedRetryPolicies;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.hedera.configLoader.ConfigLoader;
import com.hedera.configLoader.ConfigLoader.CLOUD_PROVIDER;
import com.hedera.configLoader.ConfigLoader.OPERATION_TYPE;
import com.hedera.utilities.Utility;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public abstract class Downloader {
	protected static final Logger log = LogManager.getLogger("downloader");

	protected static final Marker MARKER = MarkerManager.getMarker("DOWNLOADER");

	protected static String bucketName;

	protected static TransferManager xfer_mgr;

	protected static AmazonS3 s3Client;

	protected Comparator<String> s3KeyComparator;

	protected static Comparator<String> fileNameComparator;

	protected static Comparator<Path> pathComparator;

	protected List<String> nodeAccountIds;

	protected static ClientConfiguration clientConfiguration;

	String saveFilePath = "";

	public enum DownloadType {RCD, BALANCE, EVENT};

	public Downloader() {
		bucketName = ConfigLoader.getBucketName();

		// Define retryPolicy
		clientConfiguration = new ClientConfiguration();
		clientConfiguration.setRetryPolicy(
				PredefinedRetryPolicies.getDefaultRetryPolicyWithCustomMaxRetries(5));

		nodeAccountIds = loadNodeAccountIDs();

		s3KeyComparator = new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				Instant o1TimeStamp = Utility.parseToInstant(Utility.parseS3SummaryKey(o1).getMiddle());
				Instant o2TimeStamp = Utility.parseToInstant(Utility.parseS3SummaryKey(o2).getMiddle());
				if (o1TimeStamp == null) return -1;
				if (o2TimeStamp == null) return 1;
				return o1TimeStamp.compareTo(o2TimeStamp);
			}
		};

		fileNameComparator = new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				Instant o1TimeStamp = Utility.getInstantFromFileName(o1);
				Instant o2TimeStamp = Utility.getInstantFromFileName(o2);
				return o1TimeStamp.compareTo(o2TimeStamp);
			}
		};

		pathComparator = new Comparator<Path>() {
			@Override
			public int compare(Path p1, Path p2) {
				return p1.toString().compareTo(p2.toString());
			}
		};

		Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownTransferManager));
	}

	List<String> loadNodeAccountIDs() {
		List<String> nodes = new ArrayList<String>();
		try {
			byte[] addressBookBytes = Utility.getBytes(ConfigLoader.getAddressBookFile());
			if (addressBookBytes != null) {
				NodeAddressBook nodeAddressBook = NodeAddressBook.parseFrom(addressBookBytes);
				for (NodeAddress address : nodeAddressBook.getNodeAddressList()) {
					nodes.add(address.getMemo().toStringUtf8());
				}
			} else {
				log.error(MARKER, "Address book file {}, empty or unavailable", ConfigLoader.getAddressBookFile());
			}
		} catch (IOException ex) {
			log.warn(MARKER, "loadNodeAccountIDs - Fail to load from {}. Exception: {}", ConfigLoader.getAddressBookFile(), ex);
		}
		return nodes;
	}

	protected boolean isNeededSigFile(String s3ObjectKey, DownloadType type) {
		boolean result = false;
		switch (type) {
			case BALANCE:
				result = Utility.isBalanceSigFile(s3ObjectKey);
				break;
			case RCD:
				result = Utility.isRecordSigFile(s3ObjectKey);
				break;
			case EVENT:
				result = Utility.isEventStreamSigFile(s3ObjectKey);
				break;
			default:
				break;
		}
		return result;
	}

	/**
	 *  Download all balance .csv files with timestamp later than lastValidBalanceFileName
	 */

	@Deprecated
	protected void downloadBalanceFiles() throws IOException {
		String s3Prefix = null;
		String fileType = null;
		String lastValidFileName = null;
		
		s3Prefix = ConfigLoader.getAccountBalanceS3Location();
		fileType = ".csv";
		lastValidFileName = ConfigLoader.getLastValidBalanceFileName();
		saveFilePath = ConfigLoader.getDefaultTmpDir(OPERATION_TYPE.BALANCE);
		
		// refresh node account ids
		nodeAccountIds = loadNodeAccountIDs();

		for (String nodeAccountId : nodeAccountIds) {
			if (Utility.checkStopFile()) {
				log.info(MARKER, "Stop file found, stopping.");
				break;
			}
			ArrayList<String> files = new ArrayList<String>();
			log.info(MARKER, "Start downloading {} files of node {}", fileType, nodeAccountId);
			// Get a list of objects in the bucket, 100 at a time
			if (!s3Prefix.endsWith("/")) {
				s3Prefix += "/";
			}
			String prefix = s3Prefix + nodeAccountId + "/";
			int count = 0;
			int downloadCount = 0;
			int maxDownloadCount = ConfigLoader.getMaxDownloadItems();

			ListObjectsRequest listRequest = new ListObjectsRequest()
					.withBucketName(bucketName)
					.withPrefix(prefix)
					.withDelimiter("/")
					.withMarker(prefix + lastValidFileName)
					.withMaxKeys(100);

			ObjectListing objects = s3Client.listObjects(listRequest);
			try {
				while(downloadCount <= maxDownloadCount) {
					if (Utility.checkStopFile()) {
						log.info(MARKER, "Stop file found, stopping.");
						break;
					}
					List<S3ObjectSummary> summaries = objects.getObjectSummaries();
					for(S3ObjectSummary summary : summaries) {
						if (Utility.checkStopFile()) {
							log.info(MARKER, "Stop file found, stopping.");
							break;
						}
						if (downloadCount > maxDownloadCount) {
							break;
						}
						String s3ObjectKey = summary.getKey();
						if (!s3ObjectKey.contains("latest")) { // ignore latest.csv
							if ((s3ObjectKey.compareTo(prefix + lastValidFileName) > 0) || (lastValidFileName.contentEquals(""))) {
								Pair<Boolean, File> result = saveToLocal(bucketName, s3ObjectKey, saveFilePath + "/" + s3ObjectKey.replace("accountBalances/balance/0.0.3/", ""));
								if (result == null) {
									return;
								}
								if (maxDownloadCount != 0) downloadCount++;

								if (result.getLeft()) {
									count++;
									File file = result.getRight();
									if (file != null) {
										// move the file to the valid directory
								        File fTo = new File(file.getAbsolutePath().replace("/tmp/", "/valid/"));
								        if (moveFile(file, fTo)) {
											files.add(file.getName());
										}
									}
								} else if (result.getRight() == null) {
									log.error(MARKER, "File {} failed to download from cloud", s3ObjectKey);
								}
							}
						}
					}
					if (Utility.checkStopFile()) {
						log.info(MARKER, "Stop file found, stopping.");
						break;
					} else if (objects.isTruncated()) {
						objects = s3Client.listNextBatchOfObjects(objects);
					}
					else {
						break;
					}
				}
				log.info(MARKER, "Downloaded {} {} files of node {}", count, fileType, nodeAccountId);

				if (files.size() != 0) {
					String newLastValidBalanceFileName = lastValidFileName;

					Collections.sort(files);

					if (newLastValidBalanceFileName.isEmpty() ||
							newLastValidBalanceFileName.compareTo(files.get(files.size()-1)) < 0) {
						newLastValidBalanceFileName = files.get(files.size()-1);
					}

					if (!newLastValidBalanceFileName.equals(lastValidFileName)) {
						ConfigLoader.setLastValidBalanceFileName(newLastValidBalanceFileName);
						ConfigLoader.saveToFile();
					}
				}

			} catch(AmazonServiceException e) {
				// The call was transmitted successfully, but Amazon S3 couldn't process
				// it, so it returned an error response.
	            log.error(MARKER, "Balance download failed, Exception: {}", e);
			} catch(SdkClientException e) {
				// Amazon S3 couldn't be contacted for a response, or the client
				// couldn't parse the response from Amazon S3.
	            log.error(MARKER, "Balance download failed, Exception: {}", e);
			}
		}
	}

	/**
	 *  If type is DownloadType.RCD:
	 * 		Download all .rcd_sig files with timestamp later than lastValidRcdFileName
	 * 		Validate each .rcd_sig file with corresponding node's PubKey
	 * 		Put valid .rcd_sig into HashMap<String, List<File>>
	 *
	 *  If type is DownloadType.BALANCE:
	 * 		Download all _Balances.csv_sig files with timestamp later than lastValidBalanceFileName
	 * 		Validate each _Balances.csv_sig file with corresponding node's PubKey
	 * 		Put valid _Balances.csv_sig into HashMap<String, List<File>>
	 *
	 * 	@return
	 * 	If type is DownloadType.RCD:
	 * 		key: .rcd_sig file name
	 * 		value: a list of .rcd_sig files with the same name and from different nodes folder;
	 *
	 *  If type is DownloadType.BALANCE:
	 * 		key: _Balances.csv_sig file name
	 * 		value: a list of _Balances.csv_sig files with the same name and from different nodes folder;
	 */
	protected HashMap<String, List<File>> downloadSigFiles(DownloadType type) throws IOException {
		String s3Prefix = null;
		String fileType = null;
		String lastValidFileName = null;
		switch (type) {
			case RCD:
				s3Prefix = ConfigLoader.getRecordFilesS3Location();
				fileType = ".rcd_sig";
				lastValidFileName = ConfigLoader.getLastValidRcdFileName();
				
				saveFilePath = ConfigLoader.getDownloadToDir(OPERATION_TYPE.RECORDS);
				break;

			case BALANCE:
				s3Prefix = "accountBalances/balance";
				fileType = "_Balances.csv_sig";
				lastValidFileName = ConfigLoader.getLastValidBalanceFileName();
				saveFilePath = ConfigLoader.getDownloadToDir(OPERATION_TYPE.BALANCE);
				break;

			case EVENT:
				s3Prefix = ConfigLoader.getEventFilesS3Location();
				fileType = ".evts_sig";
				lastValidFileName = ConfigLoader.getLastValidEventFileName();
				saveFilePath = ConfigLoader.getDownloadToDir(OPERATION_TYPE.EVENTS);
				break;

			default:
				log.error(MARKER, "Invalid DownloadType {}", type);
		}

		HashMap<String, List<File>> sigFilesMap = new HashMap<>();

		// refresh node account ids
		nodeAccountIds = loadNodeAccountIDs();
		for (String nodeAccountId : nodeAccountIds) {
			if (Utility.checkStopFile()) {
				log.info(MARKER, "Stop file found, stopping.");
				break;
			}
			log.info(MARKER, "Start downloading {} files of node {}", fileType, nodeAccountId);
			// Get a list of objects in the bucket, 100 at a time
			String prefix = s3Prefix + nodeAccountId + "/";
			int count = 0;
			int downloadCount = 0;
			int downloadMax = ConfigLoader.getMaxDownloadItems();
			
			ListObjectsRequest listRequest = new ListObjectsRequest()
					.withBucketName(bucketName)
					.withPrefix(prefix)
					.withDelimiter("/")
					.withMarker(prefix + lastValidFileName)
					.withMaxKeys(100);
			ObjectListing objects = s3Client.listObjects(listRequest);
			try {
				while(downloadCount <= downloadMax) {
					if (Utility.checkStopFile()) {
						log.info(MARKER, "Stop file found, stopping.");
						break;
					}
					List<S3ObjectSummary> summaries = objects.getObjectSummaries();
					for(S3ObjectSummary summary : summaries) {
						if (Utility.checkStopFile()) {
							log.info(MARKER, "Stop file found, stopping.");
							break;
						} else if (downloadCount >= downloadMax) {
							break;
						}

						String s3ObjectKey = summary.getKey();
						if (isNeededSigFile(s3ObjectKey, type) &&
						s3KeyComparator.compare(s3ObjectKey, prefix + lastValidFileName) > 0) {
							String saveTarget = saveFilePath + s3ObjectKey;
							Pair<Boolean, File> result = saveToLocal(bucketName, s3ObjectKey, saveTarget);
							if (result.getLeft()) count++;
							if (downloadMax != 0) downloadCount++;

							File sigFile = result.getRight();
							if (sigFile != null) {
								String fileName = sigFile.getName();
								List<File> files = sigFilesMap.getOrDefault(fileName, new ArrayList<>());
								files.add(sigFile);
								sigFilesMap.put(fileName, files);
							}
						}
					}
					if (Utility.checkStopFile()) {
						log.info(MARKER, "Stop file found, stopping.");
						break;
					} else if (downloadCount >= downloadMax) {
						break;
					} else if (objects.isTruncated()) {
						objects = s3Client.listNextBatchOfObjects(objects);
					} else {
						break;
					}
				}
				log.info(MARKER, "Downloaded {} {} files of node {}", count, fileType, nodeAccountId);
			} catch(AmazonServiceException e) {
				// The call was transmitted successfully, but Amazon S3 couldn't process
				// it, so it returned an error response.
	            log.error(MARKER, "Signatures download failed, Exception: {}", e);
			} catch(SdkClientException e) {
				// Amazon S3 couldn't be contacted for a response, or the client
				// couldn't parse the response from Amazon S3.
	            log.error(MARKER, "Signatures download failed, Exception: {}", e);
			}
		}

		return sigFilesMap;
	}

	/**
	 * return a pair of download result:
	 * boolean: download it or not.
	 * True means we download it successfully; False means it already exists or we fail to download it;
	 * File is the local file
	 * @param bucket_name
	 * @param s3ObjectKey
	 * @param localFilepath
	 * @return
	 */
	protected static Pair<Boolean, File> saveToLocal(String bucket_name,
		String s3ObjectKey, String localFilepath)  {
		
		// ensure filePaths have OS specific separator
		localFilepath = localFilepath.replace("/", "~");
		localFilepath = localFilepath.replace("\\", "~");
		localFilepath = localFilepath.replace("~", File.separator);

        File f = new File(localFilepath).getAbsoluteFile();

		try {
			Download download = xfer_mgr.download(bucket_name, s3ObjectKey, f);
			download.waitForCompletion();
			if (download.isDone()) {
				log.info(MARKER, "Finished downloading " + s3ObjectKey);
				return Pair.of(true, f);
			} else {
				log.error(MARKER, "Download Fails: " + s3ObjectKey);
				return Pair.of(false, null);
			}
		} catch (AmazonServiceException ex) {
			log.error(MARKER, "Download Failed: {}, Exception: {}", s3ObjectKey, ex);
		} catch (InterruptedException ex) {
			log.error(MARKER, "Download Failed: {}, Exception: {}", s3ObjectKey, ex);
		}
		return Pair.of(false, null);
	}

	void shutdownTransferManager() {
		log.info(MARKER, "RecordFileDownloader SHUTTING DOWN .");
		xfer_mgr.shutdownNow();
	}

	protected static void printContent(InputStream input) throws IOException {
		// Read the text input stream one line at a time and display each line.
		BufferedReader reader = new BufferedReader(new InputStreamReader(input));
		String line;
		while ((line = reader.readLine()) != null) {
			System.out.println(line);
		}
		System.out.println();
		input.close();
	}

	protected static void setupCloudConnection() {
		if (ConfigLoader.getCloudProvider() == CLOUD_PROVIDER.S3) {
			if (ConfigLoader.getAccessKey().contentEquals("")) {
				s3Client = AmazonS3ClientBuilder.standard()
						.withRegion(ConfigLoader.getClientRegion())
						.withClientConfiguration(clientConfiguration)
						.build();
			} else {
				s3Client = AmazonS3ClientBuilder.standard()
						.withCredentials(new AWSStaticCredentialsProvider(
								new BasicAWSCredentials(ConfigLoader.getAccessKey(),
										ConfigLoader.getSecretKey())))
						.withRegion(ConfigLoader.getClientRegion())
						.withClientConfiguration(clientConfiguration)
						.build();
			}
		} else {
			if (ConfigLoader.getAccessKey().contentEquals("")) {
				s3Client = AmazonS3ClientBuilder.standard()
						.withEndpointConfiguration(
				                new AwsClientBuilder.EndpointConfiguration(
				                    "https://storage.googleapis.com", ConfigLoader.getClientRegion()))
						.withClientConfiguration(clientConfiguration)
						.build();
			} else {
				s3Client = AmazonS3ClientBuilder.standard()
						.withEndpointConfiguration(
				                new AwsClientBuilder.EndpointConfiguration(
				                    "https://storage.googleapis.com", ConfigLoader.getClientRegion()))
						.withCredentials(new AWSStaticCredentialsProvider(
								new BasicAWSCredentials(ConfigLoader.getAccessKey(),
										ConfigLoader.getSecretKey())))
						.withClientConfiguration(clientConfiguration)
						.build();
			}
		}
		xfer_mgr = TransferManagerBuilder.standard()
				.withS3Client(s3Client).build();
	}

	/**
	 * Moves a file from one location to another
	 * boolean: true if file moved successfully
	 * Note: The method doesn't check if source file or destination directory exist to avoid 
	 * repeated checks that could hurt performance 
	 * @param sourceFile
	 * @param destinationFile
	 * @return boolean
	 */
	protected boolean moveFile(File sourceFile, File destinationFile) {
        try {
        	// not checking if file exists to help with performance
        	// assumption is caller has created the destination file folder
        	Files.move(sourceFile.toPath(), destinationFile.toPath(), REPLACE_EXISTING);
        	return true;
        } catch (IOException e) {
			log.error(MARKER, "File Move from {} to {} Failed: {}, Exception: {}", sourceFile.getAbsolutePath(), destinationFile.getAbsolutePath(), e);
			return false;
       }
    }

	protected Pair<Boolean, File> downloadFile(DownloadType downloadType, File sigFile, String targetDir) {
		String fileName = "";
		String s3Prefix = "";
		
		String nodeAccountId = Utility.getAccountIDStringFromFilePath(sigFile.getPath());
		String sigFileName = sigFile.getName();
		
		switch (downloadType) {
			case BALANCE:
				fileName = sigFileName.replace("_Balances.csv_sig", "_Balances.csv");
				s3Prefix = ConfigLoader.getAccountBalanceS3Location();
				break;
			case EVENT:
				fileName = sigFileName.replace(".evts_sig", ".evts");
				s3Prefix = ConfigLoader.getEventFilesS3Location();
				break;
			case RCD:
				fileName = sigFileName.replace(".rcd_sig", ".rcd");
				s3Prefix =  ConfigLoader.getRecordFilesS3Location();
				break;
		}
		String s3ObjectKey = s3Prefix + nodeAccountId + "/" + fileName;
		
		String localFileName = targetDir + "/" + fileName;
		return saveToLocal(bucketName, s3ObjectKey, localFileName);
	}
}
