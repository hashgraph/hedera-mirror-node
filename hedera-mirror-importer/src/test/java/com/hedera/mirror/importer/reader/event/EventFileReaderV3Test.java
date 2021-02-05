package com.hedera.mirror.importer.reader.event;

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

import com.google.common.primitives.Ints;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

import com.hedera.mirror.importer.domain.EventFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidEventFileException;

class EventFileReaderV3Test {

    private final byte[] prevHash = new byte[EventFileReaderV3.EVENT_PREV_HASH_LENGTH];
    private final byte[] content = new byte[64];

    private final EventFileReaderV3 eventFileReader = new EventFileReaderV3();

    @TempDir
    protected File tmpPath;

    @Test
    void readValidFileVersion2() {
        File validFile = createTmpEventFile(EventFileReaderV3.EVENT_STREAM_FILE_VERSION_2,
                EventFileReaderV3.EVENT_TYPE_PREV_HASH, prevHash, content);
        EventFile eventFile = eventFileReader.read(StreamFileData.from(validFile));

        verifyForSuccess(eventFile, validFile, EventFileReaderV3.EVENT_STREAM_FILE_VERSION_2, prevHash);
    }

    @Test
    void readValidFileVersion3() {
        File validFile = createTmpEventFile(EventFileReaderV3.EVENT_STREAM_FILE_VERSION_3,
                EventFileReaderV3.EVENT_TYPE_PREV_HASH, prevHash, content);
        EventFile eventFile = eventFileReader.read(StreamFileData.from(validFile));

        verifyForSuccess(eventFile, validFile, EventFileReaderV3.EVENT_STREAM_FILE_VERSION_3, prevHash);
    }

    @Test
    void readValidFileWithNoContent() {
        File validFile = createTmpEventFile(EventFileReaderV3.EVENT_STREAM_FILE_VERSION_3,
                EventFileReaderV3.EVENT_TYPE_PREV_HASH, prevHash, null);
        EventFile eventFile = eventFileReader.read(StreamFileData.from(validFile));

        verifyForSuccess(eventFile, validFile, EventFileReaderV3.EVENT_STREAM_FILE_VERSION_3, prevHash);
    }

    @Test
    void readInvalidFileWithInvalidVersion1() {
        File invalidFile = createTmpEventFile(1, EventFileReaderV3.EVENT_TYPE_PREV_HASH, prevHash, content);
        StreamFileData streamFileData = StreamFileData.from(invalidFile);
        assertThrows(InvalidEventFileException.class, () -> eventFileReader.read(streamFileData));
    }

    @Test
    void readInvalidFileWithInvalidVersion4() {
        File invalidFile = createTmpEventFile(4, EventFileReaderV3.EVENT_TYPE_PREV_HASH, prevHash, content);
        StreamFileData streamFileData = StreamFileData.from(invalidFile);
        assertThrows(InvalidEventFileException.class, () -> eventFileReader.read(streamFileData));
    }

    @Test
    void readInvalidFileWithInvalidPrevHashMarker() {
        File invalidFile = createTmpEventFile(EventFileReaderV3.EVENT_STREAM_FILE_VERSION_3, (byte) 0x0, prevHash,
                content);
        StreamFileData streamFileData = StreamFileData.from(invalidFile);
        assertThrows(InvalidEventFileException.class, () -> eventFileReader.read(streamFileData));
    }

    @Test
    void readInvalidFileWithNoPrevHash() {
        File invalidFile = createTmpEventFile(EventFileReaderV3.EVENT_STREAM_FILE_VERSION_3,
                EventFileReaderV3.EVENT_TYPE_PREV_HASH, null, null);
        StreamFileData streamFileData = StreamFileData.from(invalidFile);
        assertThrows(InvalidEventFileException.class, () -> eventFileReader.read(streamFileData));
    }

    @Test
    void readInvalidFileWithIncompletePrevHash() {
        byte[] incompletePrevHash = new byte[EventFileReaderV3.EVENT_PREV_HASH_LENGTH - 2];
        File invalidFile = createTmpEventFile(EventFileReaderV3.EVENT_STREAM_FILE_VERSION_3,
                EventFileReaderV3.EVENT_TYPE_PREV_HASH, incompletePrevHash, null);
        StreamFileData streamFileData = StreamFileData.from(invalidFile);
        assertThrows(InvalidEventFileException.class, () -> eventFileReader.read(streamFileData));
    }

    private File createTmpEventFile(int fileVersion, byte prevHashMarker, byte[] prevHash, byte[] content) {
        String fileName = UUID.randomUUID().toString();
        File file = FileUtils.getFile(tmpPath, fileName);
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            dos.write(Ints.toByteArray(fileVersion));
            dos.write(prevHashMarker);
            if (prevHash != null && prevHash.length != 0) {
                dos.write(prevHash);
            }
            if (content != null && content.length != 0) {
                dos.write(content);
            }

            return file;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void verifyForSuccess(EventFile eventFile, File inputFile, int expectedFileVersion,
                                  byte[] expectedPrevHash) {
        assertThat(eventFile).isNotNull();
        assertThat(eventFile.getName()).isEqualTo(inputFile.getName());
        assertThat(eventFile.getVersion()).isEqualTo(expectedFileVersion);
        assertThat(eventFile.getPreviousHash()).isEqualTo(Hex.encodeHexString(expectedPrevHash));
    }
}
