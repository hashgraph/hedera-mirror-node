package com.hedera.parser;

import com.hedera.configLoader.ConfigLoader;
import com.hedera.databaseUtilities.DatabaseUtilities;
import com.hedera.mirrorNodeProxy.Utility;
import com.hedera.platform.Transaction;
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
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class EventStreamFileParser {
	private static final Logger log = LogManager.getLogger("eventStream-log");
	private static final Marker MARKER = MarkerManager.getMarker("EVENT_STREAM");
	private static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");

	private static final byte TYPE_PREV_HASH = 1;       // next 48 bytes are hash384 or previous files
	private static final byte EVENT_STREAM_FILE_VERSION = 2;
	private static final byte STREAM_EVENT_VERSION = 2;
	private static final byte STREAM_EVENT_START_NO_TRANS_WITH_VERSION = 0x5b;
	private static final byte STREAM_EVENT_START_WITH_VERSION = 0x5a;
	private static final byte commEventLast = 0x46;

	private static Connection connect = null;

	private static ConfigLoader configLoader;

	private static final long PARENT_HASH_NULL = -1;
	private static final long PARENT_HASH_NOT_FOUND_MATCH = -2;

	private enum LoadResult {
		OK, STOP, ERROR
	}

	private static final String PARSED_DIR = "/parsedEventStreamFiles/";

	/**
	 * Given a EventStream file name, read and parse and return as a list of service record pair
	 *
	 * @param fileName
	 * 		the name of record file to read
	 * @param previousFileHash
	 * 		previous file hash
	 */
	static public LoadResult loadEventStreamFile(String fileName, String previousFileHash) {

		File file = new File(fileName);
		String readPrevFileHash;

		if (file.exists() == false) {
			log.info(MARKER, "File does not exist " + fileName);
			return LoadResult.ERROR;
		}

		try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
			long counter = 0;
			int eventStreamFileVersion = dis.readInt();

			log.info(MARKER, "EventStream file format version " + eventStreamFileVersion);
			if (eventStreamFileVersion != EVENT_STREAM_FILE_VERSION) {
				log.error(MARKER, "EventStream file format version doesn't match.");
				return LoadResult.ERROR;
			}
			while (dis.available() != 0) {
				try {
					byte typeDelimiter = dis.readByte();
					switch (typeDelimiter) {
						case TYPE_PREV_HASH:
							byte[] readPrevFileHashBytes = new byte[48];
							dis.read(readPrevFileHashBytes);
							if (previousFileHash.isEmpty()) {
								log.error(MARKER, "Previous file Hash not available");
								previousFileHash = Hex.encodeHexString(readPrevFileHashBytes);
							} else {
								log.info(MARKER, "Previous file Hash = " + previousFileHash);
							}
							readPrevFileHash = Hex.encodeHexString(readPrevFileHashBytes);

							if (!Arrays.equals(new byte[48], readPrevFileHashBytes) && !readPrevFileHash.contentEquals(
									previousFileHash)) {
								if (configLoader.getStopLoggingIfHashMismatch()) {
									log.error(MARKER,
											"Previous file Hash Mismatch - stopping loading. fileName = {}, Previous = " +
													"{}, Current = {}",
											fileName, previousFileHash, readPrevFileHash);
									return LoadResult.STOP;
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
					return LoadResult.ERROR;
				}
			}
			log.info(MARKER, "Loaded {} events successfully from {}", counter, fileName);
		} catch (FileNotFoundException e) {
			log.error(MARKER, "File Not Found Error");
			return LoadResult.ERROR;
		} catch (IOException e) {
			log.error(MARKER, "IOException Error");
			return LoadResult.ERROR;
		}

		return LoadResult.OK;
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
		log.debug(MARKER,
				"Loaded Event: creatorId: {}, creatorSeq: {}, otherId: {}, otherSeq: {}, selfParentGen: {}, " +
						"otherParentGen: {}, selfParentHash: {}, otherParentHash: {}, transactions: {}, timeCreated: " +
						"{}, signature: {}, hash: {}, consensusTimeStamp: {}, consensusOrder: {}",
				creatorId, creatorSeq, otherId, otherSeq, selfParentGen, otherParentGen,
				Utility.bytesToHex(selfParentHash), Utility.bytesToHex(otherParentHash), transactions, timeCreated,
				Utility.bytesToHex(signature), Utility.bytesToHex(hash), consensusTimeStamp, consensusOrder);
		storeEvent(creatorId, creatorSeq, otherId, otherSeq, selfParentGen, otherParentGen, selfParentHash,
				otherParentHash, transactions, timeCreated, signature, hash, consensusTimeStamp, consensusOrder);
	}

	/**
	 * Store parsed Event information into database
	 *
	 * @param creatorId
	 * @param creatorSeq
	 * @param otherId
	 * @param otherSeq
	 * @param selfParentGen
	 * @param otherParentGen
	 * @param selfParentHash
	 * @param otherParentHash
	 * @param transactions
	 * @param timeCreated
	 * @param signature
	 * @param hash
	 * @param consensusTimeStamp
	 * @param consensusOrder
	 * @return
	 */
	static boolean storeEvent(long creatorId, long creatorSeq, long otherId, long otherSeq, long selfParentGen,
			long otherParentGen, byte[] selfParentHash, byte[] otherParentHash, Transaction[] transactions,
			Instant timeCreated, byte[] signature, byte[] hash, Instant consensusTimeStamp, long consensusOrder) {
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
					"insert into t_events (consensusOrder, creatorNodeId, creatorSeq, otherNodeId, otherSeq, " +
							"selfParentGen, otherParentGen, generation, selfParentConsensusOrder, " +
							"otherParentConsensusOrder, timeCreatedInNanos, signature, consensusTimestampInNanos, " +
							"txsBytesCount, platformTxCount, appTxCount) "
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
			insertEvent.close();

			// store hash
			PreparedStatement insertEventHash = connect.prepareStatement(
					"insert into t_eventHashes (consensusOrder, hash) "
							+ " values (?, ?) ");
			insertEventHash.setLong(1, consensusOrder);
			insertEventHash.setBytes(2, hash);
			insertEventHash.execute();
			log.info(MARKER, "Finished insert to EventHash, consensusOrder: {}", consensusOrder);
			insertEventHash.close();
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
	 *
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
			long consensusOrder;
			if (resultSet.next()) {
				consensusOrder = resultSet.getLong(1);
			} else {
				log.error(MARKER, "There isn't an event's Hash in t_eventHashes matches {} : {}", name, hash);
				consensusOrder = PARENT_HASH_NOT_FOUND_MATCH;
			}
			resultSet.close();
			queryParentConsensusOrder.close();
			return consensusOrder;
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
	 * read and parse a list of EventStream files
	 */
	static public void loadEventStreamFiles(List<String> fileNames) {
		String prevFileHash = "";
		for (String name : fileNames) {
			LoadResult loadResult = loadEventStreamFile(name, prevFileHash);
			if (loadResult == LoadResult.STOP) {
				return;
			}
			prevFileHash = Utility.bytesToHex(Utility.getFileHash(name));
			if (loadResult == LoadResult.OK) {
				Utility.moveFileToParsedDir(name, PARSED_DIR);
			}
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
					loadEventStreamFiles(fullPaths);
				} else {
					log.info(MARKER, "No files to parse");
				}
			} else {
				log.error(LOGM_EXCEPTION, "Exception file {} does not exist", pathName);

			}
		}
	}
}

