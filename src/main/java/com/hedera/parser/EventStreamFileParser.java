package com.hedera.parser;

import com.hedera.configLoader.ConfigLoader;
import com.hedera.databaseUtilities.DatabaseUtilities;
import com.hedera.mirrorNodeProxy.Utility;
import com.hedera.platform.Transaction;
import com.hedera.recordFileLogger.RecordFileLogger;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class EventStreamFileParser {
	private static final Logger log = LogManager.getLogger("eventStream-log");
	private static final Marker MARKER = MarkerManager.getMarker("EVENT_STREAM");
	static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");

	static final byte TYPE_PREV_HASH = 1;       // next 48 bytes are hash384 or previous files
	static final byte EVENT_STREAM_FILE_VERSION = 2;
	static final byte STREAM_EVENT_VERSION = 2;
	static final byte STREAM_EVENT_START_NO_TRANS_WITH_VERSION = 0x5b;
	static final byte STREAM_EVENT_START_WITH_VERSION = 0x5a;
	static final byte commEventLast = 0x46;

	private static Connection connect = null;

	private static ConfigLoader configLoader;

	private static final long PARENT_HASH_NULL = -1;
	private static final long PARENT_HASH_NOT_FOUND_MATCH = -2;

	/**
	 * Given a EventStream file name, read and parse and return as a list of service record pair
	 *
	 * @param fileName
	 * 		the name of record file to read
	 * @param previousFileHash
	 * 		previous file hash
	 */
	static public boolean loadEventStreamFile(String fileName, String previousFileHash) {

		File file = new File(fileName);
		FileInputStream stream = null;
		String newFileHash = "";

		if (file.exists() == false) {
			log.info(MARKER, "File does not exist " + fileName);
			return false;
		}

		try {
			long counter = 0;
			stream = new FileInputStream(file);
			DataInputStream dis = new DataInputStream(stream);

			int eventStreamFileVersion = dis.readInt();

			log.info(MARKER, "EventStream file format version " + eventStreamFileVersion);
			if (eventStreamFileVersion != EVENT_STREAM_FILE_VERSION) {
				log.error(MARKER, "EventStream file format version doesn't match.");
				return false;
			}
			while (dis.available() != 0) {
				try {
					byte typeDelimiter = dis.readByte();
					switch (typeDelimiter) {
						case TYPE_PREV_HASH:
							byte[] readFileHash = new byte[48];
							dis.read(readFileHash);
							if (previousFileHash.isEmpty()) {
								log.error(MARKER, "Previous file Hash not available");
								previousFileHash = Hex.encodeHexString(readFileHash);
							} else {
								log.info(MARKER, "Previous file Hash = " + previousFileHash);
							}
							newFileHash = Hex.encodeHexString(readFileHash);

							if (!Arrays.equals(new byte[48], readFileHash) && !newFileHash.contentEquals(previousFileHash)) {
								if (configLoader.getStopLoggingIfHashMismatch()) {
									log.error(MARKER, "Previous file Hash Mismatch - stopping loading. fileName = {}, Previous = {}, Current = {}", fileName, previousFileHash, newFileHash);
									return false;
								}
							}
							break;

						case STREAM_EVENT_START_NO_TRANS_WITH_VERSION:
							loadEvent(dis, true);
							counter++;
							break;
						case STREAM_EVENT_START_WITH_VERSION:
							loadEvent(dis, false);
							counter++;
							break;
						default:
							log.error(LOGM_EXCEPTION, "Exception Unknown record file delimiter {}", typeDelimiter);
					}

				} catch (Exception e) {
					log.error(LOGM_EXCEPTION, "Exception ", e);
					return false;
				}
			}
			dis.close();
			log.info(MARKER,"Loaded {} events successfully from {}", counter, fileName);
		} catch (FileNotFoundException e) {
			log.error(MARKER, "File Not Found Error");
			return false;
		} catch (IOException e) {
			log.error(MARKER, "IOException Error");
			return false;
		} catch (Exception e) {
			log.error(MARKER, "Parsing Error");
			return false;
		} finally {
			try {
				if (stream != null)
					stream.close();
			} catch (IOException ex) {
				log.error(MARKER, "Exception in close the stream {}", ex);
				return true;
			}
		}

		return true;
	}

	static void loadEvent(DataInputStream dis, boolean noTxs) throws IOException {
		if (dis.readInt() != STREAM_EVENT_VERSION) {
			log.error(MARKER, "EventStream format version doesn't match.");
			return;
		}
		long creatorId = dis.readLong();
		long creatorSeq = dis.readLong();
		long otherId = dis.readLong();
		long otherSeq = dis.readLong();
		long selfParentGen = dis.readLong();
		long otherParentGen = dis.readLong();
		byte[] selfParentHash = readNullableByteArray(dis);
		byte[] otherParentHash = readNullableByteArray(dis);
		Transaction[] transactions = new Transaction[0];
		if (!noTxs) {
			transactions = Transaction.readArray(dis);
		}
		Instant timeCreated = readInstant(dis);
		byte[] signature = readByteArray(dis);
		if (dis.readByte() != commEventLast) {
			log.warn(MARKER, "event end marker incorrect");
			return;
		}

		// event's hash
		byte[] hash = readByteArray(dis);
		Instant consensusTimeStamp = readInstant(dis);
		long consensusOrder = dis.readLong();
		log.info(MARKER, "Loaded Event: creatorId: {}, creatorSeq: {}, otherId: {}, otherSeq: {}, selfParentGen: {}, otherParentGen: {}, selfParentHash: {}, otherParentHash: {}, transactions: {}, timeCreated: {}, signature: {}, hash: {}, consensusTimeStamp: {}, consensusOrder: {}", creatorId, creatorSeq, otherId, otherSeq, selfParentGen, otherParentGen, Utility.bytesToHex(selfParentHash), Utility.bytesToHex(otherParentHash), transactions, timeCreated, Utility.bytesToHex(signature), Utility.bytesToHex(hash), consensusTimeStamp, consensusOrder);
		storeEvent(creatorId, creatorSeq, otherId, otherSeq, selfParentGen, otherParentGen, selfParentHash, otherParentHash, transactions, timeCreated, signature, hash, consensusTimeStamp, consensusOrder);
	}

	static boolean storeEvent(long creatorId, long creatorSeq, long otherId, long otherSeq, long selfParentGen, long otherParentGen, byte[] selfParentHash, byte[] otherParentHash, Transaction[] transactions, Instant timeCreated, byte[] signature, byte[] hash, Instant consensusTimeStamp, long consensusOrder) {
		try {
			long generation = Math.max(selfParentGen, otherParentGen) + 1;
			long selfParentConsensusOrder = getConsensusOrderForParent(selfParentHash, "selfParentHash");
			long otherParentConsensusOrder = getConsensusOrderForParent(otherParentHash, "otherParentHash");

			int txsBytesCount = 0;
			int platformTxCount = 0;
			int appTxCount = 0;

			if (transactions != null && transactions.length > 0) {
				for (Transaction transaction : transactions) {
					if (transaction.isSystem()) {
						platformTxCount++;
					} else {
						appTxCount++;
					}
				}
			}

			long timeCreatedInNanos = Utility.convertInstantToNanos(timeCreated);
			long consensusTimestampInNanos = Utility.convertInstantToNanos(consensusTimeStamp);
			PreparedStatement insertEvent = connect.prepareStatement(
					"insert into t_events (consensusOrder, creatorNodeId, creatorSeq, otherNodeId, otherSeq, selfParentGen, otherParentGen, generation, selfParentConsensusOrder, otherParentConsensusOrder, timeCreatedInNanos, signature, consensusTimestampInNanos, txsBytesCount, platformTxCount, appTxCount) "
							+ " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ");

			insertEvent.setLong(1, consensusOrder);
			insertEvent.setLong(2, creatorId);
			insertEvent.setLong(3, creatorSeq);
			insertEvent.setLong(4, otherId);
			insertEvent.setLong(5, otherSeq);
			insertEvent.setLong(6, selfParentGen);
			insertEvent.setLong(7, otherParentGen);
			insertEvent.setLong(8, generation);
			insertEvent.setLong(9, selfParentConsensusOrder);
			insertEvent.setLong(10, otherParentConsensusOrder);
			insertEvent.setLong(11, timeCreatedInNanos);
			insertEvent.setBytes(12, signature);
			insertEvent.setLong(13, consensusTimestampInNanos);
			insertEvent.setInt(14, txsBytesCount);
			insertEvent.setInt(15, platformTxCount);
			insertEvent.setInt(16, appTxCount);
			insertEvent.execute();
			log.info(MARKER, "Finished insert to Event, consensusOrder: {}", consensusOrder);

			// store hash
			PreparedStatement insertEventHash = connect.prepareStatement(
					"insert into t_eventHashes (consensusOrder, hash) "
							+ " values (?, ?) ");
			insertEventHash.setLong(1, consensusOrder);
			insertEventHash.setBytes(2, hash);
			insertEventHash.execute();
			log.info(MARKER, "Finished insert to EventHash, consensusOrder: {}", consensusOrder);
		} catch (SQLException ex) {
			log.error(LOGM_EXCEPTION, "Exception {}", ex);
			return false;
		}
		return true;
	}

	/**
	 * Find an event's consensusOrder in t_eventHashes table which hash value matches the given byte array
	 * return PARENT_HASH_NULL if the byte array is empty;
	 * return PARENT_HASH_NOT_FOUND_MATCH if didn't find a match;
	 * @param hash
	 * @param name
	 * @return
	 * @throws SQLException
	 */
	static long getConsensusOrderForParent(byte[] hash, String name) throws SQLException {
		if (hash == null) {
			return PARENT_HASH_NULL;
		} else {
			PreparedStatement queryParentConsensusOrder = connect.prepareStatement(
					"SELECT consensusOrder FROM t_eventHashes WHERE hash = ?");
			queryParentConsensusOrder.setBytes(1, hash);
			queryParentConsensusOrder.execute();
			ResultSet resultSet = queryParentConsensusOrder.getResultSet();
			if (resultSet.next()) {
				return resultSet.getLong(1);
			} else {
				log.error(MARKER, "There isn't an event's Hash in t_eventHashes matches {} : {}", name, hash);
				return PARENT_HASH_NOT_FOUND_MATCH;
			}
		}
	}

	/** read an Instant from a data stream */
	static Instant readInstant(DataInput dis)
			throws IOException {
		Instant time = Instant.ofEpochSecond(//
				dis.readLong(), // from getEpochSecond()
				dis.readLong()); // from getNano()
		return time;
	}

	/** read a byte[] from a data stream and increment byteCount[0] by the number of bytes */
	static byte[] readByteArray(DataInputStream dis)
			throws IOException {
		int len = dis.readInt();
		return readByteArrayOfLength(dis, len);
	}

	static byte[] readNullableByteArray(DataInputStream dis) throws IOException {
		int len = dis.readInt();
		if (len < 0) {
			return null;
		} else {
			return readByteArrayOfLength(dis, len);
		}
	}

	private static byte[] readByteArrayOfLength(DataInputStream dis, int len) throws IOException {
		int checksum = dis.readInt();
		if (len < 0) {
			throw new IOException(
					"readByteArrayOfLength tried to create array of length "
							+ len);
		}
		if (checksum != (101 - len)) { // must be at wrong place in the stream
			throw new IOException(
					"readByteArray tried to create array of length "
							+ len + " with wrong checksum.");
		}
		byte[] data = new byte[len];
		dis.readFully(data);
		return data;
	}

	/**
	 * read and parse a list of record files
	 */
	static public void loadRecordFiles(List<String> fileNames) {
		String prevFileHash = "";
		for (String name : fileNames) {
			if (!loadEventStreamFile(name, prevFileHash)){
				return;
			}
			prevFileHash = Utility.bytesToHex(RecordFileParser.getFileHash(name));
		}
	}

	public static void main(String[] args) {
		configLoader = new ConfigLoader("./config/config.json");

		String pathName = configLoader.getDefaultParseDir_EventStream();
		log.info(MARKER, "EventStream files folder got from configuration file: {}", pathName);

		connect = DatabaseUtilities.openDatabase(connect);

		if (pathName != null) {

			File file = new File(pathName);
			if (file.isFile()) {
				log.info(MARKER, "Loading eventStream file {} ", pathName);
				loadEventStreamFile(pathName, "");
			} else if (file.isDirectory()) { //if it's a directory

				String[] files = file.list(); // get all files under the directory
				Arrays.sort(files);           // sorted by name (timestamp)

				// add director prefix to get full path
				List<String> fullPaths = Arrays.asList(files).stream()
						.filter(f -> Utility.isEventStreamFile(f))
						.map(s -> file + "/" + s)
						.collect(Collectors.toList());

				log.info(MARKER, "Loading eventStream files from directory {} ", pathName);

				if (fullPaths != null) {
					log.info(MARKER, "Files are " + fullPaths);
					loadRecordFiles(fullPaths);
				} else {
					log.info(MARKER, "No files to parse");
				}
			} else {
				log.error(LOGM_EXCEPTION, "Exception file {} does not exist", pathName);

			}
		}
	}

	/**
	 * Given a event stream file name, read its prevFileHash
	 *
	 * @param fileName
	 * 		the name of event stream file to read
	 * @return return previous file hash's Hex String
	 */
	static public String readPrevFileHash(String fileName) {
		File file = new File(fileName);
		FileInputStream stream = null;
		if (file.exists() == false) {
			log.info(MARKER, "File does not exist " + fileName);
			return null;
		}
		byte[] prevFileHash = new byte[48];
		try {
			stream = new FileInputStream(file);
			DataInputStream dis = new DataInputStream(stream);

			// record_file format_version
			dis.readInt();

			byte typeDelimiter = dis.readByte();

			if (typeDelimiter == TYPE_PREV_HASH) {
				dis.read(prevFileHash);
				String hexString = Hex.encodeHexString(prevFileHash);
				log.info(MARKER, "readPrevFileHash :: Previous file Hash = {}, file name = {}", hexString, fileName);
				dis.close();
				return hexString;
			} else {
				log.error(MARKER, "readPrevFileHash :: Should read Previous file Hash, but found file delimiter {}, file name = {}", typeDelimiter, fileName);
			}
			dis.close();

		} catch (FileNotFoundException e) {
			log.error(MARKER, "readPrevFileHash :: File Not Found Error, file name = {}",  fileName);
		} catch (IOException e) {
			log.error(MARKER, "readPrevFileHash :: IOException Error, file name = {}",  fileName);
		} catch (Exception e) {
			log.error(MARKER, "readPrevFileHash :: Parsing Error, file name = {}",  fileName);
		} finally {
			try {
				if (stream != null)
					stream.close();
			} catch (IOException ex) {
				log.error("readPrevFileHash :: Exception in close the stream {}", ex);
			}
		}

		return null;
	}
}

