package com.hedera.mirror.importer.domain;

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

import com.google.common.base.Splitter;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import lombok.Value;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;

import com.hedera.mirror.importer.exception.InvalidStreamFileException;

@Value
public class StreamFilename implements Comparable<StreamFilename> {

    private static final Comparator<StreamFilename> COMPARATOR = Comparator.comparing(StreamFilename::getInstant);
    private static final char COMPATIBLE_TIME_SEPARATOR = '_';
    private static final char STANDARD_TIME_SEPARATOR = ':';
    private static final Splitter FILENAME_SPLITTER = Splitter.on(FilenameUtils.EXTENSION_SEPARATOR).omitEmptyStrings();

    private final String compressor;
    private final String extension;
    private final String filename;
    private final FileType fileType;
    private final String fullExtension;
    private final Instant instant;
    private final StreamType streamType;

    public StreamFilename(String filename) {
        Assert.hasText(filename, "'filename' must not be empty");
        this.filename = filename;

        TypeInfo typeInfo = extractTypeInfo(filename);
        this.compressor = typeInfo.compressor;
        this.extension = typeInfo.extension;
        this.fileType = typeInfo.fileType;
        this.streamType = typeInfo.streamType;
        this.fullExtension = this.compressor == null ? this.extension : StringUtils
                .joinWith(".", this.extension, this.compressor);

        this.instant = extractInstant(filename, this.fullExtension, this.streamType.getSuffix());
    }

    /**
     * Gets the filename with the last extension of the specified streamType and fileType with instant.
     *
     * @param streamType
     * @param fileType
     * @param instant
     * @return the data filename
     */
    public static String getFilename(StreamType streamType, FileType fileType, Instant instant) {
        String timestamp = instant.toString().replace(STANDARD_TIME_SEPARATOR, COMPATIBLE_TIME_SEPARATOR);
        String suffix = streamType.getSuffix();
        String extension;
        if (fileType == FileType.DATA) {
            extension = streamType.getLastDataExtension();
        } else {
            extension = streamType.getLastSignatureExtension();
        }

        return StringUtils.joinWith(".", StringUtils.join(timestamp, suffix), extension);
    }

    /**
     * Returns the filename after this file, in the order of timestamp. This is done by removing the separator '.' and
     * extension from the filename, then appending '_', so that regardless of the extension being used, files after
     * the generated filename will always be newer than this file.
     *
     * @return the filename to mark files after this stream filename
     */
    public String getFilenameAfter() {
        return StringUtils.remove(filename, "." + fullExtension) + COMPATIBLE_TIME_SEPARATOR;
    }

    /**
     * Check if this StreamFilename matches the other. Two StreamFilename objects match iff 1. one is signature and the
     * other is data; and 2. their file names match up to the signature suffix '_sig'
     *
     * @param other
     * @return
     */
    public boolean match(StreamFilename other) {
        if (other == null) {
            return false;
        }

        if (fileType == other.getFileType()) {
            return false;
        }

        StreamFilename signatureFilename = this;
        StreamFilename dataFilename = other;
        if (fileType == FileType.DATA) {
            signatureFilename = other;
            dataFilename = this;
        }

        String commonPrefix = signatureFilename.getFilename().split(StreamType.SIGNATURE_SUFFIX)[0];
        return dataFilename.getFilename().startsWith(commonPrefix);
    }

    @Override
    public int compareTo(StreamFilename other) {
        return COMPARATOR.compare(this, other);
    }

    @Override
    public String toString() {
        return filename;
    }

    private static TypeInfo extractTypeInfo(String filename) {
        List<String> parts = FILENAME_SPLITTER.splitToList(filename);
        if (parts.size() < 2) {
            throw new InvalidStreamFileException("Failed to determine StreamType for filename: " + filename);
        }

        String last = parts.get(parts.size() - 1);
        String secondLast = parts.get(parts.size() - 2);

        for (StreamType type : StreamType.values()) {
            String suffix = type.getSuffix();
            if (!StringUtils.isEmpty(suffix) && !filename.contains(suffix)) {
                continue;
            }

            for (FileType fileType : FileType.values()) {
                List<String> extensions = fileType == FileType.DATA ? type.getDataExtensions() :
                        type.getSignatureExtensions();
                for (String extension : extensions) {
                    // if last matches extension, the file is not compressed; otherwise if secondLast matches extension,
                    // last is the compression extension
                    if (extension.equals(last)) {
                        return new TypeInfo(null, extension, fileType, type);
                    }

                    if (extension.equals(secondLast)) {
                        return new TypeInfo(last, extension, fileType, type);
                    }
                }
            }
        }

        throw new InvalidStreamFileException("Failed to determine StreamType for filename: " + filename);
    }

    private static Instant extractInstant(String filename, String fullExtension, String suffix) {
        try {
            String fullSuffix = StringUtils.join(suffix, "." + fullExtension);
            String dateTime = StringUtils.removeEnd(filename, fullSuffix);
            dateTime = dateTime.replace(COMPATIBLE_TIME_SEPARATOR, STANDARD_TIME_SEPARATOR);
            return Instant.parse(dateTime);
        } catch (DateTimeParseException ex) {
            throw new InvalidStreamFileException("Invalid datetime string in filename " + filename, ex);
        }
    }

    public enum FileType {
        DATA,
        SIGNATURE
    }

    @Value
    private static class TypeInfo {
        String compressor;
        String extension;
        FileType fileType;
        StreamType streamType;
    }
}
