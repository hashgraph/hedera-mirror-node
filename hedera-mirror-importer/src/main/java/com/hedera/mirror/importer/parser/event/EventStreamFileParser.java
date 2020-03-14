package com.hedera.mirror.importer.parser.event;

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

import com.google.common.base.Stopwatch;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;
import org.springframework.scheduling.annotation.Scheduled;

import com.hedera.mirror.importer.domain.ApplicationStatusCode;
import com.hedera.mirror.importer.parser.FileParser;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;
import com.hedera.mirror.importer.util.DatabaseUtilities;
import com.hedera.mirror.importer.util.FileDelimiter;
import com.hedera.mirror.importer.util.ShutdownHelper;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
public class EventStreamFileParser implements FileParser {

    private static final Long PARENT_HASH_NULL = null;
    private static final long PARENT_HASH_NOT_FOUND_MATCH = -2;
    private static final String PARSED_DIR = "/parsedEventStreamFiles/";
    private static Connection connect = null;
    private final ApplicationStatusRepository applicationStatusRepository;
    private final EventParserProperties parserProperties;

    public EventStreamFileParser(ApplicationStatusRepository applicationStatusRepository,
                                 EventParserProperties parserProperties) {
        this.applicationStatusRepository = applicationStatusRepository;
        this.parserProperties = parserProperties;
    }

