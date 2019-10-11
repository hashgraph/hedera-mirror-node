package com.hedera.configLoader;

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

import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import com.hedera.utilities.Utility;
import io.github.cdimascio.dotenv.Dotenv;

import lombok.*;
import lombok.extern.log4j.Log4j2;

import java.io.*;

@Log4j2
public class ConfigLoader {

	@Getter
	@RequiredArgsConstructor
	public static enum CLOUD_PROVIDER {
		S3("https://s3.amazonaws.com"),
		GCP("https://storage.googleapis.com"),
		LOCAL("http://127.0.0.1:8001"); // Testing

		private final String endpoint;
	}

	// cloud provider, must be either S3 or GCP
	private static CLOUD_PROVIDER cloudProvider = CLOUD_PROVIDER.S3;

	// clientRegion of the S3 bucket from which we download RecordStream files;
	private static String clientRegion = "us-east-2";

	// name of the S3 bucket from which we download RecordStream files;
	private static String bucketName = "hedera-export";

	// AWS_ACCESS_KEY_ID
	private static String accessKey = null;

	// AWS_SECRET_ACCESS_KEY
	private static String secretKey = null;

	// the directory where we store the RecordStream files
	private static String downloadToDir = "./MirrorNodeData";

	// path of addressBook file
	private static String addressBookFile = "./config/0.0.102";

	private static final int DEFAULT_ACCOUNT_BALANCES_INSERT_BATCH_SIZE = 2000;
	private static int accountBalancesInsertBatchSize = DEFAULT_ACCOUNT_BALANCES_INSERT_BATCH_SIZE;

	private static final int DEFAULT_ACCOUNT_BALANCES_FILE_BUFFER_SIZE = 200_000;
	private static int accountBalancesFileBufferSize = DEFAULT_ACCOUNT_BALANCES_FILE_BUFFER_SIZE;

	// location of account balances on S3
	private static String accountBalanceS3Location = "accountBalances/balance";

	//location of record files on S3
	private static String recordFilesS3Location = "recordstreams/record";

	//location of eventStream files on S3
	private static String eventFilesS3Location = "eventsStreams/events_";

	private static final long DEFAULT_SYSTEM_SHARD_NUM = 0L;
	private static long systemShardNum = DEFAULT_SYSTEM_SHARD_NUM;

	private static boolean persistClaims = false;

	private static String persistFiles = "NONE";

	private static boolean persistContracts = false;

	private static boolean persistCryptoTransferAmounts = false;

    private static Dotenv dotEnv = Dotenv.configure().ignoreIfMissing().load();

	private static String configSavePath = "./config/config.json";

	private static JsonObject configJsonObject;

	public static enum OPERATION_TYPE {
               BALANCE
               ,RECORDS
               ,EVENTS
	}

	// TODO: Replace this hack with Spring Boot properties
	static {
		String configPath = dotEnv.get("HEDERA_MIRROR_CONFIG_PATH", configSavePath);
		File configFile = new File(configPath);
		if (configFile.exists()) {
			load(configPath);
		} else {
			configFile = Utility.getResource("config.json");
			load(configFile.getAbsolutePath());
		}
	}

	private static void load(String configSavePath) {
		log.info("Loading configuration from {}", configSavePath);
		try {

			configJsonObject = getJsonObject(configSavePath);

			if (configJsonObject.has("cloud-provider")) {
				String provider = configJsonObject.get("cloud-provider").getAsString();
				cloudProvider = CLOUD_PROVIDER.valueOf(provider);
			}
			if (configJsonObject.has("clientRegion")) {
				clientRegion = configJsonObject.get("clientRegion").getAsString();
			}
			if (configJsonObject.has("bucketName")) {
				bucketName = configJsonObject.get("bucketName").getAsString();
			}

			accessKey = dotEnv.get("HEDERA_S3_ACCESS_KEY");
			if (accessKey == null) {
				if (configJsonObject.has("accessKey")) {
					accessKey = configJsonObject.get("accessKey").getAsString();
				}
			}
			secretKey = dotEnv.get("HEDERA_S3_SECRET_KEY");
			if (secretKey == null) {
				if (configJsonObject.has("secretKey")) {
					secretKey = configJsonObject.get("secretKey").getAsString();
				}
			}
			if (configJsonObject.has("downloadToDir")) {
				downloadToDir = configJsonObject.get("downloadToDir").getAsString();
			}
			if (configJsonObject.has("addressBookFile")) {
				addressBookFile = configJsonObject.get("addressBookFile").getAsString();
			}
			if (configJsonObject.has("accountBalancesInsertBatchSize")) {
				var i = configJsonObject.get("accountBalancesInsertBatchSize").getAsInt();
				if (i > 0) {
					accountBalancesInsertBatchSize = i;
				}
			}
			if (configJsonObject.has("accountBalancesFileBufferSize")) {
				var i = configJsonObject.get("accountBalancesFileBufferSize").getAsInt();
				if (i > 0) {
					accountBalancesFileBufferSize = i;
				}
			}
			if (configJsonObject.has("systemShardNum")) {
				systemShardNum = configJsonObject.get("systemShardNum").getAsLong();
			}
			if (configJsonObject.has("accountBalancesS3Location")) {
				accountBalanceS3Location = configJsonObject.get("accountBalancesS3Location").getAsString();
			}
			if (configJsonObject.has("recordFilesS3Location")) {
				recordFilesS3Location = configJsonObject.get("recordFilesS3Location").getAsString();
			}
			if (configJsonObject.has("eventFilesS3Location")) {
				eventFilesS3Location = configJsonObject.get("eventFilesS3Location").getAsString();
			}
			if (configJsonObject.has("persistClaims")) {
				persistClaims = configJsonObject.get("persistClaims").getAsBoolean();
			}
			if (configJsonObject.has("persistFiles")) {
				persistFiles = configJsonObject.get("persistFiles").getAsString();
			}
			if (configJsonObject.has("persistContracts")) {
				persistContracts = configJsonObject.get("persistContracts").getAsBoolean();
			}
			if (configJsonObject.has("persistCryptoTransferAmounts")) {
				persistCryptoTransferAmounts = configJsonObject.get("persistCryptoTransferAmounts").getAsBoolean();
			}
		} catch (FileNotFoundException ex) {
			log.error("Cannot load configuration from {}", configSavePath, ex);
		}
	}

