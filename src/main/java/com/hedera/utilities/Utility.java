package com.hedera.utilities;



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
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.hedera.downloader.Downloader;
import com.hedera.filedelimiters.FileDelimiter;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
public class Utility {

  private static final Long SCALAR = 1_000_000_000L;

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
		Pair<byte[], byte[]> sigPair = extractHashAndSigFromFile(sigFile);
		return sigPair == null ? false : Arrays.equals(fileHash, sigPair.getLeft());
	}
	/**
	 * 1. Extract the Hash of the content of corresponding RecordStream file. This Hash is the signed Content of this signature
	 * 2. Extract signature from the file.
	 * @param file
	 * @return
	 */
	public static Pair<byte[], byte[]> extractHashAndSigFromFile(File file) {
		byte[] sig = null;

		if (file.exists() == false) {
			log.info("File does not exist {}", file.getPath());
			return null;
		}

		try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
			byte[] fileHash = new byte[48];

			while (dis.available() != 0) {
				byte typeDelimiter = dis.readByte();

				switch (typeDelimiter) {
					case FileDelimiter.SIGNATURE_TYPE_FILE_HASH:
						dis.read(fileHash);
						break;

					case FileDelimiter.SIGNATURE_TYPE_SIGNATURE:
						int sigLength = dis.readInt();
						byte[] sigBytes = new byte[sigLength];
						dis.readFully(sigBytes);
						sig = sigBytes;
						break;
					default:
						log.error("Unknown file delimiter {} in signature file {}", typeDelimiter, file);
						return null;
				}
			}

			return Pair.of(fileHash, sig);
		} catch (Exception e) {
			log.error("Unable to extract hash and signature from file {}", file, e);
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
		if (getFileExtension(fileName).contentEquals("rcd")) {
			return getRecordFileHash(fileName);
		} else if (getFileExtension(fileName).contentEquals("evt")) {
			return getEventFileHash(fileName);
		} else {
			try {
				md = MessageDigest.getInstance(FileDelimiter.HASH_ALGORITHM);
	
				byte[] array = new byte[0];
				try {
					array = Files.readAllBytes(Paths.get(fileName));
				} catch (IOException e) {
					log.error("Exception {}", e);
				}
				byte[] fileHash = md.digest(array);
				return fileHash;
	
			} catch (NoSuchAlgorithmException e) {
				log.error("Exception {}", e);
				return null;
			}
		}
	}
	
	private static byte[] getEventFileHash(String filename) {
		var file = new File(filename);
		// for >= version3, we need to calculate hash for content;
		boolean calculateContentHash = false;

		// MessageDigest for getting the file Hash
		// suppose file[i] = p[i] || h[i] || c[i];
		// p[i] denotes the bytes before previousFileHash;
		// h[i] denotes the hash of file i - 1, i.e., previousFileHash;
		// c[i] denotes the bytes after previousFileHash;
		// '||' means concatenation
		// for Version2, h[i + 1] = hash(p[i] || h[i] || c[i]);
		// for Version3, h[i + 1] = hash(p[i] || h[i] || hash(c[i]))
		MessageDigest md;

		// is only used in Version3, for getting the Hash for content after prevFileHash in current file, i.e., hash
		// (c[i])
		MessageDigest mdForContent = null;

		try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
			md = MessageDigest.getInstance(FileDelimiter.HASH_ALGORITHM);

			int eventStreamFileVersion = dis.readInt();
			md.update(Utility.integerToBytes(eventStreamFileVersion));

			log.debug("Loading event file {} with version {}", filename, eventStreamFileVersion);
			if (eventStreamFileVersion < FileDelimiter.EVENT_STREAM_FILE_VERSION_LEGACY) {
				log.error("EventStream file format version doesn't match. File is: {}", filename);
				return null;
			} else if (eventStreamFileVersion >= FileDelimiter.EVENT_STREAM_FILE_VERSION_CURRENT) {
				mdForContent = MessageDigest.getInstance(FileDelimiter.HASH_ALGORITHM);
				calculateContentHash = true;
			}

			while (dis.available() != 0) {
				byte typeDelimiter = dis.readByte();
				switch (typeDelimiter) {
					case FileDelimiter.EVENT_TYPE_PREV_HASH:
						md.update(typeDelimiter);
						byte[] readPrevFileHashBytes = new byte[48];
						dis.readFully(readPrevFileHashBytes);
						md.update(readPrevFileHashBytes);
						break;

					case FileDelimiter.EVENT_STREAM_START_NO_TRANS_WITH_VERSION:
						if (calculateContentHash) {
							mdForContent.update(typeDelimiter);
						} else {
							md.update(typeDelimiter);
						}
						break;
					case FileDelimiter.EVENT_STREAM_START_WITH_VERSION:
						if (calculateContentHash) {
							mdForContent.update(typeDelimiter);
						} else {
							md.update(typeDelimiter);
						}
						break;
					default:
						log.error("Unknown file delimiter {} for event file {}", typeDelimiter, file);
				}
			}
			log.trace("Successfully calculated hash for {}", filename);
		} catch (Exception e) {
			log.error("Error parsing event file {}", filename, e);
			return null;
		}

		if (calculateContentHash) {
			byte[] contentHash = mdForContent.digest();
			md.update(contentHash);
		}

		return md.digest();
	}
	
	private static byte[] getRecordFileHash(String filename) {
		byte[] readFileHash = new byte[48];
		
		try (DataInputStream dis = new DataInputStream(new FileInputStream(filename))) {
			MessageDigest md = MessageDigest.getInstance(FileDelimiter.HASH_ALGORITHM);
			MessageDigest mdForContent = MessageDigest.getInstance(FileDelimiter.HASH_ALGORITHM);

			int record_format_version = dis.readInt();
			int version = dis.readInt();

			md.update(Utility.integerToBytes(record_format_version));
			md.update(Utility.integerToBytes(version));

			log.info("Calculating hash for version {} record file: {}", record_format_version, filename);

			while (dis.available() != 0) {

				try {
					byte typeDelimiter = dis.readByte();

					switch (typeDelimiter) {
						case FileDelimiter.RECORD_TYPE_PREV_HASH:
							md.update(typeDelimiter);
							dis.read(readFileHash);
							md.update(readFileHash);
							break;
						case FileDelimiter.RECORD_TYPE_RECORD:

							int byteLength = dis.readInt();
							byte[] rawBytes = new byte[byteLength];
							dis.readFully(rawBytes);
							if (record_format_version >= FileDelimiter.RECORD_FORMAT_VERSION) {
								mdForContent.update(typeDelimiter);
								mdForContent.update(Utility.integerToBytes(byteLength));
								mdForContent.update(rawBytes);
							} else {
								md.update(typeDelimiter);
								md.update(Utility.integerToBytes(byteLength));
								md.update(rawBytes);
							}

							byteLength = dis.readInt();
							rawBytes = new byte[byteLength];
							dis.readFully(rawBytes);

							if (record_format_version >= FileDelimiter.RECORD_FORMAT_VERSION) {
								mdForContent.update(Utility.integerToBytes(byteLength));
								mdForContent.update(rawBytes);

							} else {
								md.update(Utility.integerToBytes(byteLength));
								md.update(rawBytes);
							}

							break;
						case FileDelimiter.RECORD_TYPE_SIGNATURE:
							int sigLength = dis.readInt();
							byte[] sigBytes = new byte[sigLength];
							dis.readFully(sigBytes);
							log.trace("File {} has signature {}", filename, Hex.encodeHexString(sigBytes));
							break;
						default:
							log.error("Unknown record file delimiter {} for file {}", typeDelimiter, filename);
							return null;
					}
				} catch (Exception e) {
					log.error("Exception {}", e);
					return null;
				}
			}

			if (record_format_version >= FileDelimiter.RECORD_FORMAT_VERSION) {
				md.update(mdForContent.digest());
			}
			readFileHash = md.digest();
			
			log.trace("Calculated file hash for the current file {}", Hex.encodeHexString(readFileHash));

		} catch (Exception e) {
			log.error("Error reading hash for file {}", filename, e);
			return null;
		}
		return readFileHash;
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
            log.error("getBytes() failed - file {} not found", location);
		} catch (IOException ex) {
            log.error("getBytes() failed, Exception: {}", ex);
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

	public static String getFileName(String path) {
		int lastIndexOf = path.lastIndexOf("/");
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

		Path destination = Paths.get(pathToSaveTo, sourceFile.getName());
		try {
			Files.move(sourceFile.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
			log.trace("{} has been moved to {}", sourceFile, pathToSaveTo);
		} catch (Exception e) {
			log.error("Error moving file {} to {}", sourceFile, pathToSaveTo, e);
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

	public static void purgeDirectory(String dir) {
		purgeDirectory(new File(dir));

	}
	public static void purgeDirectory(File dir) {
		if ( ! dir.exists()) { return; };

	    for (File file: dir.listFiles()) {
	        if (file.isDirectory())
	            purgeDirectory(file);
	        file.delete();
	    }
	}

	public static void ensureDirectory(String path) {
		if (StringUtils.isBlank(path)) {
			throw new IllegalArgumentException("Empty path");
		}

		File directory = new File(path);
		directory.mkdirs();

		if (!directory.exists()) {
			throw new IllegalStateException("Unable to create directory " + directory.getAbsolutePath());
		}
		if (!directory.isDirectory()) {
			throw new IllegalStateException("Not a directory " + directory.getAbsolutePath());
		}
		if (!directory.canRead() || !directory.canWrite()) {
			throw new IllegalStateException("Insufficient permissions for directory " + directory.getAbsolutePath());
		}
	}

	public static File getResource(String path) {
		ClassLoader[] classLoaders = { Thread.currentThread().getContextClassLoader(), Utility.class.getClassLoader(), ClassLoader.getSystemClassLoader() };
		URL url = null;

		for (ClassLoader classLoader : classLoaders) {
			if (classLoader != null) {
				url = classLoader.getResource(path);
				if (url != null) {
					break;
				}
			}
		}

		if (url == null) {
			throw new RuntimeException("Cannot find resource: " + path);
		}

		try {
			return new File(url.toURI().getSchemeSpecificPart());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
