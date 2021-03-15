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

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import lombok.Getter;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import com.hedera.mirror.importer.exception.InvalidStreamFileException;

@Getter
public class StreamFilename {

    private static final char COMPATIBLE_TIME_SEPARATOR = '_';
    private static final char STANDARD_TIME_SEPARATOR = ':';
    private static final DateTimeFormatter ISO_INSTANT_FULL_NANOS = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendInstant(9)
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT);

    private final String extension;
    private final String filename;
    private final FileType fileType;
    private final Instant instant;
    private final StreamType streamType;
    private final String timestamp;

    public StreamFilename(String filename) {
        this.filename = filename;

        TypeInfo typeInfo = extractTypeInfo(filename);
        this.extension = typeInfo.extension;
        this.fileType = typeInfo.fileType;
        this.streamType = typeInfo.streamType;

        this.instant = extractInstant(filename, this.extension, this.streamType);
        this.timestamp = ISO_INSTANT_FULL_NANOS.format(this.instant);
    }

    /**
     * Gets the data filename with the last extension of the specified streamType with instant.
     *
     * @param streamType
     * @param instant
     * @return the data filename
     */
    public static String getDataFilenameWithLastExtension(StreamType streamType, Instant instant) {
        String timestamp = instant.toString().replace(STANDARD_TIME_SEPARATOR, COMPATIBLE_TIME_SEPARATOR);
        String suffix = streamType.getSuffix();
        String extension = streamType.getLastDataExtension();
        return StringUtils.joinWith(".", StringUtils.join(timestamp, suffix), extension);
    }

    /**
     * Gets the instant from the stream filename.
     *
     * @param filename
     * @return instant from the stream filename
     */
    public static Instant getInstantFromStreamFilename(String filename) {
        if (StringUtils.isBlank(filename)) {
            return Instant.EPOCH;
        }

        return new StreamFilename(filename).getInstant();
    }

    /**
     * Gets the corresponding data filename
     *
     * @return data filename
     */
    public String getDataFilename() {
        if (fileType == FileType.DATA) {
            return filename;
        }

        String dataExtension = streamType.getSignatureToDataExtensionMap().get(extension);
        if (dataExtension == null) {
            throw new InvalidStreamFileException("No matching data extension for signature extension " + extension);
        }

        return StringUtils.join(StringUtils.removeEnd(filename, extension), dataExtension);
    }

    /**
     * Gets the signature file name with the alphabetically last extension
     *
     * @return signature file name with the alphabetically last extension
     */
    public String getSignatureFilenameWithLastExtension() {
        return StringUtils.join(StringUtils.removeEnd(filename, extension), streamType.getLastSignatureExtension());
    }

    private static TypeInfo extractTypeInfo(String filename) {
        for (StreamType type : StreamType.values()) {
            String suffix = type.getSuffix();
            for (FileType fileType : FileType.values()) {
                List<String> extensions = fileType == FileType.DATA ? type.getDataExtensions() : type
                        .getSignatureExtensions();
                for (String extension : extensions) {
                    if (filename.endsWith(extension) && (suffix != null && filename.contains(suffix))) {
                        return new TypeInfo(extension, fileType, type);
                    }
                }
            }
        }

        throw new InvalidStreamFileException("Failed to determine StreamType for filename: " + filename);
    }

    private static Instant extractInstant(String filename, String extension, StreamType streamType) {
        try {
            String suffix = StringUtils.join(streamType.getSuffix(), "." + extension);
            String dateTime = StringUtils.removeEnd(filename, suffix);
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
        String extension;
        FileType fileType;
        StreamType streamType;
    }
}