	public static CLOUD_PROVIDER getCloudProvider() {
		return cloudProvider;
	}
	public static String getClientRegion() {
		return clientRegion;
	}

	public static String getBucketName() {
		return bucketName;
	}

	public static String getAccessKey() {
		if (accessKey == null) {
			return "";
		} else {
			return accessKey;
		}
	}

	public static String getSecretKey() {
		if (secretKey == null) {
			return "";
		} else {
			return secretKey;
		}
	}

	public static String getDownloadToDir() {
		if (!downloadToDir.endsWith("/")) {
			downloadToDir += "/";
		}
		return downloadToDir;
	}

	public static String getDefaultParseDir(OPERATION_TYPE operation) {
		if (!downloadToDir.endsWith("/")) {
			downloadToDir += "/";
		}

		// always return trailing "/"
		switch (operation) {
		case BALANCE:
			return downloadToDir + "accountBalances/valid/";
		case EVENTS:
			String eventFileLoc = eventFilesS3Location.replace("/events_", "");
			eventFileLoc = eventFileLoc.replace("/", "");
			return downloadToDir + eventFileLoc + "/valid";
		case RECORDS:
			String recordFileLoc = recordFilesS3Location.replace("/record", "");
			recordFileLoc = recordFileLoc.replace("/", "");
			return downloadToDir + recordFileLoc + "/valid";
		}
		return "";
	}

	public static String getDefaultTmpDir(OPERATION_TYPE operation) {
		return getDefaultParseDir(operation).replace("/valid", "/tmp");
	}

	public static void setDownloadToDir(String downloadToDir) {
		ConfigLoader.downloadToDir = downloadToDir;
	}

	public static String getAddressBookFile() {
		return addressBookFile;
	}

	public static void setAddressBookFile(String newAddressBookFile) {
		addressBookFile = newAddressBookFile;
	}

	public static int getAccountBalancesInsertBatchSize() {
		return accountBalancesInsertBatchSize;
	}

	public static void setCloudProvider(CLOUD_PROVIDER cloudProvider) {
		ConfigLoader.cloudProvider = cloudProvider;
	}

	public static int getAccountBalancesFileBufferSize() {
		return accountBalancesFileBufferSize;
	}

	public static String getAccountBalanceS3Location() {
		return accountBalanceS3Location;
	}

	public static long getSystemShardNum() {
		return systemShardNum;
	}

	public static String getRecordFilesS3Location() {
		return recordFilesS3Location;
	}

	public static String getEventFilesS3Location() {
		return eventFilesS3Location;
	}

	public static boolean getPersistClaims() {
		return persistClaims;
	}
	public static void setPersistClaims(boolean persist) {
		persistClaims = persist;
	}
	public static String getPersistFiles() {
		return persistFiles;
	}
	public static void setPersistFiles(String persist) {
		persistFiles = persist;
	}
	public static boolean getPersistContracts() {
		return persistContracts;
	}
	public static void setPersistContracts(boolean persist) {
		persistContracts = persist;
	}
	public static boolean getPersistCryptoTransferAmounts() {
		return persistCryptoTransferAmounts;
	}
	public static void setPersistCryptoTransferAmounts(boolean persist) {
		persistCryptoTransferAmounts = persist;
	}

	/***
	 *
	 * Reads a file into a Json object.
	 *
	 * @param location of the file
	 * @return a Json object with the contents of the file
	 * @throws JsonIOException if there are problems reading the file
	 * @throws JsonSyntaxException if the file does not represent a Json object
	 * @throws FileNotFoundException if the file doesn't exist
	 */
	public static JsonObject getJsonObject(
			final String location) throws JsonIOException, JsonSyntaxException, FileNotFoundException {

		final JsonParser parser = new JsonParser();

		// Read file into object
		final FileReader file = new FileReader(location);
		return (JsonObject) parser.parse(file);
	}
}
