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

import java.io.ByteArrayInputStream;
import java.io.File;
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
    void testCorrectFailureWhenVersionIsWrong() {
        var streamFileDataMock = mock(StreamFileData.class);
        when(streamFileDataMock.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] {5}));
        final InvalidStreamFileException invalidStreamFileException = assertThrows(InvalidStreamFileException.class,
                () -> protoSignatureFileReader.read(streamFileDataMock));
        assertEquals("Expected file with version 6, given 5.", invalidStreamFileException.getMessage());
    }

    static Stream<Arguments> readValidFileTestArgumentProvider() {
        var recordFile1 = TestUtils.getResource(
                Path.of(
                                "data", "signature", "v6", "2022-06-16T10_18_37.496046001Z.rcd_sig"
                        )
                        .toString()
        );

        var recordFile2 = TestUtils.getResource(
                Path.of(
                                "data", "signature", "v6", "2022-06-16T10_18_40.720801003Z.rcd_sig"
                        )
                        .toString()
        );
        return Stream.of(
                Arguments.of(
                        recordFile1,
                        "3e546619bd9c59fe0ee03be25ff2371718ff206f31323868207bb621bc85212669eb66b8a1556d4e686c8fbcd14f9e97",
                        "60a3548a680bc84336fc0e3f15b4548b6356d6888dbeedab95087f881f578ac59078912cbf0d018ab02e15fe2b4a276d5254c4d05899ee7e77093317006d786fd3632162e169f0aa0cc6365c67676866ec37ea70bc136aeb80372ba8581ea615e48dad2839ed0688d3e76b33cb838c06cbc2621259e202fcb88eac5aed149e0eeef69c8e7259e5c93d765e6af2ab4701d42ec29b2c5cea0036d988acdd18759bf7a0cf7cfd5527047753c99e4a8006614ed3aec61c7f88c360eef811cdb1550fa7280c86ed6d134ea7b0d552676accc0bc4221895ba8c7ce1baa9eb1aa64039a663cf5044952e965c13f52b5916327b13b92b7606933987aff14e896bf1f315aec8b7dcd64f66840a0e4ccb875f5e1c058b4d1b4a4f7b8c69c0a17f5fb77ac08b2643553e39a350bde8124d8e243cf31a270f645a8e23c75d22d5ab67ae00f8a82210df0cf8cdaaded3d915a29d6f96cfe323f86a6ec6dc49693b49853414e093c9dfafa0bcb49196952798794be3bb1bc35d34e2bb116459034aed4a90fbdd7",
                        "01a43036a6ce80082680fa64b4702aecb659b8ddd02a06201c6c83d2b71def7b2ee1b5ac09bb7eb9b2f04dad5547e8e9",
                        "0703b067b5a02a734266cb2cedfd38ba73c051a70dac2d7ae39700f619286e9a58127f2c4fb3704f6dfee670203ed22517b2928efd82331cd85d8ed6f8966ceb9534ca9a24fb5fb42555bc0f1fd7adea229e82ba6bd36d0de04c3c8abfb6c7d0e2453acf97c7164482ff82b7472f117c1d295d76e29ac0eb435b7a643c3a7bbc92b99e3f825ddc484b8fa73ded84d5983009bd93d569df86a79e999b344d12e755ee7489796e2bd6aea3e18e8134afbe545a947eb389ae722c640d82a3ff06aabb83d0c384101221895168614e4e9fa4cfc2422af3f860a3284772b7f02fbe230c30a78b4a024b1da37b1093137d3839677635dea5302a53e0127beb26043234564357fb73ff8a4205e751bad73baa0a0d78358e6265401213b8201319fc822653ee891e12d53f50292768beb48cd9f5fe729b89a419144e12dbcf7e8f52b903f36836c09f7ad2def1dc7ad322ef27e970851ba16133c9dd1127ef7e7cc6d7a04825e95cffe3e6107191297491187286971c9c6d34d39cb46fbf4594e9f05005"
                ),
                Arguments.of(
                        recordFile2,
                        "b5947d59a5661f591c0950e8dd0c6a4d42f49dcf7454bc05c911fef67aedead41fb00f01ac32b1de9ac2b3130fb14346",
                        "8c23d928ae9a0986e1398fc58af59f0081a025e7438eddea8768e6c84171970fd874d9862fc06207ee0f3a049d6ae1b9daca4d568850830c39b99ac35e24e00fd59e8c01ed37891989b0988e38de4e1f9c55f8cc8d7b9fdeeb3f3c8eb91e85332fe57b7a6bb59a404b035ee01cd472a111a94edc864ce9c561e3176cb1b0cd54e905e5e82909ee346eb32e1869a0ac0eea39122ba4d17eb9eae63569676fe3345f2ba350a38781e36b973fd47ba4587f7443007f0b11453fd4da94b6bd7368b1df73aeb5897252d63cd6780ef8ccc54f9baeb772aa06d06530433f4357a578acfbe63ed487d75505bf7ded25661c8fa8d94d97cf8914caab41cfbec48160a647980063796e643c118aa45a5044beed44f8e6ace59796679cfb7ecbfaac954886ba4a10f6761d9abc45edf12b11b5e45f88c3a9dcbb1943b6c1d4eacdb47d8917246d2cadac4653138f92aa307dcfdb6b9c58350c04ae692cf029d7c8bf66e48ff292028fc09eddd89e9ea833e52ea9dbe0f8d2438fe65df37abb1b1b1c6469c4",
                        "c8766bf8449e2d4598a4b7cc41c7a54f16dc869bd9cf81af2536ba1a8ec8c86d1e33134995d45f67ce26a79249d96666",
                        "73e629389b23202311e62c11a5a740ff6dd6deff2402c1427f892a8cf1bf863933eb2b6ad39e4208bbdb241bb2bda107b4adcd72a9aff4da2f9ae5495e521a029b56a7bfc219fd81e474160adb6a551f76fccf46554c1f611ad72d27b141e3a6a206d9600296fe7c0f6c0b9a4c2fde8d26b609520576bb7b3aa1ee2e766f21ca5cb835f5b8cbdde63e3660634f56fb318fdb3318d6159c31894dfd1f732c79c4c85fb6ff59ab50ac2ce56cbcc8d14450cda030ef7c83871b86477f7cc10ce8ea44c9c5f97530bd2fdccadf87435ce9dd55cc7cc39b1382db8566d4d77ae7e3444303a8e6712bca05f7571679a7c9483532b3ca22335584cb5ded47ad2622ad19264a1666e66490dccab7996136336c0191a4472a6e570bb4167c2a0183dea146717ec302a8739975e6a175ee1bb7884d6584dba30492f8fd695b3207f6f5d02f3b4f3f2ad48160336bbf2b633b4d6cab0a0364bc251becade29e52877d8b172cb1fa8e7e3bdb56eeff9eaf77e37ce44cfdbbc32c643308720c5e4e5d37074682"
                )
        );
    }
}
