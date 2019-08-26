package com.hedera.configLoader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.hedera.databaseUtilities.ApplicationStatus;
import com.hedera.databaseUtilities.ApplicationStatus.ApplicationStatusCode;

import io.github.cdimascio.dotenv.Dotenv;

import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

@Log4j2
public class ConfigLoader {

	public static enum CLOUD_PROVIDER {
		S3
		,GCP
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

	// the port of mirrorNodeProxy;
	private static int proxyPort = 50777;

	// path of addressBook file
	private static String addressBookFile = "./config/0.0.102";

	// file name of last valid rcd file
	private static String lastValidRcdFileName = "";

	// Hash of last last valid rcd file
	private static String lastValidRcdFileHash = "";

	// file name of last valid evts file
	private static String lastValidEventFileName = "";

	// Hash of last last valid evts file
	private static String lastValidEventFileHash = "";

	// file name of last valid account balance file
	private static String lastValidBalanceFileName = "";

	// location of account balances on S3
	private static String accountBalanceS3Location = "accountBalances/balance";

	//location of record files on S3
	private static String recordFilesS3Location = "recordstreams/record";

	//location of eventStream files on S3
	private static String eventFilesS3Location = "eventstreams/events_";

	private static String stopLoggingIfRecordHashMismatchAfter = "";
	private static String stopLoggingIfEventHashMismatchAfter = "";

	private static boolean persistClaims = false;

	private static String persistFiles = "NONE";

	private static boolean persistContracts = false;

	private static boolean persistCryptoTransferAmounts = false;

	private static String apiUsername = "";
	private static String apiPassword = "";

	//database url
    private static String dbUrl = "";
    // database name
    private static String dbName = "";
    // database user
    private static String dbUserName = "";
    // database password
    private static String dbPassword = "";
    // max download items for testing
    private static int maxDownloadItems = 0;

	private static String configSavePath = "./config/config.json";
	private static String balanceSavePath = "./config/balance.json";
	private static String recordsSavePath = "./config/records.json";
	private static String eventsSavePath = "./config/events.json";

	private static JsonObject configJsonObject;
	private static JsonObject balanceJsonObject;
	private static JsonObject recordsJsonObject;
	private static JsonObject eventsJsonObject;

    private static Dotenv dotEnv = Dotenv.configure().ignoreIfMissing().load();

	private static boolean bBalanceFileExists = true;
	private static boolean bRecordsFileExists = true;
	private static boolean bEventsFileExists = true;

	private static boolean bBalanceVerifySigs = false;

	public static enum OPERATION_TYPE {
		BALANCE
		,RECORDS
		,EVENTS
	}

