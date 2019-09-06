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

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.*;
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

import com.google.common.base.Stopwatch;
import com.hedera.configLoader.ConfigLoader;
import com.hedera.configLoader.ConfigLoader.CLOUD_PROVIDER;
import com.hedera.configLoader.ConfigLoader.OPERATION_TYPE;
import com.hedera.databaseUtilities.ApplicationStatus;
import com.hedera.utilities.Utility;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public abstract class Downloader {
	protected final Logger log = LogManager.getLogger(getClass());

	protected static String bucketName;

	protected static TransferManager xfer_mgr;

	protected static AmazonS3 s3Client;

	protected Comparator<String> s3KeyComparator;

	protected static Comparator<String> fileNameComparator;

	protected static Comparator<Path> pathComparator;

	protected List<String> nodeAccountIds;

	protected static ClientConfiguration clientConfiguration;
	
	protected ApplicationStatus applicationStatus;

	String saveFilePath = "";

	public enum DownloadType {RCD, BALANCE, EVENT};

	public Downloader() throws Exception {
		applicationStatus = new ApplicationStatus();
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

		setupCloudConnection();
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
				log.error("Address book file {} empty or unavailable", ConfigLoader.getAddressBookFile());
			}
		} catch (IOException ex) {
			log.error("Failed to load node account IDs from {}", ConfigLoader.getAddressBookFile(), ex);
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
	 * @throws Exception 
	 */

	@Deprecated
	protected void downloadBalanceFiles() throws Exception {
		String s3Prefix = ConfigLoader.getAccountBalanceS3Location();
		String lastValidFileName = applicationStatus.getLastValidDownloadedBalanceFileName();
		saveFilePath = ConfigLoader.getDefaultParseDir(OPERATION_TYPE.BALANCE);

		// refresh node account ids
		nodeAccountIds = loadNodeAccountIDs();

		for (String nodeAccountId : nodeAccountIds) {
			Stopwatch stopwatch = Stopwatch.createStarted();
			if (Utility.checkStopFile()) {
				log.warn("Stop file found, stopping");
				break;
			}
			ArrayList<String> files = new ArrayList<String>();
			log.debug("Downloading balance files for node {} created after file {}", nodeAccountId, lastValidFileName);
			// Get a list of objects in the bucket, 100 at a time
			if (!s3Prefix.endsWith("/")) {
				s3Prefix += "/";
			}
			String prefix = s3Prefix + nodeAccountId + "/";
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
						log.warn("Stop file found, stopping");
						break;
					}
					List<S3ObjectSummary> summaries = objects.getObjectSummaries();
					for(S3ObjectSummary summary : summaries) {
						if (Utility.checkStopFile()) {
							log.warn("Stop file found, stopping");
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
									File file = result.getRight();
									if (file != null) {
										// move the file to the valid directory
								    File fTo = new File(file.getAbsolutePath().replace("/tmp/", "/valid/"));
								    if (moveFile(file, fTo)) {
											files.add(file.getName());
										}
									}
								} else if (result.getRight() == null) {
									log.error("Failed to download key {} from cloud", s3ObjectKey);
								}
							}
						}
					}
					if (Utility.checkStopFile()) {
						log.warn("Stop file found, stopping");
						break;
					} else if (objects.isTruncated()) {
						objects = s3Client.listNextBatchOfObjects(objects);
					}
					else {
						break;
					}
				}

				if (files.size() != 0) {
					String newLastValidBalanceFileName = lastValidFileName;

					Collections.sort(files);

					if (newLastValidBalanceFileName.isEmpty() ||
							newLastValidBalanceFileName.compareTo(files.get(files.size()-1)) < 0) {
						newLastValidBalanceFileName = files.get(files.size()-1);
					}

					if (!newLastValidBalanceFileName.equals(lastValidFileName)) {
						applicationStatus.updateLastValidDownloadedBalanceFileName(newLastValidBalanceFileName);
					}
				}

				log.info("Downloaded {} balance files for node {} in {}", files.size(), nodeAccountId, stopwatch);
			} catch(Exception e) {
				log.error("Error downloading balance files for node {} after {}", nodeAccountId, stopwatch, e);
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
	 * @throws Exception 
	 */
	protected HashMap<String, List<File>> downloadSigFiles(DownloadType type) throws Exception {
		String s3Prefix = null;
		String lastValidFileName = null;
		switch (type) {
			case RCD:
				s3Prefix = ConfigLoader.getRecordFilesS3Location();
				lastValidFileName = applicationStatus.getLastValidDownloadedRecordFileName();

				saveFilePath = ConfigLoader.getDownloadToDir(OPERATION_TYPE.RECORDS);
				break;

			case BALANCE:
				s3Prefix = "accountBalances/balance";
				lastValidFileName = applicationStatus.getLastValidDownloadedBalanceFileName();
				saveFilePath = ConfigLoader.getDownloadToDir(OPERATION_TYPE.BALANCE);
				break;

			case EVENT:
				s3Prefix = ConfigLoader.getEventFilesS3Location();
				lastValidFileName = applicationStatus.getLastValidDownloadedEventFileName();
				saveFilePath = ConfigLoader.getDownloadToDir(OPERATION_TYPE.EVENTS);
				break;

			default:
				throw new UnsupportedOperationException("Invalid DownloadType " + type);
		}

		HashMap<String, List<File>> sigFilesMap = new HashMap<>();

		// refresh node account ids
		nodeAccountIds = loadNodeAccountIDs();
		for (String nodeAccountId : nodeAccountIds) {
			if (Utility.checkStopFile()) {
				log.info("Stop file found, stopping");
				break;
			}
			log.debug("Downloading {} signature files for node {} created after file {}", type, nodeAccountId, lastValidFileName);
			// Get a list of objects in the bucket, 100 at a time
			String prefix = s3Prefix + nodeAccountId + "/";
			int count = 0;
			int downloadCount = 0;
			int downloadMax = ConfigLoader.getMaxDownloadItems();
			Stopwatch stopwatch = Stopwatch.createStarted();

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
						log.info("Stop file found, stopping");
						break;
					}
					List<S3ObjectSummary> summaries = objects.getObjectSummaries();
					for(S3ObjectSummary summary : summaries) {
						if (Utility.checkStopFile()) {
							log.info("Stop file found, stopping");
							break;
						} else if (downloadCount >= downloadMax) {
							break;
						}

						String s3ObjectKey = summary.getKey();

						if (isNeededSigFile(s3ObjectKey, type) && (s3KeyComparator.compare(s3ObjectKey, prefix + lastValidFileName) > 0 || lastValidFileName.isEmpty())) {
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
						log.info("Stop file found, stopping");
						break;
					} else if (downloadCount >= downloadMax) {
						break;
					} else if (objects.isTruncated()) {
						objects = s3Client.listNextBatchOfObjects(objects);
					} else {
						break;
					}
				}
				log.info("Downloaded {} {} signatures for node {} in {}", count, type, nodeAccountId, stopwatch);
			} catch (Exception e) {
				log.error("Error downloading {} signature files for node {} after {}", type, nodeAccountId, stopwatch, e);
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
	protected Pair<Boolean, File> saveToLocal(String bucket_name, String s3ObjectKey, String localFilepath)  {
		// ensure filePaths have OS specific separator
		localFilepath = localFilepath.replace("/", "~");
		localFilepath = localFilepath.replace("\\", "~");
		localFilepath = localFilepath.replace("~", File.separator);

		File f = new File(localFilepath).getAbsoluteFile();
		Stopwatch stopwatch = Stopwatch.createStarted();

		try {
			Download download = xfer_mgr.download(bucket_name, s3ObjectKey, f);
			download.waitForCompletion();
			if (download.isDone()) {
				log.debug("Finished downloading {} in {}", s3ObjectKey, stopwatch);
				return Pair.of(true, f);
			} else {
				log.error("Failed downloading {} after {}", s3ObjectKey, stopwatch);
				return Pair.of(false, null);
			}
		} catch (Exception ex) {
			log.error("Failed downloading {} after {}", s3ObjectKey, stopwatch, ex);
		}
		return Pair.of(false, null);
	}

	void shutdownTransferManager() {
		log.info("Shutting down");
		if (xfer_mgr != null) {
			xfer_mgr.shutdownNow();
		}
	}

	protected static void setupCloudConnection() {
		if (ConfigLoader.getCloudProvider() == CLOUD_PROVIDER.S3) {
			if (ConfigLoader.getAccessKey().contentEquals("")) {
				s3Client = AmazonS3ClientBuilder.standard()
						.withRegion(ConfigLoader.getClientRegion())
						.withClientConfiguration(clientConfiguration)
						.withCredentials(new AWSStaticCredentialsProvider(new AnonymousAWSCredentials()))
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
		} else if (ConfigLoader.getCloudProvider() == CLOUD_PROVIDER.GCP) {
			if (ConfigLoader.getAccessKey().contentEquals("")) {
				s3Client = AmazonS3ClientBuilder.standard()
						.withEndpointConfiguration(
								new AwsClientBuilder.EndpointConfiguration(
										"https://storage.googleapis.com", ConfigLoader.getClientRegion()))
						.withClientConfiguration(clientConfiguration)
						.withCredentials(new AWSStaticCredentialsProvider(new AnonymousAWSCredentials()))
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
		} else {
			s3Client = AmazonS3ClientBuilder.standard()
					.withPathStyleAccessEnabled(true)
					.withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://localhost:8001", ConfigLoader.getClientRegion()))
					.withClientConfiguration(clientConfiguration)
					.withCredentials(new AWSStaticCredentialsProvider(new AnonymousAWSCredentials()))
					.build();
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
			log.error("File move from {} to {} failed", sourceFile.getAbsolutePath(), destinationFile.getAbsolutePath(), e);
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
