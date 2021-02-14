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
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.UUID;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.EventFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidEventFileException;

class EventFileReaderV3Test {

    private static final byte[] PREVIOUS_HASH = new byte[EventFileReaderV3.EVENT_PREV_HASH_LENGTH];
    private static final byte[] CONTENT = new byte[64];

    private final EventFileReaderV3 eventFileReader = new EventFileReaderV3();

    @Test
    void readValidFileVersion2() {
        StreamFileData validFile = createEventFile(EventFileReaderV3.EVENT_STREAM_FILE_VERSION_2,
                EventFileReaderV3.EVENT_TYPE_PREV_HASH, PREVIOUS_HASH, CONTENT);
        EventFile eventFile = eventFileReader.read(validFile);

        verifyForSuccess(eventFile, validFile, EventFileReaderV3.EVENT_STREAM_FILE_VERSION_2, PREVIOUS_HASH);
    }

    @Test
    void readValidFileVersion3() {
        StreamFileData validFile = createEventFile(EventFileReaderV3.EVENT_STREAM_FILE_VERSION_3,
                EventFileReaderV3.EVENT_TYPE_PREV_HASH, PREVIOUS_HASH, CONTENT);
        EventFile eventFile = eventFileReader.read(validFile);

        verifyForSuccess(eventFile, validFile, EventFileReaderV3.EVENT_STREAM_FILE_VERSION_3, PREVIOUS_HASH);
    }

    @Test
    void readInvalidFileWithInvalidVersion1() {
        StreamFileData invalidFile = createEventFile(1, EventFileReaderV3.EVENT_TYPE_PREV_HASH, PREVIOUS_HASH,
                CONTENT);
        assertThrows(InvalidEventFileException.class, () -> eventFileReader.read(invalidFile));
    }

    @Test
    void readInvalidFileWithInvalidVersion4() {
        StreamFileData invalidFile = createEventFile(4, EventFileReaderV3.EVENT_TYPE_PREV_HASH, PREVIOUS_HASH,
                CONTENT);
        assertThrows(InvalidEventFileException.class, () -> eventFileReader.read(invalidFile));
    }

    @Test
    void readInvalidFileWithInvalidPrevHashMarker() {
        StreamFileData invalidFile = createEventFile(EventFileReaderV3.EVENT_STREAM_FILE_VERSION_3, (byte) 0x0,
                PREVIOUS_HASH, CONTENT);
        assertThrows(InvalidEventFileException.class, () -> eventFileReader.read(invalidFile));
    }

    @Test
    void readInvalidFileWithNoPrevHash() {
        StreamFileData invalidFile = createEventFile(EventFileReaderV3.EVENT_STREAM_FILE_VERSION_3,
                EventFileReaderV3.EVENT_TYPE_PREV_HASH, null, null);
        assertThrows(InvalidEventFileException.class, () -> eventFileReader.read(invalidFile));
    }

    @Test
    void readInvalidFileWithIncompletePrevHash() {
        byte[] incompletePrevHash = new byte[EventFileReaderV3.EVENT_PREV_HASH_LENGTH - 2];
        StreamFileData invalidFile = createEventFile(EventFileReaderV3.EVENT_STREAM_FILE_VERSION_3,
                EventFileReaderV3.EVENT_TYPE_PREV_HASH, incompletePrevHash, null);
        assertThrows(InvalidEventFileException.class, () -> eventFileReader.read(invalidFile));
    }

    private StreamFileData createEventFile(int fileVersion, byte prevHashMarker, byte[] prevHash, byte[] content) {
        String fileName = UUID.randomUUID().toString();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        try (DataOutputStream dos = new DataOutputStream(byteArrayOutputStream)) {
            dos.write(Ints.toByteArray(fileVersion));
            dos.write(prevHashMarker);
            if (prevHash != null && prevHash.length != 0) {
                dos.write(prevHash);
            }
            if (content != null && content.length != 0) {
                dos.write(content);
            }

            return new StreamFileData(fileName, byteArrayOutputStream.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void verifyForSuccess(EventFile eventFile, StreamFileData inputFile, int expectedFileVersion,
                                  byte[] expectedPrevHash) {
        assertThat(eventFile).isNotNull();
        assertThat(eventFile.getBytes()).isNotEmpty().isEqualTo(inputFile.getBytes());
        assertThat(eventFile.getLoadStart()).isNotNull().isPositive();
        assertThat(eventFile.getName()).isEqualTo(inputFile.getFilename());
        assertThat(eventFile.getVersion()).isEqualTo(expectedFileVersion);
        assertThat(eventFile.getPreviousHash()).isEqualTo(Hex.encodeHexString(expectedPrevHash));
    }
}
