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
import java.nio.file.Path;
import java.util.stream.Stream;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.hedera.mirror.importer.TestUtils;
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

    static Stream<Arguments> readValidFileTestArgumentProvider() {
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
                Arguments.of(
                        recordFile,
                        "50727100c40e186a48a29b6fc5f07cae8c5da433a49863e628231518f47b8414e89833ff03a1ba4fbca6c1a0ae9674c7",
                        "14277a649d60403c35972cc7523bfd2594e0351415d560db09db1681f766ff24fc387976186d064dffc2013da3bab63812beefb44321b9e22964926ab45abdd322cedbb937efa643e7c7910073363321ce863119c3973e62ca12ca3b037ac6e447828c00ceb6ff8919fb57306d75bf813898fdd99d33dedb18a7ccc8f4d404dbd8594b5a265161de206b63fe8715d8fcf8c08b5dd8a563fe295264784ed99dd892ceffbc72804d52144378d5f530e10c0b94b5c3c21c808ecff356b94f9a188e0563681f8a1df79fedd2962c6649577ab4f54685025bfc92f1e3b2f5941cc1eff95cdefcff831bd541edcb8f3d99ea24899f3105953a6685746fd14e723899aa31fa2806ce22cd6f6fe811e8988acd495678fe1bbc390a598326dca9fbca73107307a788b7efdd8316245644e54a2fdce9b82ce6dfa493a4fef54202ad8d8a6cffd18b2a0b33a3ef2eb7c86f3f97b85497acfa0a3001c4730c66b904b57dbab5475b42c61ac6cd6b8361f7e199bbc11b043dac8d57f3d96499edfff5f83b6fa4",
                        "7533f30649143845ef97687c093522cf27a655a8fa185d19f7d482af6329a79ad4ca36356c2a813b5af4893f6aaeb212",
                        "6149a367a63202928b436f3fceb506e0a1f28932c1b0ceea68ee9cc980052770bb8409a5d06eabd6f98d49313825736ba615ba329fbd2030b9e9369652cc4637935b2088a1e533c1249774a4db5b68560d32897617032379262ecac3c3efabd2c982c8b07a9cf3b3c7aefd4b171d002498535b674043523adbe23a7a420e122fa8dc4053f7dbed75f676222ffa99185fecedf483c518c1ae0fe1ab876e8cb1288abc08153cf0b9eb1806a45bed9f80c9741fe47d65f182cfc0d767a37d70e1aac3149df74723deeb4053050d09ec3cb5e6ceefb9f52b1ffa4520c86a022136d2617fbb4429c564030ce618542a163f3c20943fd9e2f1db2f6b1190cf7e2a7a68de083bad5a7b1d920e4f48156683ceb4a4908d7dbd5b3550ce0b0896940277adc28696456388fb04c75969b2694f118eb0452f4c9c7b27ae41bb33e6b04d8e88e29c77afa0f732b2a476f489b0dc46da54b6004db0b3a86f0e3ba37449863adb12a5f5d22c0505b5cd95ae1396a43d99cd24416b5931854c1c351253b0f8b232"
                ),
                Arguments.of(
                        recordFileGzipped,
                        "50727100c40e186a48a29b6fc5f07cae8c5da433a49863e628231518f47b8414e89833ff03a1ba4fbca6c1a0ae9674c7",
                        "14277a649d60403c35972cc7523bfd2594e0351415d560db09db1681f766ff24fc387976186d064dffc2013da3bab63812beefb44321b9e22964926ab45abdd322cedbb937efa643e7c7910073363321ce863119c3973e62ca12ca3b037ac6e447828c00ceb6ff8919fb57306d75bf813898fdd99d33dedb18a7ccc8f4d404dbd8594b5a265161de206b63fe8715d8fcf8c08b5dd8a563fe295264784ed99dd892ceffbc72804d52144378d5f530e10c0b94b5c3c21c808ecff356b94f9a188e0563681f8a1df79fedd2962c6649577ab4f54685025bfc92f1e3b2f5941cc1eff95cdefcff831bd541edcb8f3d99ea24899f3105953a6685746fd14e723899aa31fa2806ce22cd6f6fe811e8988acd495678fe1bbc390a598326dca9fbca73107307a788b7efdd8316245644e54a2fdce9b82ce6dfa493a4fef54202ad8d8a6cffd18b2a0b33a3ef2eb7c86f3f97b85497acfa0a3001c4730c66b904b57dbab5475b42c61ac6cd6b8361f7e199bbc11b043dac8d57f3d96499edfff5f83b6fa4",
                        "7533f30649143845ef97687c093522cf27a655a8fa185d19f7d482af6329a79ad4ca36356c2a813b5af4893f6aaeb212",
                        "6149a367a63202928b436f3fceb506e0a1f28932c1b0ceea68ee9cc980052770bb8409a5d06eabd6f98d49313825736ba615ba329fbd2030b9e9369652cc4637935b2088a1e533c1249774a4db5b68560d32897617032379262ecac3c3efabd2c982c8b07a9cf3b3c7aefd4b171d002498535b674043523adbe23a7a420e122fa8dc4053f7dbed75f676222ffa99185fecedf483c518c1ae0fe1ab876e8cb1288abc08153cf0b9eb1806a45bed9f80c9741fe47d65f182cfc0d767a37d70e1aac3149df74723deeb4053050d09ec3cb5e6ceefb9f52b1ffa4520c86a022136d2617fbb4429c564030ce618542a163f3c20943fd9e2f1db2f6b1190cf7e2a7a68de083bad5a7b1d920e4f48156683ceb4a4908d7dbd5b3550ce0b0896940277adc28696456388fb04c75969b2694f118eb0452f4c9c7b27ae41bb33e6b04d8e88e29c77afa0f732b2a476f489b0dc46da54b6004db0b3a86f0e3ba37449863adb12a5f5d22c0505b5cd95ae1396a43d99cd24416b5931854c1c351253b0f8b232"
                )
        );
    }
}
