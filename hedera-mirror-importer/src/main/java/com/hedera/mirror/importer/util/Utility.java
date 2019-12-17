package com.hedera.mirror.importer.util;



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

import static com.hederahashgraph.api.proto.java.Key.KeyCase.ED25519;

import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

@Log4j2
public class Utility {

    private static final Long SCALAR = 1_000_000_000L;
    private static final String EMPTY_HASH = Hex.encodeHexString(new byte[48]);

    /**
     * Verify if a file's hash is equal to the hash contained in sig file
     *
     * @return
     */
    public static boolean hashMatch(byte[] hash, File rcdFile) {
        byte[] fileHash = Utility.getFileHash(rcdFile.getPath());
        return Arrays.equals(fileHash, hash);
    }

    /**
     * 1. Extract the Hash of the content of corresponding RecordStream file. This Hash is the signed Content of this
     * signature 2. Extract signature from the file.
     *
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
     * @param fileName file name
     * @return byte array of hash value of null if calculating has failed
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

                byte[] array = Files.readAllBytes(Paths.get(fileName));
                return md.digest(array);
            } catch (NoSuchAlgorithmException | IOException e) {
                log.error("Exception {}", e);
                return null;
            }
        }
    }

    /**
     * Calculate SHA384 hash of an event file
     *
     * @param filename file name
     * @return byte array of hash value of null if calculating has failed
     */
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

        // is only used in Version3, for getting the Hash for content after prevFileHash in current file, i.e., hash
        // (c[i])

        try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
            MessageDigest md;
            MessageDigest mdForContent = null;

            md = MessageDigest.getInstance(FileDelimiter.HASH_ALGORITHM);

            int eventStreamFileVersion = dis.readInt();
            md.update(Utility.integerToBytes(eventStreamFileVersion));

