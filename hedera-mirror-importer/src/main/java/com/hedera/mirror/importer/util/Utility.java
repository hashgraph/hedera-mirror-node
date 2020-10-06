package com.hedera.mirror.importer.util;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import com.google.common.primitives.Ints;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.parser.domain.RecordItem;

@Log4j2
@UtilityClass
public class Utility {

    public static final Instant MAX_INSTANT_LONG = Instant.ofEpochSecond(0, Long.MAX_VALUE);

    private static final Long SCALAR = 1_000_000_000L;
    private static final String EMPTY_HASH = Hex.encodeHexString(new byte[48]);

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
     * Calculate SHA384 hash of a balance file
     *
     * @param fileName file name
     * @return hash in hex or null if calculating has failed
     */
    public static String getBalanceFileHash(String fileName) {
        try {
            MessageDigest md = MessageDigest.getInstance(FileDelimiter.HASH_ALGORITHM);
            byte[] array = Files.readAllBytes(Paths.get(fileName));
            return Utility.bytesToHex(md.digest(array));
        } catch (NoSuchAlgorithmException | IOException e) {
            log.error(e);
            return null;
        }
    }

    /**
     * Parses record stream file.
     *
     * @param filePath           path to record file
     * @param recordItemConsumer if not null, consumer is invoked for each transaction in the record file
     * @return parsed record file
     */
    public static RecordFile parseRecordFile(String filePath, Consumer<RecordItem> recordItemConsumer) {
        RecordFile recordFile = new RecordFile();
        String fileName = FilenameUtils.getName(filePath);
        recordFile.setName(fileName);

        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(filePath)))) {
            MessageDigest md = MessageDigest.getInstance(FileDelimiter.HASH_ALGORITHM);
            MessageDigest mdForContent = md;

            int recordFormatVersion = readInt(dis, md);
            if (recordFormatVersion >= FileDelimiter.RECORD_FORMAT_VERSION) {
                mdForContent = MessageDigest.getInstance(FileDelimiter.HASH_ALGORITHM);
            }

            readInt(dis, md); // version
            log.info("Loading record format version {} from record file: {}", recordFormatVersion, fileName);
            recordFile.setRecordFormatVersion(recordFormatVersion);

            log.debug("Calculating hash for version {} record file: {}", recordFormatVersion, fileName);

            long count = 0;
            while (dis.available() != 0) {
                byte typeDelimiter = dis.readByte();
                switch (typeDelimiter) {
                    case FileDelimiter.RECORD_TYPE_PREV_HASH:
                        md.update(typeDelimiter);
                        byte[] readFileHash = new byte[48];
                        dis.read(readFileHash);
                        String previousHash = Hex.encodeHexString(readFileHash);
                        recordFile.setPreviousHash(previousHash);
                        md.update(readFileHash);
                        break;

                    case FileDelimiter.RECORD_TYPE_RECORD:
                        mdForContent.update(typeDelimiter);
                        byte[] transactionRawBytes = readBytes(dis, readInt(dis, mdForContent), mdForContent);
                        byte[] recordRawBytes = readBytes(dis, readInt(dis, mdForContent), mdForContent);

                        boolean isFirstTransaction = recordFile.getConsensusStart() == null;
                        boolean isLastTransaction = dis.available() == 0;

                        // We need the first and last transaction timestamps for metrics
                        if (recordItemConsumer != null || isFirstTransaction || isLastTransaction) {
                            RecordItem recordItem = new RecordItem(transactionRawBytes, recordRawBytes);

                            if (recordItemConsumer != null) {
                                recordItemConsumer.accept(recordItem);
                            }

                            if (isFirstTransaction) {
                                recordFile.setConsensusStart(recordItem.getConsensusTimestamp());
                            }

                            if (isLastTransaction) {
                                recordFile.setConsensusEnd(recordItem.getConsensusTimestamp());
                            }
                        }
                        count++;
                        break;
                    default:
                        throw new IllegalArgumentException(String.format(
                                "Unknown record file delimiter %s for file %s", typeDelimiter, fileName));
                }
            }
            if (recordFormatVersion == FileDelimiter.RECORD_FORMAT_VERSION) {
                md.update(mdForContent.digest());
            }
            if (recordFile.getPreviousHash() == null) {
                throw new IllegalArgumentException("previous hash is null in file " + fileName);
            }
            recordFile.setFileHash(Hex.encodeHexString(md.digest()));
            log.trace("Calculated file hash for the record file {}", recordFile.getFileHash());
            recordFile.setCount(count);

            return recordFile;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error parsing bad record file " + fileName, e);
        }
    }

    /**
     * Reads int from the input stream and updates the digest.
     */
    public static int readInt(DataInputStream dis, MessageDigest md) throws IOException {
        int value = dis.readInt();
        md.update(Ints.toByteArray(value));
        return value;
    }

    /**
     * Reads given number of bytes from the input stream and updates the digest.
     */
    public static byte[] readBytes(DataInputStream dis, int len, MessageDigest md) throws IOException {
        byte[] bytes = new byte[len];
        dis.readFully(bytes);
        md.update(bytes);
        return bytes;
    }

    /**
     * @return Timestamp from an instant
     */
    public static Timestamp instantToTimestamp(Instant instant) {
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }

    /**
     * @return string which represents an AccountID
     */
    public static String accountIDToString(AccountID accountID) {
        return String.format("%d.%d.%d", accountID.getShardNum(),
                accountID.getRealmNum(), accountID.getAccountNum());
    }

    public static Instant convertToInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    /**
     * print a protobuf Message's content to a String
     *
     * @param message
     * @return
     */
    public static String printProtoMessage(GeneratedMessageV3 message) {
        return TextFormat.shortDebugString(message);
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
            if (timestamp == null) {
                return null;
            }
            return Math.addExact(Math.multiplyExact(timestamp.getSeconds(), SCALAR), timestamp.getNanos());
        } catch (ArithmeticException e) {
            throw new ArithmeticException("Long overflow when converting Timestamp to nanos timestamp: " + timestamp);
        }
    }

    public static Long timestampInNanosMax(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return convertToNanosMax(timestamp.getSeconds(), timestamp.getNanos());
    }

    public static boolean hashIsEmpty(String hash) {
        return StringUtils.isBlank(hash) || hash.equals(EMPTY_HASH);
    }

    // Moves a file in the form 2019-08-30T18_10_00.419072Z.rcd to destinationRoot/2019/08/30
    public static void archiveFile(File source, Path destinationRoot) {
        String filename = source.getName();
        String date = filename.substring(0, 10).replace("-", File.separator);
        Path destination = destinationRoot.resolve(date);

        try {
            destination.toFile().mkdirs();
            Files.move(source.toPath(), destination.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            log.trace("Moved {} to {}", source, destination);
        } catch (Exception e) {
            log.error("Error moving file {} to {}", source, destination, e);
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

    public static final Instant getInstantFromFilename(String filename) {
        if (StringUtils.isBlank(filename)) {
            return Instant.EPOCH;
        }

        StreamType streamType = StreamType.fromFilename(filename);
        String date = FilenameUtils.removeExtension(filename);

        if (streamType != null) {
            date = StringUtils.removeEnd(date, streamType.getSuffix());
        }

        date = date.replace('_', ':');
        return Instant.parse(date);
    }

    public static final long getTimestampFromFilename(String filename) {
        Instant instant = getInstantFromFilename(filename);
        return Utility.convertToNanosMax(instant.getEpochSecond(), instant.getNano());
    }

    public static final String getStreamFilenameFromInstant(StreamType streamType, Instant instant) {
        String timestamp = instant.toString().replace(':', '_');
        return timestamp + streamType.getSuffix() + "." + streamType.getExtension();
    }

    public static final boolean isStreamFileAfterInstant(String filename, Instant instant) {
        return instant != null && getInstantFromFilename(filename).isAfter(instant);
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
    String protobufKeyToHexIfEd25519OrNull(@Nullable byte[] protobufKey) {
        if ((null == protobufKey) || (0 == protobufKey.length)) {
            return null;
        }

        try {
            var parsedKey = Key.parseFrom(protobufKey);
            if (ED25519 != parsedKey.getKeyCase()) {
                return null;
            }

            return Hex.encodeHexString(parsedKey.getEd25519().toByteArray(), true);
        } catch (InvalidProtocolBufferException protoEx) {
            log.error("Invalid protobuf Key, could parse key", protoEx);
            return null;
        } catch (Exception e) {
            log.error("Invalid ED25519 key could not be translated to hex text.", e);
            return null;
        }
    }

    /**
     * Generates a TransactionID object
     *
     * @param payerAccountId the AccountID of the transaction payer account
     */
    public static TransactionID getTransactionId(AccountID payerAccountId) {
        Timestamp validStart = Utility.instantToTimestamp(Instant.now());
        return TransactionID.newBuilder().setAccountID(payerAccountId).setTransactionValidStart(validStart).build();
    }

    /**
     * Helps verify chaining for files in a stream.
     *
     * @param actualPrevFileHash   prevFileHash as read from current file
     * @param expectedPrevFileHash hash of last file from application state
     * @param verifyHashAfter      Only the files created after (not including) this point of time are verified for hash
     *                             chaining.
     * @param fileName             name of current stream file being verified
     * @return true if verification succeeds, else false
     */
    public static boolean verifyHashChain(
            String actualPrevFileHash, String expectedPrevFileHash, Instant verifyHashAfter, String fileName) {
        var fileInstant = Instant.parse(FilenameUtils.getBaseName(fileName).replaceAll("_", ":"));
        if (!verifyHashAfter.isBefore(fileInstant)) {
            return true;
        }
        if (Utility.hashIsEmpty(expectedPrevFileHash)) {
            log.error("Previous file hash not available");
            return true;
        }
        log.trace("actual file hash = {}, expected file hash = {}", actualPrevFileHash, expectedPrevFileHash);
        if (actualPrevFileHash.contentEquals(expectedPrevFileHash)) {
            return true;
        }
        return false;
    }
}
