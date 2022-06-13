package com.hedera.mirror.importer.reader.signature;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.hedera.mirror.importer.domain.StreamFileData;

public class ProtoSignatureFileReaderTest extends AbstractSignatureFileReaderTest {

    private static final int SIGNATURE_FILE_FORMAT_VERSION = 6;

    private final ProtoSignatureFileReader protoSignatureFileReader = new ProtoSignatureFileReader();

    @ParameterizedTest
    @MethodSource("readValidFileTestArgumentProvider")
    void readValidFileTest(
            File signatureFile,
            String entireFileHashAsHex,
            String entireFileSignatureHex,
            String metadataHashAsHex,
            String metadataSignatureAsHex) {
        var streamFileData = StreamFileData.from(signatureFile);
        var fileStreamSignature = protoSignatureFileReader.read(streamFileData);

        assertNotNull(fileStreamSignature);

        assertThat(fileStreamSignature.getBytes()).isNotEmpty().isEqualTo(streamFileData.getBytes());

        assertEquals(entireFileHashAsHex, fileStreamSignature.getFileHashAsHex());
        assertEquals(entireFileSignatureHex, new String(Hex.encodeHex(fileStreamSignature.getFileHashSignature())));

        assertEquals(metadataHashAsHex, fileStreamSignature.getMetadataHashAsHex());
        assertEquals(metadataSignatureAsHex, new String(Hex.encodeHex(fileStreamSignature.getMetadataHashSignature())));
    }
}
