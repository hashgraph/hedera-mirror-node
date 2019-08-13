package com.hedera.utilities;

import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.hedera.downloader.Downloader;
import com.hedera.parser.RecordFileParser;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utility {
	private static final Logger log = LogManager.getLogger("utility");
	private static final Marker MARKER = MarkerManager.getMarker("MIRROR_NODE");
	private static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");
  private static final Long SCALAR = 1_000_000_000L;

	private static final byte TYPE_PREV_HASH = 1;       // next 48 bytes are hash384 of previous files
	private static final byte TYPE_RECORD = 2;          // next data type is transaction and its record
	private static final byte TYPE_SIGNATURE = 3;       // the file content signature, should not be hashed
	private static final byte TYPE_FILE_HASH = 4;       // next 48 bytes are hash384 of content of corresponding RecordFile


	public static boolean checkStopFile() {
		File stopFile = new File("./stop");
		return stopFile.exists();
	}
	/**
	 * Verify if a .rcd file's hash is equal to the hash contained in .rcd_sig file
	 * @return
	 */
	public static boolean hashMatch(File sigFile, File rcdFile) {
		byte[] fileHash = Utility.getFileHash(rcdFile.getPath());
		return Arrays.equals(fileHash, extractHashAndSigFromFile(sigFile).getLeft());
	}
	/**
	 * 1. Extract the Hash of the content of corresponding RecordStream file. This Hash is the signed Content of this signature
	 * 2. Extract signature from the file.
	 * @param file
	 * @return
	 */
	public static Pair<byte[], byte[]> extractHashAndSigFromFile(File file) {
		FileInputStream stream = null;
		byte[] sig = null;

		if (file.exists() == false) {
			log.info(MARKER, "File does not exist " + file.getPath());
			return null;
		}

		try {
			stream = new FileInputStream(file);
			DataInputStream dis = new DataInputStream(stream);
			byte[] fileHash = new byte[48];

			while (dis.available() != 0) {
				try {
					byte typeDelimiter = dis.readByte();

					switch (typeDelimiter) {
						case TYPE_FILE_HASH:
							dis.read(fileHash);
							// log.info(MARKER, "File Hash = " + Hex.encodeHexString(fileHash));
							break;

						case TYPE_SIGNATURE:
							int sigLength = dis.readInt();
							// log.info(MARKER, "sigLength = " + sigLength);
							byte[] sigBytes = new byte[sigLength];
							dis.readFully(sigBytes);
							// log.info(MARKER, "File {} Signature = {} ", file.getName(), Hex.encodeHexString(sigBytes));
							sig = sigBytes;
							break;
						default:
							log.error(MARKER, "extractHashAndSigFromFile :: Exception Unknown record file delimiter {}", typeDelimiter);
					}

				} catch (Exception e) {
					log.error(MARKER, "extractHashAndSigFromFile :: Exception ", e);
					break;
				}
			}

			return Pair.of(fileHash, sig);
		} catch (FileNotFoundException e) {
			log.error(MARKER, "extractHashAndSigFromFile :: File Not Found Error");
		} catch (IOException e) {
			log.error(MARKER, "extractHashAndSigFromFile :: IOException Error");
		} catch (Exception e) {
			log.error(MARKER, "extractHashAndSigFromFile :: Parsing Error");
		} finally {
			try {
				if (stream != null)
					stream.close();
			} catch (IOException ex) {
				log.error("extractHashAndSigFromFile :: Exception in close the stream {}", ex);
			}
		}

		return null;
	}

	/**
	 * Calculate SHA384 hash of a binary file
	 *
	 * @param fileName
	 * 		file name
	 * @return byte array of hash value
	 */
	public static byte[] getFileHash(String fileName) {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-384");

			byte[] array = new byte[0];
			try {
				array = Files.readAllBytes(Paths.get(fileName));
			} catch (IOException e) {
				log.error("Exception {}", e);
			}
			byte[] fileHash = md.digest(array);
			return fileHash;

		} catch (NoSuchAlgorithmException e) {
			log.error(LOGM_EXCEPTION, "Exception {}", e);
			return null;
		}
	}

	public static AccountID stringToAccountID(final String string) throws IllegalArgumentException{
		if (string == null || string.isEmpty()) {
			throw new IllegalArgumentException("Cannot parse empty string to AccountID");
		}
		String[] strs = string.split("[.]");

		if (strs.length != 3) {
			throw new IllegalArgumentException("Cannot parse string to AccountID: Invalid format.");
		}
		AccountID.Builder idBuilder = AccountID.newBuilder();
		idBuilder.setShardNum(Integer.valueOf(strs[0]))
				.setRealmNum(Integer.valueOf(strs[1]))
				.setAccountNum(Integer.valueOf(strs[2]));
		return idBuilder.build();
	}

	public static JsonObject getJsonInput(String location) throws JsonIOException, JsonSyntaxException, FileNotFoundException {
		JsonParser parser = new JsonParser();
		FileReader file = new FileReader(location);
		return (JsonObject)parser.parse(file);
	}

	public static byte[] integerToBytes(int number) {
		ByteBuffer b = ByteBuffer.allocate(4);
		b.putInt(number);
		return b.array();
	}

	public static byte[] longToBytes(long number) {
		ByteBuffer b = ByteBuffer.allocate(8);
		b.putLong(number);
		return b.array();
	}

	public static byte booleanToByte(boolean value) {
		return value ? (byte)1 : (byte)0;
	}

	public static byte[] instantToBytes(Instant instant) {
		ByteBuffer b = ByteBuffer.allocate(16);
		b.putLong(instant.getEpochSecond()).putLong(instant.getNano());
		return b.array();
	}

	/**
	 * return a string which represents an AccountID
	 * @param accountID
	 * @return
	 */
	public static String accountIDToString(final AccountID accountID) {
		return String.format("%d.%d.%d", accountID.getShardNum(),
				accountID.getRealmNum(), accountID.getAccountNum());
	}

	/**
	 * Compare if two AccountIDs are the same
	 * @param id1
	 * @param id2
	 * @return
	 */
	public static boolean isSameAccountID(final AccountID id1, final AccountID id2) {
		return id1.getAccountNum() == id2.getAccountNum()
				&& id1.getShardNum() == id2.getShardNum()
				&& id1.getRealmNum() == id2.getRealmNum();
	}

	public static Instant convertToInstant(final Timestamp timestamp) {
		return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
	}

	/**
	 * print a Transaction's content to a String
	 * @param transaction
	 * @return
	 * @throws InvalidProtocolBufferException
	 */
	public static String printTransaction(final Transaction transaction) throws InvalidProtocolBufferException {
		StringBuilder stringBuilder = new StringBuilder();
		if (transaction.hasSigs()) {
			stringBuilder.append(TextFormat.shortDebugString(transaction.getSigs()) + "\n");
		}
		if (transaction.hasSigMap()) {
			stringBuilder.append(TextFormat.shortDebugString(transaction.getSigMap()) + "\n");
		}

		stringBuilder.append(TextFormat.shortDebugString(
					getTransactionBody(transaction)) + "\n");
		return stringBuilder.toString();
	}

	/**
	 * print a Transaction's content to a formatted (Readable) String
	 * @param transaction
	 * @return
	 * @throws InvalidProtocolBufferException
	 */
	public static String printTransactionNice(final Transaction transaction) throws InvalidProtocolBufferException {
		StringBuilder stringBuilder = new StringBuilder();

		stringBuilder.append(TextFormat.printToString(
					getTransactionBody(transaction)));
		if (transaction.hasSigs()) {
			stringBuilder.append(TextFormat.printToString(transaction.getSigs()) + "\n");
		}
		if (transaction.hasSigMap()) {
			stringBuilder.append(TextFormat.printToString(transaction.getSigMap()) + "\n");
		}

		return stringBuilder.toString();
	}

	/**
	 * print a protobuf Message's content to a String
	 * @param message
	 * @return
	 * @throws InvalidProtocolBufferException
	 */
	public static String printProtoMessage(final GeneratedMessageV3 message){
		return TextFormat.shortDebugString(message);
	}

	public static TransactionBody getTransactionBody(final Transaction transaction)  throws InvalidProtocolBufferException {
		if (transaction.hasBody()) {
			return transaction.getBody();
		} else {
			return TransactionBody.parseFrom(transaction.getBodyBytes());
		}
	}

	/***
	 *
	 * Build a byte array from data stored in a file
	 *
	 * @param location of the stored bytes
	 * @return a Byte array
	 */
	public static byte[] getBytes(String location){
		byte[] bytes = null;
		try (final FileInputStream fis = new FileInputStream(new File(location))) {
			bytes = fis.readAllBytes();
		} catch (FileNotFoundException ex) {
            log.error(MARKER, "getBytes() failed - file {} not found", location);
		} catch (IOException ex) {
            log.error(MARKER, "getBytes() failed, Exception: {}", ex);
		}
		return bytes;
	}

	/**
	 * Convert hex string to bytes.
	 *
	 * @param data to be converted
	 * @return converted bytes
	 */
	public static byte[] hexToBytes(String data) throws DecoderException {
		byte[] rv = Hex.decodeHex(data);
		return rv;
	}

	/**
	 * Convert bytes to hex.
	 *
	 * @param bytes to be converted
	 * @return converted HexString
	 */
	public static String bytesToHex(byte[] bytes) {
		if (bytes == null || bytes.length == 0) return null;
		return Hex.encodeHexString(bytes);
	}

	/**
	 * parse a timestamp string in file name to Instant
	 * @param str
	 * @return
	 */
	public static Instant parseToInstant(String str) {
		if (str == null || str.isEmpty()) return null;
		Instant result;
		try {
			result = Instant.parse(str);
		} catch (DateTimeParseException ex) {
			result = Instant.parse(str.replace("_", ":"));
		}
		return result;
	}

	/**
	 * Parse a s3ObjectSummaryKey to three parts:
	 * (1) node AccountID string
	 * (2) Instant string
	 * (3) file type string
	 * For example, for "record0.0.101/2019-06-05T20_29_32.856974Z.rcd_sig",
	 * the result would be Triple.of("0.0.101",
	 * 				"2019-06-05T20_29_32.856974Z",
	 * 				"rcd_sig");
	 * 	for "balance0.0.3/2019-06-21T14_56_00.049967001Z_Balances.csv_sig",
	 * 	the result would be Triple.of("0.0.3",
	 * 				"2019-06-21T14_56_00.049967001Z",
	 * 				"Balances.csv_sig");
	 *
	 * @param s3ObjectSummaryKey
	 * @return
	 */
	public static Triple<String, String, String> parseS3SummaryKey(String s3ObjectSummaryKey) {
		String regex;
		if (isRecordSigFile(s3ObjectSummaryKey) || isRecordFile(s3ObjectSummaryKey)) {
			regex = "record([\\d]+[.][\\d]+[.][\\d]+)/(.*Z).(.+)";
		} else if (isBalanceSigFile(s3ObjectSummaryKey) || isBalanceFile(s3ObjectSummaryKey)) {
			regex = "balance([\\d]+[.][\\d]+[.][\\d]+)/(.*Z)_(.+)";
		} else if (isEventStreamFile(s3ObjectSummaryKey) || isEventStreamSigFile(s3ObjectSummaryKey)) {
			regex = "events_([\\d]+[.][\\d]+[.][\\d]+)/(.*Z).(.+)";
		} else {
			return Triple.of(null, null, null);
		}

		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(s3ObjectSummaryKey);
		String inst = null;
		String nodeAccountID = null;
		String file_type = null;
		while(matcher.find()) {
			nodeAccountID = matcher.group(1);
			inst = matcher.group(2);
			file_type = matcher.group(3);
		}
		return Triple.of(nodeAccountID,
				inst,
				file_type);
	}

	public static Instant getInstantFromFileName(String name) {
		if (isRecordFile(name) || isRecordSigFile(name) || isEventStreamFile(name) || isEventStreamSigFile(name)) {
			return parseToInstant(name.substring(0, name.lastIndexOf(".")));
		} else {
			return parseToInstant(name.substring(0, name.lastIndexOf("_Balances")));
		}
	}

	public static String getAccountIDStringFromFilePath(String path) {
		if (isRecordFile(path) || isRecordSigFile(path)) {
			return getAccountIDStringFromFilePath(path, Downloader.DownloadType.RCD);
		} else if (isEventStreamFile(path) || isEventStreamSigFile(path)) {
			return getAccountIDStringFromFilePath(path, Downloader.DownloadType.EVENT);
		} else {
			return getAccountIDStringFromFilePath(path, Downloader.DownloadType.BALANCE);
		}
	}

	public static String getAccountIDStringFromFilePath(String path, Downloader.DownloadType type) {
		String regex;
		if (type == Downloader.DownloadType.RCD) {
			regex = "record([\\d]+[.][\\d]+[.][\\d]+)";
		} else if (type == Downloader.DownloadType.EVENT) {
			regex = "events_([\\d]+[.][\\d]+[.][\\d]+)";
		} else {
			//account balance
			regex = "([\\d]+[.][\\d]+[.][\\d]+)/(.+)Z";
		}
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(path);

		String match = null;
		while(matcher.find()) {
			match = matcher.group(1);
		}
		return match;
	}

	public static String getFileExtension(String path) {
		int lastIndexOf = path.lastIndexOf(".");
		if (lastIndexOf == -1) {
			return ""; // empty extension
		}
		return path.substring(lastIndexOf + 1);
	}

	public static boolean isBalanceFile(String filename) {
		return filename.endsWith("Balances.csv");
	}

	public static boolean isBalanceSigFile(String filename) {
		return filename.endsWith("Balances.csv_sig");
	}

	public static boolean isRecordFile(String filename) {
		return filename.endsWith(".rcd");
	}

	public static boolean isEventStreamFile(String filename) {
		return filename.endsWith(".evts");
	}

	public static boolean isEventStreamSigFile(String filename) {
		return filename.endsWith(".evts_sig");
	}

	public static boolean isRecordSigFile(String filename) {
		return filename.endsWith(".rcd_sig");
	}

	public static String getFileNameFromS3SummaryKey(String s3SummaryKey) {
		Triple<String, String, String> triple = parseS3SummaryKey(s3SummaryKey);
		if (isRecordFile(s3SummaryKey) || isRecordSigFile(s3SummaryKey)) {
			return triple.getMiddle() + "." + triple.getRight();
		} else {
			return triple.getMiddle() + "_" + triple.getRight();
		}
	}

	public static boolean greaterThanSuperMajorityNum(long n, long N) {
		return n > N * 2 / 3.0;
	}

	/**
	 * Convert an Instant to a Long type timestampInNanos
	 * @param instant
	 * @return
	 */
	public static Long convertInstantToNanos(Instant instant) {
		if (instant == null) {
			return null;
		}
		return instant.getEpochSecond() * SCALAR + instant.getNano();
	}

	/**
	 * Convert a Long type timestampInNanos to an Instant
	 * @param bigint
	 * @return
	 */
	public static Instant convertNanosToInstant(Long bigint) {
		if (bigint == null) {
			return null;
		}
		long seconds = bigint / SCALAR;
		int nanos = (int) (bigint % SCALAR);
		return Instant.ofEpochSecond(seconds, nanos);
	}

	public static boolean hashIsEmpty(String hash) {
		return (hash.isEmpty() || hash.contentEquals("000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"));
	}
	
	public static void moveFileToParsedDir(String fileName, String subDir) {
		File sourceFile = new File(fileName);
		String pathToSaveTo = sourceFile.getParentFile().getParentFile().getPath() + subDir;
		String shortFileName = sourceFile.getName().substring(0, 10).replace("-", "/");
		pathToSaveTo += shortFileName;

		File parsedDir = new File(pathToSaveTo);
		parsedDir.mkdirs();

		File destFile = new File(pathToSaveTo + "/" + sourceFile.getName());
		try {
			Files.move(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			log.info(MARKER, sourceFile.toPath() + " has been moved to " + destFile.getPath());
		} catch (IOException ex) {
			log.error(MARKER, "Fail to move {} to {} : {}",
					fileName, parsedDir.getName(),
					ex);
		}
	}

	/**
	 * return false if the directory doesn't exist and we fail to create it;
	 * return true if the directory exists or we create it successfully
	 *
	 * @param path
	 * @return
	 */
	public static boolean createDirIfNotExists(String path) {
		File file = new File(path);
		if (!file.exists()) {
			return file.mkdirs();
		}
		return true;
	}
}
