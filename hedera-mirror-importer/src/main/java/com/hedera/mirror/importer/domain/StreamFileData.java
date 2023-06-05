/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.domain;

import com.hedera.mirror.importer.exception.FileOperationException;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.function.Supplier;
import lombok.CustomLog;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

@CustomLog
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Value
public class StreamFileData {

    private static final CompressorStreamFactory compressorStreamFactory = new CompressorStreamFactory(true);

    @EqualsAndHashCode.Include
    private final StreamFilename streamFilename;

    @EqualsAndHashCode.Include
    private final byte[] bytes;

    @Getter(lazy = true)
    private final byte[] decompressedBytes = decompressBytes();

    private final Instant lastModified;

    private static StreamFileData readStreamFileData(File file, Supplier<StreamFilename> streamFilenameSupplier) {
        try {
            byte[] bytes = FileUtils.readFileToByteArray(file);
            var lastModified = Instant.ofEpochMilli(file.lastModified());
            return new StreamFileData(streamFilenameSupplier.get(), bytes, lastModified);
        } catch (InvalidStreamFileException e) {
            throw e;
        } catch (Exception e) {
            throw new FileOperationException("Unable to read file to byte array", e);
        }
    }

    public static StreamFileData from(@NonNull File file) {
        return readStreamFileData(file, () -> StreamFilename.of(file.getPath()));
    }

    public static StreamFileData from(@NonNull StreamFilename streamFilename) {
        return readStreamFileData(new File(streamFilename.getFilePath()), () -> streamFilename);
    }

    // Used for testing String based files like CSVs
    public static StreamFileData from(@NonNull String filename, @NonNull String contents) {
        return new StreamFileData(
                StreamFilename.of(filename), contents.getBytes(StandardCharsets.UTF_8), Instant.now());
    }

    // Used for testing with raw bytes
    public static StreamFileData from(@NonNull String filename, byte[] bytes) {
        return new StreamFileData(StreamFilename.of(filename), bytes, Instant.now());
    }

    public InputStream getInputStream() {
        return new ByteArrayInputStream(getDecompressedBytes());
    }

    public String getFilename() {
        return streamFilename.getFilename();
    }

    @Override
    public String toString() {
        return streamFilename.toString();
    }

    private byte[] decompressBytes() {
        var compressor = streamFilename.getCompressor();
        if (StringUtils.isBlank(compressor)) {
            return bytes;
        }

        try (var inputStream = new ByteArrayInputStream(bytes);
                var compressorInputStream =
                        compressorStreamFactory.createCompressorInputStream(compressor, inputStream)) {
            return compressorInputStream.readAllBytes();
        } catch (CompressorException | IOException e) {
            var filename = streamFilename.getFilename();
            log.error("Failed to decompress stream file {}", filename);
            throw new InvalidStreamFileException(filename, e);
        }
    }
}
