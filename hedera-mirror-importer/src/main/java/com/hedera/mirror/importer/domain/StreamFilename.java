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
import java.util.List;
import lombok.Getter;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import com.hedera.mirror.importer.exception.InvalidStreamFileException;

@Getter
public class StreamFilename {

    private final static char COMPATIBLE_TIME_SEPARATOR = '_';
    private final static char STANDARD_TIME_SEPARATOR = ':';

    private final String extension;
    private final String filename;
    private final FileType fileType;
    private final Instant instant;
    private final StreamType streamType;
    private final String timestamp;

    public StreamFilename(String filename) {
        this.filename = filename;

        TypeInfo typeInfo = getTypeInfo(filename);
        this.extension = typeInfo.extension;
        this.fileType = typeInfo.fileType;
        this.streamType = typeInfo.streamType;

        String date = StringUtils.removeEnd(filename, "." + this.extension);
        date = StringUtils.removeEnd(date, this.streamType.getSuffix());
        this.timestamp = date;
        date = date.replace(COMPATIBLE_TIME_SEPARATOR, STANDARD_TIME_SEPARATOR);
        this.instant = Instant.parse(date);
    }

    /**
     * Gets the signature file name with the alphabetically last extension
     *
     * @return signature file name with the alphabetically last extension
     */
    public String getSignatureFilenameWithLastExtension() {
        return getFilename(instant, streamType, streamType.getLastSignatureExtension());
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

    private TypeInfo getTypeInfo(String filename) {
        for (StreamType type : StreamType.values()) {
            for (FileType fileType : FileType.values()) {
                List<String> extensions = fileType == FileType.DATA ? type.getDataExtensions() : type
                        .getSignatureExtensions();
                for (String extension : extensions) {
                    if (filename.endsWith(extension)) {
                        return new TypeInfo(extension, fileType, type);
                    }
                }
            }
        }

        throw new InvalidStreamFileException("Failed to determine StreamType for filename: " + filename);
    }

    public static String getDataFilenameWithLastExtension(StreamType streamType, Instant instant) {
        return getFilename(instant, streamType, streamType.getLastDataExtension());
    }

    public static Instant getInstantFromStreamFilename(String filename) {
        return new StreamFilename(filename).getInstant();
    }

    private static String getFilename(Instant instant, StreamType streamType, String extension) {
        String timestamp = instant.toString().replace(STANDARD_TIME_SEPARATOR, COMPATIBLE_TIME_SEPARATOR);
        return StringUtils.joinWith(".", StringUtils.join(timestamp, streamType.getSuffix()), extension);
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
