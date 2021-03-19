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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.hedera.mirror.importer.exception.InvalidStreamFileException;

class StreamFileDataTest {

    private static final String FILENAME = "2021-03-12T17_15_00Z.rcd";

    @TempDir
    Path dataPath;

    @ParameterizedTest(name = "create StreamFileData from {3}")
    @CsvSource({
            "true, false, false, empty file should return valid StreamFileData object",
            "true, true, false, file with content should return valid StreamFileData object",
            "false, false, false, non-existent file expect exception",
            "false, false, true, directory expect exception",
    })
    void from(boolean createFile, boolean writeData, boolean createDirectory, String testName) throws IOException {
        File file = FileUtils.getFile(dataPath.toFile(), FILENAME);

        if (createFile) {
            FileUtils.touch(file);

            if (writeData) {
                FileUtils.write(file, "testdata", StandardCharsets.UTF_8);
            }

            StreamFileData streamFileData = StreamFileData.from(file);

            assertThat(streamFileData.getFilename()).isEqualTo(file.getName());
            assertThat(streamFileData.getInputStream()).isNotNull();
        } else {
            if (createDirectory) {
                FileUtils.forceMkdir(file);
            }

            assertThrows(RuntimeException.class, () -> StreamFileData.from(file), testName);
        }
    }

    @Test
    void createWithGzippedData() throws IOException {
        String filename = "foobar.gz";
        byte[] uncompressedBytes = { 1, 2, 3 };

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try (OutputStream os = new GZIPOutputStream(baos)) {
                os.write(uncompressedBytes);
            }

            StreamFileData streamFileData = new StreamFileData("gz", filename, baos.toByteArray());

            try (InputStream is = streamFileData.getInputStream()) {
                assertThat(is.readAllBytes()).isEqualTo(uncompressedBytes);
            }
        }
    }

    @Test
    void createWithMismatchCompressor() throws IOException {
        String filename = "foobar.gz";
        byte[] uncompressedBytes = { 1, 2, 3 };

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try (OutputStream os = new GZIPOutputStream(baos)) {
                os.write(uncompressedBytes);
            }

            StreamFileData streamFileData = new StreamFileData("lzma", filename, baos.toByteArray());
            assertThrows(InvalidStreamFileException.class, streamFileData::getInputStream);
        }
    }

    @Test
    void createWithCompressorAndUncompressedData() {
        String filename = "foobar.gz";
        byte[] uncompressedBytes = { 1, 2, 3 };

        StreamFileData streamFileData = new StreamFileData("gz", filename, uncompressedBytes);
        assertThrows(InvalidStreamFileException.class, streamFileData::getInputStream);
    }
}
