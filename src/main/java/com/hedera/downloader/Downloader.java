package com.hedera.downloader;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.SdkClientException;
import com.amazonaws.retry.PredefinedRetryPolicies;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.google.gson.JsonObject;
import com.hedera.configLoader.ConfigLoader;
import com.hedera.mirrorNodeProxy.Utility;
import com.hedera.signatureVerifier.NodeSignatureVerifier;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public abstract class Downloader {
	protected static final Logger log = LogManager.getLogger("node-log");

	protected static final Marker MARKER = MarkerManager.getMarker("DOWNLOADER");

	protected static ConfigLoader configLoader;

	protected static String bucketName;

	protected static TransferManager xfer_mgr;

	protected static AmazonS3 s3Client;

	protected static NodeSignatureVerifier verifier;

	protected Comparator<String> s3KeyComparator;

	protected static Comparator<String> fileNameComparator;

	protected static Comparator<Path> pathComparator;

	protected List<String> nodeAccountIds;

	protected static ClientConfiguration clientConfiguration;

	enum DownloadType {RCD, BALANCE};


	public Downloader(ConfigLoader myConfigLoader) {
		configLoader = myConfigLoader;
		bucketName = configLoader.getBucketName();

		// Define retryPolicy
		clientConfiguration = new ClientConfiguration();
		clientConfiguration.setRetryPolicy(
				PredefinedRetryPolicies.getDefaultRetryPolicyWithCustomMaxRetries(5));

		verifier = new NodeSignatureVerifier(configLoader);
		nodeAccountIds = loadNodeAccountIDs(configLoader.getNodeInfoFile());

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

	List<String> loadNodeAccountIDs(String fileName) {
		try {
			JsonObject jsonObject = ConfigLoader.getJsonObject(fileName);
			return jsonObject.entrySet()
					.stream()
					.map(i -> i.getKey())
					.collect(Collectors.toCollection(ArrayList::new));
		} catch (IOException ex) {
			log.warn(MARKER, "loadNodeAccountIDs - Fail to load from {}. Exception: {}", fileName, ex.getStackTrace());
		}
		return new ArrayList<>();
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
			default:
				break;
		}
		return result;
	}

	/**
	 *  Download all balance .csv files with timestamp later than lastValidBalanceFileName
	 */
	
	protected void downloadBalanceFiles() throws IOException {
		String s3Prefix = null;
		String fileType = null;
		String lastValidFileName = null;

		s3Prefix = configLoader.getAccountBalanceS3Location();
		fileType = ".csv";
		lastValidFileName = configLoader.getLastValidBalanceFileName();


		for (String nodeAccountId : nodeAccountIds) {
			ArrayList<String> files = new ArrayList<String>();
			log.info(MARKER, "Start downloading {} files of node {}", fileType, nodeAccountId);
			// Get a list of objects in the bucket, 100 at a time
			if (!s3Prefix.endsWith("/")) {
				s3Prefix += "/";
			}
			String prefix = s3Prefix + nodeAccountId + "/";
			int count = 0;
			ListObjectsRequest listRequest = new ListObjectsRequest()
					.withBucketName(bucketName)
					.withPrefix(prefix)
					.withDelimiter("/")
					.withMarker(prefix + lastValidFileName)
					.withMaxKeys(100);
			
			ObjectListing objects = s3Client.listObjects(listRequest);
			try {
				while(true) {
					List<S3ObjectSummary> summaries = objects.getObjectSummaries();
					for(S3ObjectSummary summary : summaries) {
						String s3ObjectKey = summary.getKey();
						if (!s3ObjectKey.contains("latest")) { // ignore latest.csv
							if ((s3ObjectKey.compareTo(prefix + lastValidFileName) > 0) || (lastValidFileName.contentEquals(""))) {
								Pair<Boolean, File> result = saveToLocal(bucketName, s3ObjectKey);
								if (result.getLeft()) {
									count++;
									File file = result.getRight();
									if (file != null) {
										String fileName = file.getName();
										files.add(fileName);
									}
								} else {
									log.error(MARKER, "File {} failed to download from S3", s3ObjectKey);
								}
							}
						}
					}
					if(objects.isTruncated()) {
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
						configLoader.setLastValidBalanceFileName(newLastValidBalanceFileName);
						configLoader.saveToFile();
					}
				}
				
			} catch(AmazonServiceException e) {
				// The call was transmitted successfully, but Amazon S3 couldn't process
				// it, so it returned an error response.
	            log.error(MARKER, "Balance download failed, Exception: {}", e.getStackTrace());
			} catch(SdkClientException e) {
				// Amazon S3 couldn't be contacted for a response, or the client
				// couldn't parse the response from Amazon S3.
	            log.error(MARKER, "Balance download failed, Exception: {}", e.getStackTrace());
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
				s3Prefix = configLoader.getRecordFilesS3Location();
				fileType = ".rcd_sig";
				lastValidFileName = configLoader.getLastValidRcdFileName();
				break;

			case BALANCE:
				s3Prefix = "accountBalances/balance";
				fileType = "_Balances.csv_sig";
				lastValidFileName = configLoader.getLastValidBalanceFileName();
				break;

			default:
				log.error(MARKER, "Invalid DownloadType {}", type);
		}

		HashMap<String, List<File>> sigFilesMap = new HashMap<>();


		for (String nodeAccountId : nodeAccountIds) {
			log.info(MARKER, "Start downloading {} files of node {}", fileType, nodeAccountId);
			// Get a list of objects in the bucket, 100 at a time
			String prefix = s3Prefix + nodeAccountId + "/";
			int count = 0;
			ListObjectsRequest listRequest = new ListObjectsRequest()
					.withBucketName(bucketName)
					.withPrefix(prefix)
					.withDelimiter("/")
					.withMarker(prefix + lastValidFileName)
					.withMaxKeys(100);
			ObjectListing objects = s3Client.listObjects(listRequest);
			try {
				while(true) {
					List<S3ObjectSummary> summaries = objects.getObjectSummaries();
					for(S3ObjectSummary summary : summaries) {
						String s3ObjectKey = summary.getKey();
						if (isNeededSigFile(s3ObjectKey, type) &&
						s3KeyComparator.compare(s3ObjectKey, prefix + lastValidFileName) > 0) {
							Pair<Boolean, File> result = saveToLocal(bucketName, s3ObjectKey);
							if (result.getLeft()) count++;

							File sigFile = result.getRight();
							if (sigFile != null) {
								String fileName = sigFile.getName();
								List<File> files = sigFilesMap.getOrDefault(fileName, new ArrayList<>());
								files.add(sigFile);
								sigFilesMap.put(fileName, files);
							}
						}
					}
					if(objects.isTruncated()) {
						objects = s3Client.listNextBatchOfObjects(objects);
					}
					else {
						break;
					}
				}
				log.info(MARKER, "Downloaded {} {} files of node {}", count, fileType, nodeAccountId);
			} catch(AmazonServiceException e) {
				// The call was transmitted successfully, but Amazon S3 couldn't process
				// it, so it returned an error response.
	            log.error(MARKER, "Balance download failed, Exception: {}", e.getStackTrace());
			} catch(SdkClientException e) {
				// Amazon S3 couldn't be contacted for a response, or the client
				// couldn't parse the response from Amazon S3.
	            log.error(MARKER, "Balance download failed, Exception: {}", e.getStackTrace());
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
	 * @return
	 */
	protected static Pair<Boolean, File> saveToLocal(String bucket_name,
			String s3ObjectKey) throws IOException {
		String filePath = configLoader.getDownloadToDir() + s3ObjectKey;
		return saveToLocal(bucket_name, s3ObjectKey, filePath);
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
                
		if (f.exists()) {
			log.info(MARKER, "File exists: " + localFilepath);
			return Pair.of(false, f);
		}
		try {
            if( ! f.getParentFile().exists() ) {
                f.getParentFile().mkdirs();
            }
            f.createNewFile();
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
			log.error(MARKER, "Download Fails: {}, Exception: {}", s3ObjectKey, ex.getErrorMessage());
		} catch (InterruptedException ex) {
			log.error(MARKER, "Download Fails: {}, Exception: {}", s3ObjectKey, ex.getStackTrace());
		} catch (IOException ex) {
            log.error(MARKER, "Download Fails: {}, Exception: {}", s3ObjectKey, ex.getStackTrace());
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
}
