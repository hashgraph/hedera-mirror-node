package com.hedera.configLoader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import io.github.cdimascio.dotenv.Dotenv;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ConfigLoader {

	private static final Logger log = LogManager.getLogger("recordStream-log");
	private static final Marker MARKER = MarkerManager.getMarker("ConfigLoader");

	public enum CLOUD_PROVIDER {
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

	// download period in seconds
	private static long downloadPeriodSec = 120;

	// the directory where we store the RecordStream files
	private static String downloadToDir = "./recordstreams";

	// the default directory to be parsed
	private static String defaultParseDir = "./recordstreams/valid/";

	// the port of mirrorNodeProxy;
	private static int proxyPort = 50777;

	// path of the file which contains nodeInfo
	private static String nodeInfoFile = "./config/nodesInfo.json";

	// path of addressBook file
	private static String addressBookFile = "./config/0.0.102";

	// file name of last downloaded rcd_sig file
	private static String lastDownloadedRcdSigName = "";

	// file name of last valid rcd file
	private static String lastValidRcdFileName = "";

	// Hash of last last valid rcd file
	private static String lastValidRcdFileHash = "";

	// file name of last valid account balance file
	private static String lastValidBalanceFileName = "";
	
	// location of account balances on S3
	private static String accountBalanceS3Location = "accountBalances/balance";

	//location of record files on S3
	private static String recordFilesS3Location = "./recordstreams/record";

	private static String stopLoggingIfHashMismatchAfter = "";

	private static boolean persistClaims = false;
	
	private static String persistFiles = "NONE";
	
	private static boolean persistContracts = false;
	
	private static boolean persistCryptoTransferAmounts = false;
		
	//database url
    private static String dbUrl = "";
    // database user
    private static String dbUserName = "";
    // database password
    private static String dbPassword = "";
    // max download items for testing
    private static int maxDownloadItems = 0;
	
	private static String configSavePath = "./config/config.json";

	private static JsonObject jsonObject;

    private static Dotenv dotEnv = Dotenv.configure().ignoreIfMissing().load();
    
	public ConfigLoader(String configPath) {
		configSavePath = configPath;
		log.info(MARKER, "Loading configuration from {}", configPath);
		try {
			jsonObject = getJsonObject(configPath);
			
			if (jsonObject.has("cloud-provider")) {
				String provider = jsonObject.get("cloud-provider").getAsString();
				if (provider.contentEquals("GCP")) {
					cloudProvider = CLOUD_PROVIDER.GCP;
				} else if (provider.contentEquals("S3")) {
					cloudProvider = CLOUD_PROVIDER.S3;
				} else {
					log.error(MARKER, "Cloud provider {} not recognized, must be one of S3 or GCP", provider);
				}
			}
			if (jsonObject.has("clientRegion")) {
				clientRegion = jsonObject.get("clientRegion").getAsString();
			}
			if (jsonObject.has("bucketName")) {
				bucketName = jsonObject.get("bucketName").getAsString();
			}
			
			accessKey = dotEnv.get("HEDERA_S3_ACCESS_KEY");
			if (accessKey == null) {
				if (jsonObject.has("accessKey")) {
					accessKey = jsonObject.get("accessKey").getAsString();
				}
			}
			secretKey = dotEnv.get("HEDERA_S3_SECRET_KEY");
			if (secretKey == null) {
				if (jsonObject.has("secretKey")) {
					secretKey = jsonObject.get("secretKey").getAsString();
				}
			}
			if (jsonObject.has("downloadPeriodSec")) {
				downloadPeriodSec = jsonObject.get("downloadPeriodSec").getAsLong();
			}
			if (jsonObject.has("downloadToDir")) {
				downloadToDir = jsonObject.get("downloadToDir").getAsString();
			}
			if (jsonObject.has("defaultParseDir")) {
				defaultParseDir = jsonObject.get("defaultParseDir").getAsString();
			}
			if (jsonObject.has("proxyPort")) {
				proxyPort = jsonObject.get("proxyPort").getAsInt();
			}
			if (jsonObject.has("nodeInfoFile")) {
				nodeInfoFile = jsonObject.get("nodeInfoFile").getAsString();
			}
			if (jsonObject.has("addressBookFile")) {
				addressBookFile = jsonObject.get("addressBookFile").getAsString();
			}
			if (jsonObject.has("lastDownloadedRcdSigName")) {
				lastDownloadedRcdSigName = jsonObject.get("lastDownloadedRcdSigName").getAsString();
			}
			if (jsonObject.has("lastValidRcdFileName")) {
				lastValidRcdFileName = jsonObject.get("lastValidRcdFileName").getAsString();
			}
			if (jsonObject.has("lastValidRcdFileHash")) {
				lastValidRcdFileHash = jsonObject.get("lastValidRcdFileHash").getAsString();
			}
			if (jsonObject.has("lastValidBalanceFileName")) {
				lastValidBalanceFileName = jsonObject.get("lastValidBalanceFileName").getAsString();
			}
			if (jsonObject.has("accountBalancesS3Location")) {
				accountBalanceS3Location = jsonObject.get("accountBalancesS3Location").getAsString();
			}
			if (jsonObject.has("recordFilesS3Location")) {
				recordFilesS3Location = jsonObject.get("recordFilesS3Location").getAsString();
			}
			dbUrl = dotEnv.get("HEDERA_MIRROR_DB_URL");
			if (dbUrl == null) {
				if (jsonObject.has("dbUrl")) {
					dbUrl = jsonObject.get("dbUrl").getAsString();
				}
			}
			dbUserName = dotEnv.get("HEDERA_MIRROR_DB_USER");
			if (dbUserName == null) {
				if (jsonObject.has("dbUsername")) {
					dbUserName = jsonObject.get("dbUsername").getAsString();
				}
			}
			dbPassword = dotEnv.get("HEDERA_MIRROR_DB_PASS");
			if (dbPassword == null) {
				if (jsonObject.has("dbPassword")) {
					dbPassword = jsonObject.get("dbPassword").getAsString();
				}
			}
			if (jsonObject.has("maxDownloadItems")) {
				maxDownloadItems = jsonObject.get("maxDownloadItems").getAsInt();
			}
			if (jsonObject.has("stopLoggingIfHashMismatch")) {
				stopLoggingIfHashMismatchAfter = jsonObject.get("stopLoggingIfHashMismatch").getAsString();
			}
			if (jsonObject.has("persistClaims")) {
				persistClaims = jsonObject.get("persistClaims").getAsBoolean();
			}
			if (jsonObject.has("persistFiles")) {
				persistFiles = jsonObject.get("persistFiles").getAsString();
			}
			if (jsonObject.has("persistContracts")) {
				persistContracts = jsonObject.get("persistContracts").getAsBoolean();
			}
			if (jsonObject.has("persistCryptoTransferAmounts")) {
				persistCryptoTransferAmounts = jsonObject.get("persistCryptoTransferAmounts").getAsBoolean();
			}
			
		} catch (FileNotFoundException ex) {
			log.warn(MARKER, "Cannot load configuration from {}, Exception: {}", configPath, ex.getStackTrace());
		}
	}

	public CLOUD_PROVIDER getCloudProvider() {
		return cloudProvider;
	}
	public String getClientRegion() {
		return clientRegion;
	}

	public String getBucketName() {
		return bucketName;
	}

	public String getAccessKey() {
		return accessKey;
	}

	public String getSecretKey() {
		return secretKey;
	}

	public long getDownloadPeriodSec() {
		return downloadPeriodSec;
	}

	public String getDownloadToDir() {
		return downloadToDir;
	}

	public String getDefaultParseDir() {
		return defaultParseDir;
	}

	public int getProxyPort() {
		return proxyPort;
	}

	public String getNodeInfoFile() {
		return nodeInfoFile;
	}

	public String getAddressBookFile() {
		return addressBookFile;
	}

	public void setAddressBookFile(String newAddressBookFile) {
		addressBookFile = newAddressBookFile;
	}

	public String getLastDownloadedRcdSigName() {
		return lastDownloadedRcdSigName;
	}

	public void setLastDownloadedRcdSigName(String name) {
		lastDownloadedRcdSigName = name;
		jsonObject.addProperty("lastDownloadedRcdSigName", name);
		log.info(MARKER, "Update lastDownloadedRcdSigName to be {}", name);
	}

	public String getLastValidRcdFileName() {
		return lastValidRcdFileName;
	}

	public void setLastValidRcdFileName(String name) {
		lastValidRcdFileName = name;
		jsonObject.addProperty("lastValidRcdFileName", name);
		log.info(MARKER, "Update lastValidRcdFileName to be {}", name);
	}

	public String getLastValidRcdFileHash() {
		return lastValidRcdFileHash;
	}

	public void setLastValidRcdFileHash(String name) {
		lastValidRcdFileHash = name;
		jsonObject.addProperty("lastValidRcdFileHash", name);
		log.info(MARKER, "Update lastValidRcdFileHash to be {}", name);
	}

	public String getLastValidBalanceFileName() {
		return lastValidBalanceFileName;
	}
	
	public String getAccountBalanceS3Location() {
		return accountBalanceS3Location;
	}

	public String getRecordFilesS3Location() {
		return recordFilesS3Location;
	}
	
	public String getDBUrl() {
		return dbUrl;
	}
	public String getDBUserName() {
		return dbUserName;
	}
	public String getDBPassword() {
		return dbPassword;
	}
	
	public int getMaxDownloadItems() {
		return maxDownloadItems;	
	}
	
	public boolean getPersistClaims() {
		return persistClaims;
	}
	public String getStopLoggingIfHashMismatchAfter() {
		return stopLoggingIfHashMismatchAfter;
	}
	public String getPersistFiles() {
		return persistFiles;
	}
	public boolean getPersistContracts() {
		return persistContracts;
	}
	public boolean getPersistCryptoTransferAmounts() {
		return persistCryptoTransferAmounts;
	}
	
	public void setLastValidBalanceFileName(String name) {
		lastValidBalanceFileName = name;
		jsonObject.addProperty("lastValidBalanceFileName", name);
		log.info(MARKER, "Update lastValidBalanceFileName to be {}", name);
	}

	public void saveToFile() {
		try (FileWriter file = new FileWriter(configSavePath)) {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			gson.toJson(jsonObject, file);
			log.info(MARKER, "Successfully wrote configuration to {}", configSavePath);
		} catch (IOException ex) {
			log.warn(MARKER, "Fail to write configuration to {}, Exception: {}", configSavePath, ex.getStackTrace());
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
