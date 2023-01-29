package com.hedera.mirror.importer.reader.record.sidecar;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.InvalidProtocolBufferException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.transaction.SidecarFile;
import com.hedera.mirror.importer.TestRecordFiles;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;

class SidecarFileReaderImplTest {

    private static final String RECORD_FILENAME = "2022-07-13T08_46_11.304284003Z.rcd.gz";

    private static final String SIDECAR_FILENAME = "2022-07-13T08_46_11.304284003Z_01.rcd.gz";

    private static final String SIDECAR_FILE_PATH = "data/recordstreams/v6/record0.0.3/sidecar/" + SIDECAR_FILENAME;

    private DomainBuilder domainBuilder;

    private SidecarFileReader sidecarFileReader;

    @BeforeEach
    void beforeEach() {
        domainBuilder = new DomainBuilder();
        sidecarFileReader = new SidecarFileReaderImpl();
    }

    @Test
    void read() {
        var expected = TestRecordFiles.getAll().get(RECORD_FILENAME).getSidecars().get(0);
        // clear the fields SidecarFileReader fills
        var sidecar = expected.toBuilder()
                .actualHash(null)
                .bytes(null)
                .count(null)
                .size(null)
                .records(null)
                .build();
        var streamFileData = StreamFileData.from(TestUtils.getResource(SIDECAR_FILE_PATH));

        sidecarFileReader.read(sidecar, streamFileData);

        assertThat(sidecar)
                .returns(streamFileData.getBytes(), SidecarFile::getBytes)
                .isEqualTo(expected);
    }

    @Test
    void readCorruptedGzipFile() {
        var streamFileData = StreamFileData.from(SIDECAR_FILENAME, domainBuilder.bytes(256));
        var sidecar = domainBuilder.sidecarFile().get();
        assertThatThrownBy(() -> sidecarFileReader.read(sidecar, streamFileData))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasCauseInstanceOf(CompressorException.class);
    }

    @Test
    void readCorruptedProtoFile() throws IOException {
        try (var byteArrayOutputStream = new ByteArrayOutputStream();
             var gzipCompressorOutputStream = new GzipCompressorOutputStream(byteArrayOutputStream)) {
            gzipCompressorOutputStream.write(domainBuilder.bytes(256));
            gzipCompressorOutputStream.finish();
            var streamFileData = StreamFileData.from(SIDECAR_FILENAME, byteArrayOutputStream.toByteArray());
            var sidecar = domainBuilder.sidecarFile().get();
            assertThatThrownBy(() -> sidecarFileReader.read(sidecar, streamFileData))
                    .isInstanceOf(InvalidStreamFileException.class)
                    .hasCauseInstanceOf(InvalidProtocolBufferException.class);
        }
    }
}
