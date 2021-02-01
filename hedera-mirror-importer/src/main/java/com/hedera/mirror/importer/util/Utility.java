package com.hedera.mirror.importer.util;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.importer.domain.DigestAlgorithm.SHA384;
import static com.hederahashgraph.api.proto.java.Key.KeyCase.ED25519;

import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.exception.FileOperationException;

@Log4j2
@UtilityClass
public class Utility {

    public static final Instant MAX_INSTANT_LONG = Instant.ofEpochSecond(0, Long.MAX_VALUE);

    private static final Long SCALAR = 1_000_000_000L;

    /**
     * @return Timestamp from an instant
     */
    public static Timestamp instantToTimestamp(Instant instant) {
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
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
            if (!file.delete()) {
                log.warn("Unable to delete file: {}", file);
            }
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
     * Opens a file and returns a {@link InputStream} object. Throws {@link FileOperationException} if some error
     * occurs.
     *
     * @param file the input file
     * @return {@link InputStream} object representing the file
     */
    public static InputStream openQuietly(File file) {
        try {
            return new FileInputStream(file);
        } catch (IOException e) {
            throw new FileOperationException("Unable to open file " + file.getPath(), e);
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
     * @param actualPrevHash   prevHash as read from current file
     * @param expectedPrevHash hash of last file from application state
     * @param verifyHashAfter  Only the files created after (not including) this point of time are verified for hash
     *                         chaining.
     * @param fileName         name of current stream file being verified
     * @return true if verification succeeds, else false
     */
    public static boolean verifyHashChain(String actualPrevHash, String expectedPrevHash,
                                          Instant verifyHashAfter, String fileName) {
        var fileInstant = Instant.parse(FilenameUtils.getBaseName(fileName).replaceAll("_", ":"));
        if (!verifyHashAfter.isBefore(fileInstant)) {
            return true;
        }

        if (SHA384.isHashEmpty(expectedPrevHash)) {
            log.warn("Previous hash not available");
            return true;
        }

        if (log.isTraceEnabled()) {
            log.trace("actual hash = {}, expected hash = {}", actualPrevHash, expectedPrevHash);
        }

        return actualPrevHash.contentEquals(expectedPrevHash);
    }
}