            log.debug("Loading event file {} with version {}", filename, eventStreamFileVersion);
            if (eventStreamFileVersion < FileDelimiter.EVENT_STREAM_FILE_VERSION_LEGACY) {
                log.error("EventStream file format version {} doesn't match. File is: {}", eventStreamFileVersion,
                        filename);
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
                        log.error("Unknown event file delimiter {} for file {}", typeDelimiter, file);
                        return null;
                }
            }
            log.trace("Successfully calculated hash for {}", filename);
            if (calculateContentHash) {
                byte[] contentHash = mdForContent.digest();
                md.update(contentHash);
            }

            return md.digest();
        } catch (Exception e) {
            log.error("Error parsing event file {}", filename, e);
            return null;
        }
    }

    /**
     * Calculate SHA384 hash of a record file
     *
     * @param filename file name
     * @return byte array of hash value of null if calculating has failed
     */
    private static byte[] getRecordFileHash(String filename) {
        byte[] readFileHash = new byte[48];

        try (DataInputStream dis = new DataInputStream(new FileInputStream(filename))) {
            MessageDigest md = MessageDigest.getInstance(FileDelimiter.HASH_ALGORITHM);
            MessageDigest mdForContent = MessageDigest.getInstance(FileDelimiter.HASH_ALGORITHM);

            int record_format_version = dis.readInt();
            int version = dis.readInt();

            md.update(Utility.integerToBytes(record_format_version));
            md.update(Utility.integerToBytes(version));

            log.debug("Calculating hash for version {} record file: {}", record_format_version, filename);

            while (dis.available() != 0) {

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
                        log.trace("File {} has signature {}", () -> filename, () -> Hex.encodeHexString(sigBytes));
                        break;
                    default:
                        log.error("Unknown record file delimiter {} for file {}", typeDelimiter, filename);
                        return null;
                }
            }

            if (record_format_version == FileDelimiter.RECORD_FORMAT_VERSION) {
                md.update(mdForContent.digest());
            }

            byte[] fileHash = md.digest();
            log.trace("Calculated file hash for the current file {}", () -> Hex.encodeHexString(fileHash));
            return fileHash;
        } catch (Exception e) {
            log.error("Error reading hash for file {}", filename, e);
            return null;
        }
    }

    public static AccountID stringToAccountID(String string) throws IllegalArgumentException {
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
        return value ? (byte) 1 : (byte) 0;
    }

    public static byte[] instantToBytes(Instant instant) {
        ByteBuffer b = ByteBuffer.allocate(16);
        b.putLong(instant.getEpochSecond()).putLong(instant.getNano());
        return b.array();
    }

    /**
     * return a Timestamp from an instant
     *
     * @param instant
     * @return
     */
    public static Timestamp instantToTimestamp(Instant instant) {
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }

    /**
     * return a string which represents an AccountID
     *
     * @param accountID
     * @return
     */
    public static String accountIDToString(AccountID accountID) {
        return String.format("%d.%d.%d", accountID.getShardNum(),
                accountID.getRealmNum(), accountID.getAccountNum());
    }

    public static Instant convertToInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    /**
     * print a Transaction's content to a String
     *
     * @param transaction
     * @return
     * @throws InvalidProtocolBufferException
     */
    public static String printTransaction(Transaction transaction) throws InvalidProtocolBufferException {
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
     *
     * @param transaction
     * @return
     * @throws InvalidProtocolBufferException
     */
    public static String printTransactionNice(Transaction transaction) throws InvalidProtocolBufferException {
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
     *
     * @param message
     * @return
     * @throws InvalidProtocolBufferException
     */
    public static String printProtoMessage(GeneratedMessageV3 message) {
        return TextFormat.shortDebugString(message);
    }

    public static TransactionBody getTransactionBody(Transaction transaction) throws InvalidProtocolBufferException {
        if (transaction.hasBody()) {
            return transaction.getBody();
        } else {
            return TransactionBody.parseFrom(transaction.getBodyBytes());
        }
    }

    /**
     * Convert bytes to hex.
     *
     * @param bytes to be converted
     * @return converted HexString
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return Hex.encodeHexString(bytes);
    }

    /**
     * parse a timestamp string in file name to Instant
     *
     * @param str
     * @return
     */
    public static Instant parseToInstant(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        Instant result;
        try {
            result = Instant.parse(str);
        } catch (DateTimeParseException ex) {
            result = Instant.parse(str.replace("_", ":"));
        }
        return result;
    }

    /**
     * Parse a s3ObjectSummaryKey to three parts: (1) node AccountID string (2) Instant string (3) file type string For
     * example, for "record0.0.101/2019-06-05T20_29_32.856974Z.rcd_sig", the result would be Triple.of("0.0.101",
     * "2019-06-05T20_29_32.856974Z", "rcd_sig"); for "balance0.0.3/2019-06-21T14_56_00.049967001Z_Balances.csv_sig",
     * the result would be Triple.of("0.0.3", "2019-06-21T14_56_00.049967001Z", "Balances.csv_sig");
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
        while (matcher.find()) {
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

    public static String getAccountIDStringFromFilePath(File file) {
        String regex;
        String path = file.getPath();
        if (isRecordFile(path) || isRecordSigFile(path)) {
            regex = "record([\\d]+[.][\\d]+[.][\\d]+)";
        } else if (isEventStreamFile(path) || isEventStreamSigFile(path)) {
            regex = "events_([\\d]+[.][\\d]+[.][\\d]+)";
        } else {
            //account balance
            regex = "([\\d]+[.][\\d]+[.][\\d]+)/(.+)Z";
        }
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(path);

        String match = null;
        while (matcher.find()) {
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

    /**
     * Convert an Instant to a Long type timestampInNanos
     */
    public static Long convertInstantToNanos(Instant instant) {
        return convertToNanos(instant.getEpochSecond(), instant.getNano());
    }

    /**
     * Converts time in (second, nanos) to time in only nanos.
     */
    public static Long convertToNanos(long second, long nanos) {
        try {
            return Math.addExact(Math.multiplyExact(second, SCALAR), nanos);
        } catch (ArithmeticException e) {
            log.error("Long overflow when converting time to nanos timestamp : {}s {}ns", second, nanos);
            throw e;
        }
    }

    /**
     * Converts time in (second, nanos) to time in only nanos, with a fallback if overflow: If positive overflow, return
     * the max time in the future (Long.MAX_VALUE). If negative overflow, return the max time in the past
     * (Long.MIN_VALUE).
     */
    public static Long convertToNanosMax(long second, long nanos) {
        try {
            return convertToNanos(second, nanos);
        } catch (ArithmeticException ex) {
            return second >= 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    /**
     * Convert Timestamp to a Long type timeStampInNanos
     */
    public static Long timeStampInNanos(Timestamp timestamp) {
        try {
            return Math.addExact(Math.multiplyExact(timestamp.getSeconds(), SCALAR), timestamp.getNanos());
        } catch (ArithmeticException e) {
            throw new ArithmeticException("Long overflow when converting Timestamp to nanos timestamp: " + timestamp);
        }
    }

    public static boolean hashIsEmpty(String hash) {
        return StringUtils.isBlank(hash) || hash.equals(EMPTY_HASH);
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

    public static void purgeDirectory(Path path) {
        File dir = path.toFile();
        if (!dir.exists()) {
            return;
        }

        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                purgeDirectory(file.toPath());
            }
            file.delete();
        }
    }

    public static void ensureDirectory(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Empty path");
        }

        File directory = path.toFile();
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
        ClassLoader[] classLoaders = {Thread
                .currentThread().getContextClassLoader(), Utility.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()};
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

    /**
     * If the protobuf encoding of a Key is a single ED25519 key, return the key as a String with lowercase hex
     * encoding.
     *
     * @param protobufKey
     * @return ED25519 public key as a String in hex encoding, or null
     * @throws InvalidProtocolBufferException if the protobufKey is not a valid protobuf encoding of a Key
     *                                        (BasicTypes.proto)
     */
    public static @Nullable
    String protobufKeyToHexIfEd25519OrNull(@Nullable byte[] protobufKey)
            throws InvalidProtocolBufferException {
        if ((null == protobufKey) || (0 == protobufKey.length)) {
            return null;
        }

        var parsedKey = Key.parseFrom(protobufKey);
        if (ED25519 != parsedKey.getKeyCase()) {
            return null;
        }

        return Hex.encodeHexString(parsedKey.getEd25519().toByteArray(), true);
    }

    /**
     * Generates a TransactionID object
     *
     * @param payerAccountId the AccountID of the transaction payer account
     * @return
     */
    public static TransactionID getTransactionId(AccountID payerAccountId) {
        Timestamp validStart = Utility.instantToTimestamp(Instant.now());
        return TransactionID.newBuilder().setAccountID(payerAccountId).setTransactionValidStart(validStart).build();
    }
}
