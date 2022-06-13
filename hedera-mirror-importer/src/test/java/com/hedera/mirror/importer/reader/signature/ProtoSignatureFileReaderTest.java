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

import java.io.File;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.StreamFileData;

public class ProtoSignatureFileReaderTest {

    private final ProtoSignatureFileReader protoSignatureFileReader = new ProtoSignatureFileReader();

    @ParameterizedTest
    @MethodSource("readValidFileTestArgumentProvider")
    void readValidFileTest(File signatureFile) {
        var streamFileData = StreamFileData.from(signatureFile);
        var fileStreamSignature = protoSignatureFileReader.read(streamFileData);

        assertNotNull(fileStreamSignature);
        assertThat(fileStreamSignature.getBytes()).isNotEmpty().isEqualTo(streamFileData.getBytes());
//        assertArrayEquals(Base64.decodeBase64(entireFileHashBase64.getBytes()), fileStreamSignature.getFileHash());
//        assertArrayEquals(Base64.decodeBase64(entireFileSignatureBase64.getBytes()), fileStreamSignature
//                .getFileHashSignature());
//        assertArrayEquals(Base64.decodeBase64(metadataHashBase64.getBytes()), fileStreamSignature.getMetadataHash());
//        assertArrayEquals(Base64.decodeBase64(metadataSignatureBase64.getBytes()), fileStreamSignature
//                .getMetadataHashSignature());
    }


    static Stream<Arguments> readValidFileTestArgumentProvider(){
        var recordFile = TestUtils.getResource(
                Path.of(
                                "data", "signature", "v6", "2022-06-13T14_30_54.302946267Z.rcd_sig"
                        )
                        .toString()
        );

        var recordFileGzipped = TestUtils.getResource(
                Path.of(
                                "data", "signature", "v6", "2022-06-13T14_30_54.302946267Z.rcd_sig.gz"
                        )
                        .toString()
        );
        return Stream.of(
                Arguments.of(recordFile),
                Arguments.of(recordFileGzipped)
        );
    }
}
