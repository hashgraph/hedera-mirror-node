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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;

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

    @Test
    void testCorrectFailureWhenVersionIsWrong() throws IOException {
        var inputStreamMock = mock(InputStream.class);
        when(inputStreamMock.read()).thenReturn(5);
        var streamFileDataMock = mock(StreamFileData.class);
        when(streamFileDataMock.getInputStream()).thenReturn(inputStreamMock);
        final InvalidStreamFileException invalidStreamFileException = assertThrows(InvalidStreamFileException.class,
                () -> protoSignatureFileReader.read(streamFileDataMock));
        assertEquals("Expected file with version 6, given 5.", invalidStreamFileException.getMessage());
    }

    static Stream<Arguments> readValidFileTestArgumentProvider() {
        var recordFile = TestUtils.getResource(
                Path.of(
                                "data", "signature", "v6", "2022-06-14T14_49_22.456975294Z.rcd_sig"
                        )
                        .toString()
        );

        var recordFileGzipped = TestUtils.getResource(
                Path.of(
                                "data", "signature", "v6", "2022-06-14T14_49_22.456975294Z.rcd_sig.gz"
                        )
                        .toString()
        );
        return Stream.of(
                Arguments.of(
                        recordFile,
                        "3ec9149b6a8735c7917530285e2b3528b702dc637f43ddd1da556a74b45d3eb55d7127862319feddccfacf45d987e33b",
                        "6d88c487b371bae696f7ddd5c643fe15716d2fbb3c1f9698112097f7ac1dfab1895059d3117cc9f6f26d341f5f4c1319b7c839bfa363853c3d26daf150f356d4a35b55a2803d30b3a5013101f08b6be530848d79b207a5e18891af730f705e7371c0a6d26dd6244aef2fc2f6462dc9e1f160df7ff0b5a48608ea8bd0e4642780771b15bad35686e0d9aa8387bf440085ae862469cfc81c60089887868b0eee6bd519cf91dbbfdd649c9082767adc49d7eeae877cc780273dba16ede3b09b603cf31c7a44ab258bdf822fbeb6ef607af9cd6c09fc1bacd8a19a148bed4a331e784a4ee93f26a1ddd33ea1c8f4ea4126233b5c5d2c4cf3b6d2a0c26c74cb9692d193ea6703f02db25ea09828f007da5a3fb1a55c401e79f62370b7363da205013922261abcdb9a8cc79464180729d5646e0482bf9cacab9fefaf007f35d0a7822d6b09cf29a23690cec1f0e0b08cffba8753edcc0ff19c3fb8a17b20a65539a60b39876c5ad864103189d256c049ab390ec071e2b42db0dd36d796b9674ff9fda6",
                        "3a74b1449b6d42f92ae8b363b48e739f422a21541ffc30418b80418d51f45c142a010a8e7ce0909d3b3fd500b39027b1",
                        "6ddd1142ce71de99f9da1e9d6933f93f92773583f6413e13fbb72aadebdff5ec7dabd6504a91cd7e757b5f895d204620a442e649bd47a43f4f4c396d665957bc5d4260114d193fdd66c3af908dc589d85783953961ae283bc540e43c278da76b0f1dec05a61eb0cf011e4f6744a768a8cc080bbccae0a9137b1a3a23d6c8ab72c4a6997a98a5a81cb6aafe1a1b4bb4eaf83d51587cb2903691a8c823254e771a811d07d8357d4bd9a916a4b605e9f7fbb8a65569729e6556d83644b072f5df269e042e282f0c3a8c2ecd2bada6d288507967b0b6be2eb4713cb8264be9595aaa563b1d06f33adece27b50c8ecac86d137b979e3f1c898ecc182e35fa6af33f5d3b504c1d570dcf1de7d91ed73bd7919a9a59bdbbe831b8cf7610646d5801b8f0e4a4c5701558f905c68c8762b344dfb468ab74a6456a675f3dcdfd8eebb69b5720111cffeeda6f01af8ffe88c985f132064346b492fddcca0ef2d3ff75a6f9802788f531c6238e7449dbe654e1380477d1fe000d7dca6f59ad3a04dcf8fe6d34"
                ),
                Arguments.of(
                        recordFileGzipped,
                        "3ec9149b6a8735c7917530285e2b3528b702dc637f43ddd1da556a74b45d3eb55d7127862319feddccfacf45d987e33b",
                        "6d88c487b371bae696f7ddd5c643fe15716d2fbb3c1f9698112097f7ac1dfab1895059d3117cc9f6f26d341f5f4c1319b7c839bfa363853c3d26daf150f356d4a35b55a2803d30b3a5013101f08b6be530848d79b207a5e18891af730f705e7371c0a6d26dd6244aef2fc2f6462dc9e1f160df7ff0b5a48608ea8bd0e4642780771b15bad35686e0d9aa8387bf440085ae862469cfc81c60089887868b0eee6bd519cf91dbbfdd649c9082767adc49d7eeae877cc780273dba16ede3b09b603cf31c7a44ab258bdf822fbeb6ef607af9cd6c09fc1bacd8a19a148bed4a331e784a4ee93f26a1ddd33ea1c8f4ea4126233b5c5d2c4cf3b6d2a0c26c74cb9692d193ea6703f02db25ea09828f007da5a3fb1a55c401e79f62370b7363da205013922261abcdb9a8cc79464180729d5646e0482bf9cacab9fefaf007f35d0a7822d6b09cf29a23690cec1f0e0b08cffba8753edcc0ff19c3fb8a17b20a65539a60b39876c5ad864103189d256c049ab390ec071e2b42db0dd36d796b9674ff9fda6",
                        "3a74b1449b6d42f92ae8b363b48e739f422a21541ffc30418b80418d51f45c142a010a8e7ce0909d3b3fd500b39027b1",
                        "6ddd1142ce71de99f9da1e9d6933f93f92773583f6413e13fbb72aadebdff5ec7dabd6504a91cd7e757b5f895d204620a442e649bd47a43f4f4c396d665957bc5d4260114d193fdd66c3af908dc589d85783953961ae283bc540e43c278da76b0f1dec05a61eb0cf011e4f6744a768a8cc080bbccae0a9137b1a3a23d6c8ab72c4a6997a98a5a81cb6aafe1a1b4bb4eaf83d51587cb2903691a8c823254e771a811d07d8357d4bd9a916a4b605e9f7fbb8a65569729e6556d83644b072f5df269e042e282f0c3a8c2ecd2bada6d288507967b0b6be2eb4713cb8264be9595aaa563b1d06f33adece27b50c8ecac86d137b979e3f1c898ecc182e35fa6af33f5d3b504c1d570dcf1de7d91ed73bd7919a9a59bdbbe831b8cf7610646d5801b8f0e4a4c5701558f905c68c8762b344dfb468ab74a6456a675f3dcdfd8eebb69b5720111cffeeda6f01af8ffe88c985f132064346b492fddcca0ef2d3ff75a6f9802788f531c6238e7449dbe654e1380477d1fe000d7dca6f59ad3a04dcf8fe6d34"
                )
        );
    }
}