    /**
     * Given a eventStream file name, read its prevFileHash
     *
     * @param fileName the name of eventStream file to read
     * @return return previous file hash's Hex String
     */
    static public String readPrevFileHash(String fileName) {
        File file = new File(fileName);
        String readPrevFileHash;

        if (file.exists() == false) {
            log.info("File does not exist {}", fileName);
            return null;
        }

        try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
            int eventStreamFileVersion = dis.readInt();

            log.trace("EventStream file format version {}", eventStreamFileVersion);
            if (eventStreamFileVersion < FileDelimiter.EVENT_STREAM_FILE_VERSION_LEGACY) {
                log.error("EventStream file format version doesn't match");
                return null;
            }
            while (dis.available() != 0) {
                byte typeDelimiter = dis.readByte();
                if (typeDelimiter == FileDelimiter.EVENT_TYPE_PREV_HASH) {
                    byte[] readPrevFileHashBytes = new byte[48];
                    dis.read(readPrevFileHashBytes);
                    readPrevFileHash = Hex.encodeHexString(readPrevFileHashBytes);
                    return readPrevFileHash;
                } else {
                    log.error("Unknown record file delimiter {} for file {}", typeDelimiter, file);
                    return null;
                }
            }
        } catch (Exception e) {
            log.error("Error reading previous file hash for file {}", file, e);
        }
        return null;
    }

    /**
     * Given a EventStream file name, read and parse and return as a list of service record pair
     *
     * @param fileName         the name of record file to read
     * @param previousFileHash previous file hash
     * @throws Exception
     */
    private LoadResult loadEventStreamFile(String fileName, String previousFileHash) throws Exception {

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
            log.info("File does not exist {}", fileName);
            return LoadResult.ERROR;
        }

        Stopwatch stopwatch = Stopwatch.createStarted();

        try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
            md = MessageDigest.getInstance(FileDelimiter.HASH_ALGORITHM);

            long counter = 0;
            int eventStreamFileVersion = dis.readInt();
            md.update(Utility.integerToBytes(eventStreamFileVersion));

            log.debug("Loading event file {} with version {}", fileName, eventStreamFileVersion);
            if (eventStreamFileVersion < FileDelimiter.EVENT_STREAM_FILE_VERSION_LEGACY) {
                log.error("EventStream file format version doesn't match.");
                return LoadResult.ERROR;
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
                        if (previousFileHash.isEmpty()) {
                            log.error("Previous file hash not available");
                            previousFileHash = Hex.encodeHexString(readPrevFileHashBytes);
                        }
                        readPrevFileHash = Hex.encodeHexString(readPrevFileHashBytes);

                        if (!Arrays.equals(new byte[48], readPrevFileHashBytes) && !readPrevFileHash.contentEquals(
                                previousFileHash)) {
                            if (applicationStatusRepository
                                    .findByStatusCode(ApplicationStatusCode.EVENT_HASH_MISMATCH_BYPASS_UNTIL_AFTER)
                                    .compareTo(fileName) < 0) {
                                // last file for which mismatch is allowed is in the past
                                log.error("Hash mismatch for file {}. Previous = {}, Current = {}", fileName,
                                        previousFileHash, readPrevFileHash);
                                return LoadResult.STOP;
                            }
                        }
                        break;

                    case FileDelimiter.EVENT_STREAM_START_NO_TRANS_WITH_VERSION:
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
                    case FileDelimiter.EVENT_STREAM_START_WITH_VERSION:
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
                        log.error("Unknown record file delimiter {} for file", typeDelimiter, file);
                }
            }
            log.info("Loaded {} events successfully from {} in {}", counter, fileName, stopwatch);
        } catch (Exception e) {
            log.error("Error parsing event file {} after {}", fileName, stopwatch, e);
            return LoadResult.ERROR;
        }

        if (calculateContentHash) {
            byte[] contentHash = mdForContent.digest();
            md.update(contentHash);
        }
        String thisFileHash = Utility.bytesToHex(md.digest());
        if (!Utility.hashIsEmpty(thisFileHash)) {
            applicationStatusRepository
                    .updateStatusValue(ApplicationStatusCode.LAST_PROCESSED_EVENT_HASH, thisFileHash);
        }
        return LoadResult.OK;
    }

    private boolean loadEvent(DataInputStream dis, MessageDigest md, boolean noTxs) throws IOException {
        if (dis.readInt() != FileDelimiter.EVENT_STREAM_VERSION) {
            log.error("EventStream format version doesn't match.");
            return false;
        }
        md.update(Utility.integerToBytes(FileDelimiter.EVENT_STREAM_VERSION));

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
        if (dis.readByte() != FileDelimiter.EVENT_COMM_EVENT_LAST) {
            log.warn("Event end marker incorrect");
            return false;
        }
        md.update(FileDelimiter.EVENT_COMM_EVENT_LAST);

        // event's hash
        byte[] hash = readByteArray(dis, md);

        Instant consensusTimeStamp = readInstant(dis);
        md.update(Utility.instantToBytes(consensusTimeStamp));

        long consensusOrder = dis.readLong();
        md.update(Utility.longToBytes(consensusOrder));

        if (log.isTraceEnabled()) {
            log.trace("Loaded Event: creatorId: {}, creatorSeq: {}, otherId: {}, otherSeq: {}, selfParentGen: {}, " +
                            "otherParentGen: {}, selfParentHash: {}, otherParentHash: {}, transactions: {}, " +
                            "timeCreated: " +
                            "{}, signature: {}, hash: {}, consensusTimeStamp: {}, consensusOrder: {}",
                    creatorId, creatorSeq, otherId, otherSeq, selfParentGen, otherParentGen,
                    Utility.bytesToHex(selfParentHash), Utility.bytesToHex(otherParentHash), transactions, timeCreated,
                    Utility.bytesToHex(signature), Utility.bytesToHex(hash), consensusTimeStamp, consensusOrder);
        }

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
    private boolean storeEvent(long creatorId, long creatorSeq, long otherId, long otherSeq, long selfParentGen,
                               long otherParentGen, byte[] selfParentHash, byte[] otherParentHash, int[] txCounts,
                               Instant timeCreated, byte[] signature, byte[] hash, Instant consensusTimeStamp,
                               long consensusOrder) {
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
            log.info("Successfully stored event with consensusOrder {}", consensusOrder);
            insertEvent.close();
        } catch (Exception ex) {
            log.error("Error storing event", ex);
            return false;
        }
        return true;
    }

    /**
     * Find an event's id in t_events table which hash value matches the given byte array return PARENT_HASH_NULL if the
     * byte array is null; return PARENT_HASH_NOT_FOUND_MATCH if didn't find a match;
     *
     * @param hash
     * @param name
     * @return
     * @throws SQLException
     */
    private long getIdForParent(byte[] hash, String name) throws SQLException {
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
                log.error("There isn't an event's hash in the database that matches {}: {}", hash, name);
                id = PARENT_HASH_NOT_FOUND_MATCH;
            }
            resultSet.close();
            query.close();
            return id;
        }
    }

    /**
     * read an Instant from a data stream
     */
    private Instant readInstant(DataInput dis)
            throws IOException {
        Instant time = Instant.ofEpochSecond(//
                dis.readLong(), // from getEpochSecond()
                dis.readLong()); // from getNano()
        return time;
    }

    private byte[] readByteArray(DataInputStream dis, MessageDigest md)
            throws IOException {
        int len = dis.readInt();
        md.update(Utility.integerToBytes(len));
        return readByteArrayOfLength(dis, len, md);
    }

    private byte[] readNullableByteArray(DataInputStream dis, MessageDigest md) throws IOException {
        int len = dis.readInt();
        md.update(Utility.integerToBytes(len));
        if (len < 0) {
            return null;
        } else {
            return readByteArrayOfLength(dis, len, md);
        }
    }

    private byte[] readByteArrayOfLength(DataInputStream dis, int len, MessageDigest md) throws IOException {
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
     *
     * @throws Exception
     */
    private boolean loadEventStreamFiles(List<String> fileNames) throws Exception {

        String prevFileHash = applicationStatusRepository
                .findByStatusCode(ApplicationStatusCode.LAST_PROCESSED_EVENT_HASH);
        for (String name : fileNames) {
            if (ShutdownHelper.isStopping()) {
                return false;
            }
            LoadResult loadResult = loadEventStreamFile(name, prevFileHash);
            if (loadResult == LoadResult.STOP) {
                return false;
            }
            prevFileHash = applicationStatusRepository
                    .findByStatusCode(ApplicationStatusCode.LAST_PROCESSED_EVENT_HASH);
            if (loadResult == LoadResult.OK) {
                Utility.moveOrDeleteParsedFile(name, parserProperties);
            }
        }
        return true;
    }

    @Override
    @Scheduled(fixedRateString = "${hedera.mirror.parser.event.frequency:60000}")
    public void parse() {
        try {
            if (!parserProperties.isEnabled()) {
                return;
            }

            if (ShutdownHelper.isStopping()) {
                return;
            }

            Path path = parserProperties.getValidPath();
            log.info("Parsing event files from {}", path);
            File file = path.toFile();
            connect = DatabaseUtilities.openDatabase(connect);

            boolean result = true;
            if (file.isFile()) {
                log.info("Loading event file {}", path);
                if (loadEventStreamFile(path.toString(), applicationStatusRepository
                        .findByStatusCode(ApplicationStatusCode.LAST_PROCESSED_EVENT_HASH)) == LoadResult.STOP) {
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

                log.info("Loading {} event files from directory {}", fullPaths.size(), path);

                log.trace("Event files: {}", fullPaths);
                result = loadEventStreamFiles(fullPaths);
            } else {
                log.error("Event file {} does not exist", path);
            }

            try {
                connect = DatabaseUtilities.closeDatabase(connect);
            } catch (SQLException e) {
                log.error("Error closing database connection", e);
            }
        } catch (Exception e) {
            log.error("Error parsing events files", e);
        }
    }

    private enum LoadResult {
        OK, STOP, ERROR
    }
}
