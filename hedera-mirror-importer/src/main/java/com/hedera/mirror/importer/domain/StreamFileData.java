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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.hedera.mirror.importer.downloader.StreamFileNotifier;

import lombok.NonNull;
import lombok.Value;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.hedera.mirror.importer.exception.FileOperationException;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;

@Value
public class StreamFileData {

    private static final CompressorStreamFactory compressorStreamFactory = new CompressorStreamFactory(true);

    private final String compressor;
    private final String filename;
    private final byte[] bytes;

    public static StreamFileData from(String compressor, @NonNull File file) {
        try {
            byte[] bytes = FileUtils.readFileToByteArray(file);
            return new StreamFileData(compressor, file.getName(), bytes);
        } catch (InvalidStreamFileException e) {
            throw e;
        } catch (Exception e) {
            throw new FileOperationException("Unable to read file to byte array", e);
        }
    }

    public static StreamFileData from(@NonNull File file) {
        return from(null, file);
    }

    // Used for testing String based files like CSVs
    public static StreamFileData from(@NonNull String filename, @NonNull String contents) {
        return new StreamFileData(null, filename, contents.getBytes(StandardCharsets.UTF_8));
    }

    public static StreamFileData from(@NonNull String filename, @NonNull byte[] bytes) {
        return new StreamFileData(null, filename, bytes);
    }

    public InputStream getInputStream() {
        try {
            InputStream is = new ByteArrayInputStream(bytes);
            if (!StringUtils.isBlank(compressor)) {
                is = compressorStreamFactory.createCompressorInputStream(compressor, is);
            }

            return is;
        } catch (CompressorException ex) {
            throw new InvalidStreamFileException("Unable to open compressed file " + filename, ex);
        }
    }

    @Override
    public String toString() {
        return getFilename();
    }
}
