package com.hedera.mirror.importer.reader.signature;

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

import static com.hedera.mirror.importer.reader.signature.SignatureFileReaderV2.HASH_SIZE;
import static com.hedera.mirror.importer.reader.signature.SignatureFileReaderV2.SIGNATURE_TYPE_FILE_HASH;
import static com.hedera.mirror.importer.reader.signature.SignatureFileReaderV2.SIGNATURE_TYPE_SIGNATURE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

import com.google.api.client.util.Base64;
import com.google.common.primitives.Ints;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Resource;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Value;

import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.FileStreamSignature;

class SignatureFileReaderV2Test extends AbstractSignatureFileReaderTest {

    @Value("classpath:data/signature/v2/2019-08-30T18_10_00.419072Z.rcd_sig")
    private File signatureFile;

    private static final String entireFileHashBase64 = "WRVY4Fm9FinuOGxONaaHW0xnoJZxj10iV3KmUQQnFRiUFN99tViEle" +
            "+yqF3EoP/a";
    private static final String entireFileSignatureBase64 = "nOVITUEb1WfYLJN4Jp2/aIEYTiqEzfTSMU5Y6KDKbCi55" +
            "+vsWasqfQaUE4JLGC+JO+Ky2Ui1WsnDHCDxxE/Jx0K+90n2eg8pFZLlA6xcMZ4fLchy6+mhQWYhtRSdCr6aO0JV4lOtFUSZ" +
            "/DC4qIiwo0VaHNkWCw+bhrERFKeTZcxzHtiElGEeggxwFMvNXBUigU2LoWWLm5BDS9N35iRrfEf6g0HybYe2tOiA717vlKvIMr0t" +
            "YJmlLLKUB9brEUpdSm8RRLs+jzEY76YT7Uv6WzIq04SetI+GUOMkEXDNvtcSKnE8625L7qmhbiiX4Ub90jCxCqt6JHXrCM1VsYWEn" +
            "/oUesRi5pnATgjqZOXycMegavb1Ikf3GoQAvn1Bx6EO14Uh7hVMxa/NYMtSVNQ17QG6QtA4j7viVvJ9EPSiCsmg3Cp2PhBW5ZPshq" +
            "+ExciGbnXFu+ytLZGSwKhePwuLQsBNTbGUcDFy1IJge95tEweR51Y1Nfh6PqPTnkdirRGO";

    @Resource
    SignatureFileReaderV2 fileReaderV2;

    private static final int SIGNATURE_LENGTH = 48;

    @Test
    void testReadValidFile() throws IOException {
        try (InputStream stream = getInputStream(signatureFile)) {
            FileStreamSignature fileStreamSignature = fileReaderV2.read(stream);
            assertNotNull(fileStreamSignature);
            assertArrayEquals(Base64.decodeBase64(entireFileHashBase64.getBytes()), fileStreamSignature
                    .getEntireFileHash());
            assertArrayEquals(Base64.decodeBase64(entireFileSignatureBase64.getBytes()), fileStreamSignature
                    .getEntireFilesignature());
        }
    }

    @TestFactory
    Iterable<DynamicTest> testReadCorruptSignatureFileV2() {
        SignatureFileSection hashDelimiter = new SignatureFileSection(
                new byte[] {SIGNATURE_TYPE_FILE_HASH},
                "invalidHashDelimiter",
                incrementLastByte,
                "hashDelimiter");

        SignatureFileSection hash = new SignatureFileSection(
                TestUtils.generateRandomByteArray(HASH_SIZE),
                "invalidHashLength",
                truncateLastByte,
                "hash");

        SignatureFileSection signatureDelimiter = new SignatureFileSection(
                new byte[] {SIGNATURE_TYPE_SIGNATURE},
                "invalidSignatureDelimiter",
                incrementLastByte,
                "signatureDelimiter");

        SignatureFileSection signatureLength = new SignatureFileSection(
                Ints.toByteArray(SIGNATURE_LENGTH),
                null,
                null,
                null);

        SignatureFileSection signature = new SignatureFileSection(
                TestUtils.generateRandomByteArray(SIGNATURE_LENGTH),
                "incorrectSignatureLength",
                truncateLastByte,
                "EOFException");

        SignatureFileSection invalidExtraData = new SignatureFileSection(
                new byte[0],
                "invalidExtraData",
                bytes -> new byte[] {1},
                "Extra data discovered in signature file");

        List<SignatureFileSection> signatureFileSections = Arrays
                .asList(hashDelimiter, hash, signatureDelimiter, signatureLength, signature, invalidExtraData);

        return generateCorruptedFileTests(fileReaderV2, signatureFileSections);
    }
}
