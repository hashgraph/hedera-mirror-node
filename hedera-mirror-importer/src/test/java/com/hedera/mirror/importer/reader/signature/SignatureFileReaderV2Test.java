package com.hedera.mirror.importer.reader.signature;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import javax.annotation.Resource;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Value;

import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.FileStreamSignature;
import com.hedera.mirror.importer.exception.SignatureFileParsingException;

class SignatureFileReaderV2Test extends AbstractSignatureFileReaderTest {

    @Value("classpath:data/signature/v2/2019-08-30T18_10_00.419072Z.rcd_sig")
    private File signatureFile;

    @Resource
    SignatureFileReaderV2 fileReaderV2;

    private static final int SIGNATURE_LENGTH = 48;
    private static final byte[] SIGNATURE_LENGTH_BYTES = TestUtils.intToByteArray(SIGNATURE_LENGTH);

    @Test
    void testReadValidFile() throws IOException {
        try (InputStream stream = getInputStream(signatureFile)) {
            FileStreamSignature answer = fileReaderV2.read(stream);
            assertNotNull(answer);
            assertNotNull(answer.getSignature());
            assertNotNull(answer.getHash());
        }
    }

    @Test
    void testReadBlankFile() throws IOException {
        try (InputStream stream = getInputStream(new byte[0])) {
            SignatureFileParsingException exception = assertThrows(SignatureFileParsingException.class, () -> {
                fileReaderV2.read(stream);
            });
            assertTrue(exception.getCause() instanceof IOException);
        }
    }

    @TestFactory
    Iterable<DynamicTest> corruptSignatureFileV2() {

        CorruptSignatureFileSection hashDelimiter =
                new CorruptSignatureFileSection("invalidHashDelimiter", new byte[] {SIGNATURE_TYPE_FILE_HASH},
                        "Unable " +
                                "to read signature file hash: type delimiter", incrementLastByte);

        CorruptSignatureFileSection hash = new CorruptSignatureFileSection("invalidHashLength",
                generateRandomByteArray(HASH_SIZE),
                "Unable to read " +
                        "signature file hash: hash length", truncateLastByte);

        CorruptSignatureFileSection signatureDelimiter =
                new CorruptSignatureFileSection("invalidSignatureDelimiter", new byte[] {SIGNATURE_TYPE_SIGNATURE},
                        "Unable to read signature file signature: type delimiter", incrementLastByte);

        CorruptSignatureFileSection signatureLength = new CorruptSignatureFileSection(null, SIGNATURE_LENGTH_BYTES,
                null,
                null);

        CorruptSignatureFileSection signature =
                new CorruptSignatureFileSection("incorrectSignatureLength", generateRandomByteArray(SIGNATURE_LENGTH),
                        "EOFException", truncateLastByte);

        CorruptSignatureFileSection invalidExtraData = new CorruptSignatureFileSection("invalidExtraData",
                new byte[] {1},
                "Extra data discovered in signature file", returnValidBytes);

        signatureFileSections = Arrays
                .asList(hashDelimiter, hash, signatureDelimiter, signatureLength, signature, invalidExtraData);

        return generateCorruptedFileTests();
    }

    @Override
    protected SignatureFileReader getFileReader() {
        return fileReaderV2;
    }
}