	static {
		log.info("Loading configuration from {}", configSavePath);
		try {
			// migration from config.json to balance.json for some properties related to balances only
			if (!new File(balanceSavePath).exists()) {
				// create the file
				bBalanceFileExists = false;
			}
			if (!new File(recordsSavePath).exists()) {
				bRecordsFileExists = false;
			}
			if (!new File(eventsSavePath).exists()) {
				bEventsFileExists = false;
			}

			configJsonObject = getJsonObject(configSavePath);

			if (configJsonObject.has("cloud-provider")) {
				String provider = configJsonObject.get("cloud-provider").getAsString();
				if (provider.contentEquals("GCP")) {
					cloudProvider = CLOUD_PROVIDER.GCP;
				} else if (provider.contentEquals("S3")) {
					cloudProvider = CLOUD_PROVIDER.S3;
				} else {
					log.error("Cloud provider {} not recognized, must be one of S3 or GCP", provider);
				}
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
			if (configJsonObject.has("proxyPort")) {
				proxyPort = configJsonObject.get("proxyPort").getAsInt();
			}
			if (configJsonObject.has("addressBookFile")) {
				addressBookFile = configJsonObject.get("addressBookFile").getAsString();
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
			apiUsername = dotEnv.get("DB_USER");
			if (apiUsername == null) {
				if (configJsonObject.has("apiUsername")) {
					apiUsername = configJsonObject.get("apiUsername").getAsString();
				}
			}
			apiPassword = dotEnv.get("DB_PASS");
			if (apiPassword == null) {
				if (configJsonObject.has("apiPassword")) {
					apiPassword = configJsonObject.get("apiPassword").getAsString();
				}
			}
			dbUrl = dotEnv.get("HEDERA_MIRROR_DB_URL");
			if (dbUrl == null) {
				if (configJsonObject.has("dbUrl")) {
					dbUrl = configJsonObject.get("dbUrl").getAsString();
				}
			}
			dbName = dotEnv.get("HEDERA_MIRROR_DB_NAME");
			if (dbName == null) {
				if (configJsonObject.has("dbName")) {
					dbName = configJsonObject.get("dbName").getAsString();
				}
			}
			dbUserName = dotEnv.get("HEDERA_MIRROR_DB_USER");
			if (dbUserName == null) {
				if (configJsonObject.has("dbUsername")) {
					dbUserName = configJsonObject.get("dbUsername").getAsString();
				}
			}
			dbPassword = dotEnv.get("HEDERA_MIRROR_DB_PASS");
			if (dbPassword == null) {
				if (configJsonObject.has("dbPassword")) {
					dbPassword = configJsonObject.get("dbPassword").getAsString();
				}
			}
			if (configJsonObject.has("maxDownloadItems")) {
				maxDownloadItems = configJsonObject.get("maxDownloadItems").getAsInt();
			}
			if (configJsonObject.has("stopLoggingIfRecordHashMismatch")) {
				stopLoggingIfRecordHashMismatchAfter = configJsonObject.get("stopLoggingIfRecordHashMismatch").getAsString();
				if (ApplicationStatus.setStatus(ApplicationStatusCode.RHMBUA, stopLoggingIfRecordHashMismatchAfter)) {
					// remove from config file
					configJsonObject.remove("stopLoggingIfRecordHashMismatch");
					UpdateConfigFile();
				}
			} else {
				stopLoggingIfRecordHashMismatchAfter = ApplicationStatus.getStatus(ApplicationStatusCode.RHMBUA);
			}
			
			if (configJsonObject.has("stopLoggingIfEventHashMismatch")) {
				stopLoggingIfEventHashMismatchAfter = configJsonObject.get("stopLoggingIfEventHashMismatch").getAsString();
				if (ApplicationStatus.setStatus(ApplicationStatusCode.EHMBUA, stopLoggingIfEventHashMismatchAfter)) {
					// remove from config file
					configJsonObject.remove("stopLoggingIfEventHashMismatch");
					UpdateConfigFile();
				}
			} else {
				stopLoggingIfEventHashMismatchAfter = ApplicationStatus.getStatus(ApplicationStatusCode.EHMBUA);
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

			if (bBalanceFileExists) {
				balanceJsonObject = getJsonObject(balanceSavePath);
				if (balanceJsonObject.has("lastValidBalanceFileName")) {
					lastValidBalanceFileName = balanceJsonObject.get("lastValidBalanceFileName").getAsString();
				}
				
			} else if (configJsonObject.has("lastValidBalanceFileName")) {
					lastValidBalanceFileName = configJsonObject.get("lastValidBalanceFileName").getAsString();
					configJsonObject.remove("lastValidBalanceFileName");
			} else {
				lastValidBalanceFileName = ApplicationStatus.getStatus(ApplicationStatusCode.LVDBF);
			}
			
			// migrate to database if necessary
			if (bBalanceFileExists || configJsonObject.has("lastValidBalanceFileName")) {
				if (ApplicationStatus.setStatus(ApplicationStatusCode.LVDBF, lastValidBalanceFileName)) {
					// update config.json to remove unnecessary data
					UpdateConfigFile();		
					if (bBalanceFileExists) {
						File file = new File(balanceSavePath);
						file.delete();
						bBalanceFileExists = false;
					}
				}
			}

			if (bRecordsFileExists) {
				recordsJsonObject = getJsonObject(recordsSavePath);
				if (recordsJsonObject.has("lastValidRcdFileName")) {
					lastValidRcdFileName = recordsJsonObject.get("lastValidRcdFileName").getAsString();
				}
				if (recordsJsonObject.has("lastValidRcdFileHash")) {
					lastValidRcdFileHash = recordsJsonObject.get("lastValidRcdFileHash").getAsString();
				}
			} else if ((configJsonObject.has("lastValidRcdFileName")) || (configJsonObject.has("lastValidRcdFileHash"))) {
				if (configJsonObject.has("lastValidRcdFileName")) {
					lastValidRcdFileName = configJsonObject.get("lastValidRcdFileName").getAsString();
					configJsonObject.remove("lastValidRcdFileName");
				}
				if (configJsonObject.has("lastValidRcdFileHash")) {
					lastValidRcdFileHash = configJsonObject.get("lastValidRcdFileHash").getAsString();
					configJsonObject.remove("lastValidRcdFileHash");
				}
			} else {
				lastValidRcdFileName = ApplicationStatus.getStatus(ApplicationStatusCode.LVDRF);
				lastValidRcdFileHash = ApplicationStatus.getStatus(ApplicationStatusCode.LVDRFH);
			}
			
			// migrate to database if necessary
			if (bRecordsFileExists || configJsonObject.has("lastValidRcdFileName")  || (configJsonObject.has("lastValidRcdFileHash"))) {
				if (ApplicationStatus.setStatus(ApplicationStatusCode.LVDRF, lastValidRcdFileName)) {
					if (ApplicationStatus.setStatus(ApplicationStatusCode.LVDRFH, lastValidRcdFileHash)) {
						// update config.json to remove unnecessary data
						UpdateConfigFile();		
						if (bRecordsFileExists) {
							File file = new File(recordsSavePath);
							file.delete();
							bRecordsFileExists = false;
						}
					}
				}
			}

			if (bEventsFileExists) {
				eventsJsonObject = getJsonObject(eventsSavePath);
				if (recordsJsonObject.has("lastValidEventFileName")) {
					lastValidEventFileName = eventsJsonObject.get("lastValidEventFileName").getAsString();
				}
				if (recordsJsonObject.has("lastValidEventFileHash")) {
				    lastValidEventFileHash = recordsJsonObject.get("lastValidEventFileHash").getAsString();
				}
				// save to database
				if (ApplicationStatus.setStatus(ApplicationStatusCode.LVDEF, lastValidEventFileName)) {
					if (ApplicationStatus.setStatus(ApplicationStatusCode.LVDEFH, lastValidEventFileHash)) {
						File file = new File(eventsSavePath);
						file.delete();
						bEventsFileExists = false;
					}
				}
			} else {
				lastValidEventFileName = ApplicationStatus.getStatus(ApplicationStatusCode.LVDEF);
				lastValidEventFileHash = ApplicationStatus.getStatus(ApplicationStatusCode.LVDEFH);
			}

			if (configJsonObject.has("balanceVerifySigs")) {
				bBalanceVerifySigs = configJsonObject.get("balanceVerifySigs").getAsBoolean();
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

	public static String getDownloadToDir(OPERATION_TYPE operation) {
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

	public static int getProxyPort() {
		return proxyPort;
	}

	public static String getAddressBookFile() {
		return addressBookFile;
	}

	public static void setAddressBookFile(String newAddressBookFile) {
		addressBookFile = newAddressBookFile;
	}

	public static String getLastValidRcdFileName() {
		return ApplicationStatus.getStatus(ApplicationStatusCode.LVDRF);
	}

	public static void setLastValidRcdFileName(String name) {
		ApplicationStatus.setStatus(ApplicationStatusCode.LVDRF, name);
		log.trace("Update lastValidRcdFileName to be {}", name);
	}

	public static String getLastValidRcdFileHash() {
		return ApplicationStatus.getStatus(ApplicationStatusCode.LVDRFH);
	}

	public static void setLastValidRcdFileHash(String hash) {
		if (ApplicationStatus.setStatus(ApplicationStatusCode.LVDRFH, hash)) {
			log.trace("Update lastValidRcdFileHash to be {}", hash);
		}
	}

	public static String getLastValidEventFileName() {
		return ApplicationStatus.getStatus(ApplicationStatusCode.LVDEF);
	}

	public static void setLastValidEventFileName(String name) {
		if (ApplicationStatus.setStatus(ApplicationStatusCode.LVDEF, name)) {
			log.trace("Update lastValidEventFileName to be {}", name);
		}
	}

	public static String getLastValidEventFileHash() {
		return ApplicationStatus.getStatus(ApplicationStatusCode.LVDEFH);
	}

	public static void setLastValidEventFileHash(String hash) {
		if (ApplicationStatus.setStatus(ApplicationStatusCode.LVDEFH, hash)) {
			log.trace("Update lastValidEventFileHash to be {}", hash);
		}
	}

	public static String getLastValidBalanceFileName() {
		return ApplicationStatus.getStatus(ApplicationStatusCode.LVDBF);
	}

	public static String getAccountBalanceS3Location() {
		return accountBalanceS3Location;
	}

	public static String getRecordFilesS3Location() {
		return recordFilesS3Location;
	}

	public static String getEventFilesS3Location() {
		return eventFilesS3Location;
	}

    public static String getApiUsername() {
        return apiUsername;
    }
    public static String getApiPassword() {
        return apiPassword;
    }
	public static String getDBUrl() {
		return dbUrl;
	}
    public static String getDBName() {
        return dbName;
    }
	public static String getDBUserName() {
		return dbUserName;
	}
	public static String getDBPassword() {
		return dbPassword;
	}

	public static int getMaxDownloadItems() {
		return maxDownloadItems;
	}

	public static boolean getPersistClaims() {
		return persistClaims;
	}
	public static String getStopLoggingIfRecordHashMismatchAfter() {
		return ApplicationStatus.getStatus(ApplicationStatusCode.RHMBUA);
	}
	public static String getStopLoggingIfEventHashMismatchAfter() {
		return ApplicationStatus.getStatus(ApplicationStatusCode.EHMBUA);
	}
	public static String getPersistFiles() {
		return persistFiles;
	}
	public static boolean getPersistContracts() {
		return persistContracts;
	}
	public static boolean getPersistCryptoTransferAmounts() {
		return persistCryptoTransferAmounts;
	}
	public static boolean getBalanceVerifySigs() {
		return bBalanceVerifySigs;
	}

	public static void setLastValidBalanceFileName(String name) {
		lastValidBalanceFileName = name;
		balanceJsonObject.addProperty("lastValidBalanceFileName", name);
		log.trace("Update lastValidBalanceFileName to be {}", name);
		saveBalanceDataToFile();
	}

	public static void saveBalanceDataToFile() {
		if (!bBalanceFileExists) {
			File balanceFile = new File(balanceSavePath);
			try {
				balanceFile.createNewFile();
				bBalanceFileExists = true;
			} catch (IOException e) {
				log.error("Unable to create balance data file {}", balanceSavePath, e);
				return;
			}
		}
		try (FileWriter file = new FileWriter(balanceSavePath)) {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			gson.toJson(balanceJsonObject, file);
			log.debug("Successfully wrote update to {}", balanceSavePath);
		} catch (IOException ex) {
			log.error("Fail to write update to {}", balanceSavePath, ex);
		}
	}

	private static void UpdateConfigFile() {
		try (FileWriter file = new FileWriter(configSavePath)) {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			gson.toJson(configJsonObject, file);
			log.debug("Successfully wrote update to {}", configSavePath);
		} catch (IOException ex) {
			log.error("Fail to write update to {}", configSavePath, ex);
		}
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
