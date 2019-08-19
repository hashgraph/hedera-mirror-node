package com.hedera.parser;

import com.hedera.configLoader.ConfigLoader;
import com.hedera.configLoader.ConfigLoader.OPERATION_TYPE;
import com.hedera.databaseUtilities.DatabaseUtilities;
import com.hedera.platform.Transaction;
import com.hedera.recordFileLogger.LoggerStatus;
import com.hedera.utilities.Utility;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class EventStreamFileParser {
	private static final Logger log = LogManager.getLogger("eventStream-log");
	private static final Marker MARKER = MarkerManager.getMarker("EVENT_STREAM");
	private static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");

	private static final byte TYPE_PREV_HASH = 1;       // next 48 bytes are hash384 or previous files
	private static final byte EVENT_STREAM_FILE_VERSION_CURRENT = 3;
	private static final byte EVENT_STREAM_FILE_VERSION_LEGACY = 2;
	private static final byte STREAM_EVENT_VERSION = 2;
	private static final byte STREAM_EVENT_START_NO_TRANS_WITH_VERSION = 0x5b;
	private static final byte STREAM_EVENT_START_WITH_VERSION = 0x5a;
	private static final byte commEventLast = 0x46;

	private static Connection connect = null;

	private static ConfigLoader configLoader;
	private static LoggerStatus loggerStatus = new LoggerStatus();

	private static final Long PARENT_HASH_NULL = null;
	private static final long PARENT_HASH_NOT_FOUND_MATCH = -2;

	private static final String HASH_ALGORITHM = "SHA-384";

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

		if (file.exists() == false) {
			log.info(MARKER, "File does not exist " + fileName);
			return LoadResult.ERROR;
		}

		try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
			md = MessageDigest.getInstance(HASH_ALGORITHM);

			long counter = 0;
			int eventStreamFileVersion = dis.readInt();
			md.update(Utility.integerToBytes(eventStreamFileVersion));

			log.info(MARKER, "EventStream file format version " + eventStreamFileVersion);
			if (eventStreamFileVersion < EVENT_STREAM_FILE_VERSION_LEGACY) {
				log.error(MARKER, "EventStream file format version doesn't match.");
				return LoadResult.ERROR;
			} else if (eventStreamFileVersion >= EVENT_STREAM_FILE_VERSION_CURRENT) {
				mdForContent = MessageDigest.getInstance(HASH_ALGORITHM);
				calculateContentHash = true;
			}

			while (dis.available() != 0) {
				try {
					byte typeDelimiter = dis.readByte();
					switch (typeDelimiter) {
						case TYPE_PREV_HASH:
							md.update(typeDelimiter);
							byte[] readPrevFileHashBytes = new byte[48];
							dis.readFully(readPrevFileHashBytes);
							md.update(readPrevFileHashBytes);
							if (previousFileHash.isEmpty()) {
								log.error(MARKER, "Previous file Hash not available");
								previousFileHash = Hex.encodeHexString(readPrevFileHashBytes);
							} else {
								log.info(MARKER, "Previous file Hash = " + previousFileHash);
							}
							readPrevFileHash = Hex.encodeHexString(readPrevFileHashBytes);
							log.info(MARKER, "Read Previous file Hash = " + readPrevFileHash);

							if (!Arrays.equals(new byte[48], readPrevFileHashBytes) && !readPrevFileHash.contentEquals(
									previousFileHash)) {
								if (configLoader.getStopLoggingIfEventHashMismatchAfter().compareTo(fileName) < 0) {
									// last file for which mismatch is allowed is in the past
									log.error(MARKER,
											"Previous file Hash Mismatch - stopping loading. fileName = {}, Previous " +
													"=" +
													" " +
													"{}, Current = {}",
											fileName, previousFileHash, readPrevFileHash);
									return LoadResult.STOP;
								}
							}
							break;

						case STREAM_EVENT_START_NO_TRANS_WITH_VERSION:
							if (calculateContentHash) {
								mdForContent.update(typeDelimiter);
								if (!loadEvent(dis, mdForContent, true)) {
									return LoadResult.STOP;
								}
							} else {
								md.update(typeDelimiter);
								if (!loadEvent(dis, md, true)) {
									return LoadResult.STOP;
								}
							}
							counter++;
							break;
						case STREAM_EVENT_START_WITH_VERSION:
							if (calculateContentHash) {
								mdForContent.update(typeDelimiter);
								if (!loadEvent(dis, mdForContent, false)) {
									return LoadResult.STOP;
								}
							} else {
								md.update(typeDelimiter);
								if (!loadEvent(dis, md, false)) {
									return LoadResult.STOP;
								}
							}
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
		} catch (NoSuchAlgorithmException e) {
			log.error(LOGM_EXCEPTION, "Exception {}", e);
			return LoadResult.ERROR;
		}

		if (calculateContentHash) {
			byte[] contentHash = mdForContent.digest();
			md.update(contentHash);
		}
		String thisFileHash = Utility.bytesToHex(md.digest());

		loggerStatus.setLastProcessedEventHash(thisFileHash);
		loggerStatus.saveToFile();
		return LoadResult.OK;
	}

	static boolean loadEvent(DataInputStream dis, MessageDigest md, boolean noTxs) throws IOException {
		if (dis.readInt() != STREAM_EVENT_VERSION) {
			log.error(MARKER, "EventStream format version doesn't match.");
			return false;
		}
		md.update(Utility.integerToBytes(STREAM_EVENT_VERSION));

		long creatorId = dis.readLong();
		md.update(Utility.longToBytes(creatorId));

		long creatorSeq = dis.readLong();
		md.update(Utility.longToBytes(creatorSeq));

		long otherId = dis.readLong();
		md.update(Utility.longToBytes(otherId));

		long otherSeq = dis.readLong();
		md.update(Utility.longToBytes(otherSeq));

		long selfParentGen = dis.readLong();
		md.update(Utility.longToBytes(selfParentGen));

		long otherParentGen = dis.readLong();
		md.update(Utility.longToBytes(otherParentGen));

		byte[] selfParentHash = readNullableByteArray(dis, md);
		byte[] otherParentHash = readNullableByteArray(dis, md);

		//counts[0] denotes the number of bytes in the Transaction Array
		//counts[1] denotes the number of system Transactions
		//counts[2] denotes the number of application Transactions
		int[] counts = new int[3];

		Transaction[] transactions = new Transaction[0];
		if (!noTxs) {
			transactions = Transaction.readArray(dis, counts, md);
		}

		Instant timeCreated = readInstant(dis);
		md.update(Utility.instantToBytes(timeCreated));

		byte[] signature = readByteArray(dis, md);
		if (dis.readByte() != commEventLast) {
			log.warn(MARKER, "event end marker incorrect");
			return false;
		}
		md.update(commEventLast);

		// event's hash
		byte[] hash = readByteArray(dis, md);

		Instant consensusTimeStamp = readInstant(dis);
		md.update(Utility.instantToBytes(consensusTimeStamp));

		long consensusOrder = dis.readLong();
		md.update(Utility.longToBytes(consensusOrder));

		log.debug(MARKER,
				"Loaded Event: creatorId: {}, creatorSeq: {}, otherId: {}, otherSeq: {}, selfParentGen: {}, " +
						"otherParentGen: {}, selfParentHash: {}, otherParentHash: {}, transactions: {}, timeCreated: " +
						"{}, signature: {}, hash: {}, consensusTimeStamp: {}, consensusOrder: {}",
				creatorId, creatorSeq, otherId, otherSeq, selfParentGen, otherParentGen,
				Utility.bytesToHex(selfParentHash), Utility.bytesToHex(otherParentHash), transactions, timeCreated,
				Utility.bytesToHex(signature), Utility.bytesToHex(hash), consensusTimeStamp, consensusOrder);

		if (storeEvent(creatorId, creatorSeq, otherId, otherSeq, selfParentGen, otherParentGen, selfParentHash,
				otherParentHash, counts, timeCreated, signature, hash, consensusTimeStamp, consensusOrder)) {
			return true;
		} else {
			return false;
		}
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
	 * @param txCounts
	 * @param timeCreated
	 * @param signature
	 * @param hash
	 * @param consensusTimeStamp
	 * @param consensusOrder
	 * @return
	 */
	static boolean storeEvent(long creatorId, long creatorSeq, long otherId, long otherSeq, long selfParentGen,
			long otherParentGen, byte[] selfParentHash, byte[] otherParentHash, int[] txCounts,
			Instant timeCreated, byte[] signature, byte[] hash, Instant consensusTimeStamp, long consensusOrder) {
		try {
			long generation = Math.max(selfParentGen, otherParentGen) + 1;
			Long self_parent_id = null;
			if (selfParentHash != null) {
				self_parent_id = getIdForParent(selfParentHash, "selfParentHash");
			}
			Long other_parent_id = null;
			if (otherParentHash != null) {
				other_parent_id = getIdForParent(otherParentHash, "otherParentHash");
			}

			int txsBytesCount = txCounts[0];
			int platformTxCount = txCounts[1];
			int appTxCount = txCounts[2];

			long timeCreatedInNanos = Utility.convertInstantToNanos(timeCreated);
			long consensusTimestampInNanos = Utility.convertInstantToNanos(consensusTimeStamp);
			PreparedStatement insertEvent = connect.prepareStatement(
					"insert into t_events (consensus_order, creator_node_id, creator_seq, other_node_id, other_seq, " +
							"self_parent_generation, other_parent_generation, generation, self_parent_id, " +
							"other_parent_id, created_timestamp_ns, signature, consensus_timestamp_ns, " +
							"txs_bytes_count, platform_tx_count, app_tx_count, latency_ns, hash, self_parent_hash, " +
							"other_parent_hash) "
							+ " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ");

			insertEvent.setLong(1, consensusOrder);
			insertEvent.setLong(2, creatorId);
			insertEvent.setLong(3, creatorSeq);
			insertEvent.setLong(4, otherId);
			insertEvent.setLong(5, otherSeq);
			insertEvent.setLong(6, selfParentGen);
			insertEvent.setLong(7, otherParentGen);
			insertEvent.setLong(8, generation);
			if (self_parent_id != null && self_parent_id >= 0) {
				insertEvent.setLong(9, self_parent_id);
			} else {
				insertEvent.setNull(9, Types.BIGINT);
			}
			if (other_parent_id != null && other_parent_id >= 0) {
				insertEvent.setLong(10, other_parent_id);
			} else {
				insertEvent.setNull(10, Types.BIGINT);
			}
			insertEvent.setLong(11, timeCreatedInNanos);
			insertEvent.setBytes(12, signature);
			insertEvent.setLong(13, consensusTimestampInNanos);
			insertEvent.setInt(14, txsBytesCount);
			insertEvent.setInt(15, platformTxCount);
			insertEvent.setInt(16, appTxCount);
			insertEvent.setLong(17, consensusTimestampInNanos - timeCreatedInNanos);
			insertEvent.setBytes(18, hash);
			insertEvent.setBytes(19, selfParentHash);
			insertEvent.setBytes(20, otherParentHash);
			insertEvent.execute();
			log.info(MARKER, "Finished insert to Event, consensusOrder: {}", consensusOrder);
			insertEvent.close();
		} catch (SQLException ex) {
			log.error(LOGM_EXCEPTION, "Exception {}", ex);
			return false;
		}
		return true;
	}

	/**
	 * Find an event's id in t_events table which hash value matches the given byte array
	 * return PARENT_HASH_NULL if the byte array is null;
	 * return PARENT_HASH_NOT_FOUND_MATCH if didn't find a match;
	 *
	 * @param hash
	 * @param name
	 * @return
	 * @throws SQLException
	 */
	static long getIdForParent(byte[] hash, String name) throws SQLException {
		if (hash == null) {
			return PARENT_HASH_NULL;
		} else {
			PreparedStatement query = connect.prepareStatement(
					"SELECT id FROM t_events WHERE hash = ?");
			query.setBytes(1, hash);
			query.execute();
			ResultSet resultSet = query.getResultSet();
			long id;
			if (resultSet.next()) {
				id = resultSet.getLong(1);
			} else {
				log.error(MARKER, "There isn't an event's Hash in t_events matches {} : {}", name, hash);
				id = PARENT_HASH_NOT_FOUND_MATCH;
			}
			resultSet.close();
			query.close();
			return id;
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

	static byte[] readByteArray(DataInputStream dis, MessageDigest md)
			throws IOException {
		int len = dis.readInt();
		md.update(Utility.integerToBytes(len));
		return readByteArrayOfLength(dis, len, md);
	}

	static byte[] readNullableByteArray(DataInputStream dis, MessageDigest md) throws IOException {
		int len = dis.readInt();
		md.update(Utility.integerToBytes(len));
		if (len < 0) {
			return null;
		} else {
			return readByteArrayOfLength(dis, len, md);
		}
	}

	private static byte[] readByteArrayOfLength(DataInputStream dis, int len, MessageDigest md) throws IOException {
		int checksum = dis.readInt();
		md.update(Utility.integerToBytes(checksum));

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
		md.update(data);
		return data;
	}

	/**
	 * read and parse a list of EventStream files
	 */
	static public boolean loadEventStreamFiles(List<String> fileNames) {
		String prevFileHash = loggerStatus.getLastProcessedEventHash();
		for (String name : fileNames) {
			if (Utility.checkStopFile()) {
				log.info(MARKER, "Stop file found, stopping.");
				return false;
			}
			LoadResult loadResult = loadEventStreamFile(name, prevFileHash);
			if (loadResult == LoadResult.STOP) {
				return false;
			}
			prevFileHash = loggerStatus.getLastProcessedEventHash();
			if (loadResult == LoadResult.OK) {
				Utility.moveFileToParsedDir(name, PARSED_DIR);
			}
		}
		return true;
	}

	/**
	 * Given a eventStream file name, read its prevFileHash
	 *
	 * @param fileName
	 * 		the name of eventStream file to read
	 * @return return previous file hash's Hex String
	 */
	static public String readPrevFileHash(String fileName) {
		File file = new File(fileName);
		String readPrevFileHash;

		if (file.exists() == false) {
			log.info(MARKER, "File does not exist " + fileName);
			return null;
		}

		try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
			int eventStreamFileVersion = dis.readInt();

			log.info(MARKER, "EventStream file format version " + eventStreamFileVersion);
			if (eventStreamFileVersion < EVENT_STREAM_FILE_VERSION_LEGACY) {
				log.error(MARKER, "EventStream file format version doesn't match.");
				return null;
			}
			while (dis.available() != 0) {
				byte typeDelimiter = dis.readByte();
				if (typeDelimiter == TYPE_PREV_HASH) {
					byte[] readPrevFileHashBytes = new byte[48];
					dis.read(readPrevFileHashBytes);
					readPrevFileHash = Hex.encodeHexString(readPrevFileHashBytes);
					return readPrevFileHash;
				} else {
					log.error(LOGM_EXCEPTION, "Exception Unknown record file delimiter {}", typeDelimiter);
					return null;
				}
			}
		} catch (FileNotFoundException e) {
			log.error(MARKER, "File Not Found Error");
			return null;
		} catch (IOException e) {
			log.error(MARKER, "IOException Error");
			return null;
		}
		return null;
	}

	public static boolean parseNewFiles(String pathName) {
		if (pathName == null) return false;
		log.info(MARKER, "EventStream files folder got from configuration file: {}", pathName);

		File file = new File(pathName);
		// if the path doesn't exist, and it denotes a file, return false to stop processing;
		// if the path doesn't exist, and it denotes a path, we try to create the directories, and return false when creation fails
		if (!file.exists()) {
			if (file.isFile()) {
				log.info(MARKER, "File {} does not exist", pathName);
				return false;
			} else if (!file.mkdirs()) {
				log.info(MARKER, "Directory {} does not exist and cannot be created", pathName);
				return false;
			}
		}
		configLoader = new ConfigLoader();

		if (Utility.checkStopFile()) {
			log.info(MARKER, "Stop file found, stopping.");
			return false;
		}

		connect = DatabaseUtilities.openDatabase(connect);

		boolean result = true;
		if (file.isFile()) {
			log.info(MARKER, "Loading eventStream file {} ", pathName);
			if (loadEventStreamFile(pathName, loggerStatus.getLastProcessedEventHash()) == LoadResult.STOP) {
				result = false;
			}
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
				result = loadEventStreamFiles(fullPaths);
			} else {
				log.info(MARKER, "No files to parse");
			}
		} else {
			log.error(LOGM_EXCEPTION, "Exception file {} does not exist", pathName);

		}

		try {
			connect = DatabaseUtilities.closeDatabase(connect);
		} catch (SQLException e) {
			log.error(LOGM_EXCEPTION, "Exception {}", e);
		}

		return result;
	}

	public static void main(String[] args) {
		String pathName;

		while (true) {
			if (Utility.checkStopFile()) {
				log.info(MARKER, "Stop file found, exiting.");
				System.exit(0);
			}

			pathName = ConfigLoader.getDefaultParseDir(OPERATION_TYPE.EVENTS);
			log.info(MARKER, "Event files folder got from configuration file: {}", pathName);

			if (pathName != null && !parseNewFiles(pathName)) {
				break;
			}
		}
	}
}
