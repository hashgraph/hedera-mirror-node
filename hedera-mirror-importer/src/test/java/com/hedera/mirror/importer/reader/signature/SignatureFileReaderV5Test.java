package com.hedera.mirror.importer.reader.signature;

/*
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

import static com.hedera.mirror.importer.reader.signature.SignatureFileReaderV5.HASH_CLASS_ID;
import static com.hedera.mirror.importer.reader.signature.SignatureFileReaderV5.HASH_CLASS_VERSION;
import static com.hedera.mirror.importer.reader.signature.SignatureFileReaderV5.HASH_DIGEST_TYPE;
import static com.hedera.mirror.importer.reader.signature.SignatureFileReaderV5.HASH_SIZE;
import static com.hedera.mirror.importer.reader.signature.SignatureFileReaderV5.OBJECT_STREAM_SIGNATURE_VERSION;
import static com.hedera.mirror.importer.reader.signature.SignatureFileReaderV5.SIGNATURE_CLASS_ID;
import static com.hedera.mirror.importer.reader.signature.SignatureFileReaderV5.SIGNATURE_CLASS_VERSION;
import static com.hedera.mirror.importer.reader.signature.SignatureFileReaderV5.SIGNATURE_FILE_FORMAT_VERSION;
import static com.hedera.mirror.importer.reader.signature.SignatureFileReaderV5.SIGNATURE_TYPE;
import static org.junit.Assert.assertNotNull;

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

class SignatureFileReaderV5Test extends AbstractSignatureFileReaderTest {

    @Value("classpath:data/signature/v5/2020-12-29T21_28_40.972713000Z.rcd_sig")
    private File signatureFile;

    @Resource
    SignatureFileReaderV5 fileReaderV5;

    private final byte[] SIGNATURE_FILE_FORMAT_VERSION_BYTES = new byte[] {SIGNATURE_FILE_FORMAT_VERSION};

    private static final byte[] OBJECT_STREAM_SIGNATURE_VERSION_BYTES = TestUtils
            .intToByteArray(OBJECT_STREAM_SIGNATURE_VERSION);

    private static final byte[] HASH_CLASS_ID_BYTES = TestUtils.longToByteArray(HASH_CLASS_ID);
    private static final byte[] HASH_CLASS_VERSION_BYTES = TestUtils.intToByteArray(HASH_CLASS_VERSION);
    private static final byte[] HASH_DIGEST_TYPE_BYTES = TestUtils.intToByteArray(HASH_DIGEST_TYPE);
    private static final byte[] HASH_LENGTH_BYTES = TestUtils.intToByteArray(HASH_SIZE);

    private static final byte[] SIGNATURE_CLASS_ID_BYTES = TestUtils.longToByteArray(SIGNATURE_CLASS_ID);
    private static final byte[] SIGNATURE_CLASS_VERSION_BYTES = TestUtils.intToByteArray(SIGNATURE_CLASS_VERSION);
    private static final byte[] SIGNATURE_TYPE_BYTES = TestUtils.intToByteArray(SIGNATURE_TYPE);

    private static final int SIGNATURE_LENGTH = 48;
    private static final byte[] SIGNATURE_LENGTH_BYTES = TestUtils.intToByteArray(SIGNATURE_LENGTH);

    @Test
    void testReadValidFile() throws IOException {
        try (InputStream stream = getInputStream(signatureFile)) {
            FileStreamSignature answer = fileReaderV5.read(stream);
            assertNotNull(answer);
            assertNotNull(answer.getSignature());
            assertNotNull(answer.getHash());
        }
    }

    @TestFactory
    Iterable<DynamicTest> testReadCorruptSignatureFileV5() {

        SignatureFileSection fileVersion = new SignatureFileSection(
                SIGNATURE_FILE_FORMAT_VERSION_BYTES,
                "invalidFileFormatVersion",
                incrementLastByte,
                "Unable to read signature file v5: file version");

        SignatureFileSection objectStreamSignatureVersion = new SignatureFileSection(
                OBJECT_STREAM_SIGNATURE_VERSION_BYTES,
                "invalidObjectStreamSignatureVersion",
                incrementLastByte,
                "Unable to read signature file v5: object stream signature version");

        SignatureFileSection hashClassId = new SignatureFileSection(
                HASH_CLASS_ID_BYTES,
                "invalidHashClassId",
                incrementLastByte,
                "Unable to read signature file v5 hash: invalid class id");

        SignatureFileSection hashClassVersion = new SignatureFileSection(
                HASH_CLASS_VERSION_BYTES,
                "invalidHashClassVersion",
                incrementLastByte,
                "Unable to read signature file v5 hash: invalid class version");

        SignatureFileSection hashDigestType = new SignatureFileSection(
                HASH_DIGEST_TYPE_BYTES,
                "invalidHashDigestType",
                incrementLastByte,
                "Unable to read signature file v5 hash: invalid digest type");

        SignatureFileSection hashLength = new SignatureFileSection(
                HASH_LENGTH_BYTES,
                "invalidHashLength",
                incrementLastByte,
                "Unable to read signature file v5 hash: invalid length");

        SignatureFileSection hash = new SignatureFileSection(
                TestUtils.generateRandomByteArray(HASH_SIZE),
                "incorrectHashLength",
                truncateLastByte,
                "Unable to read signature file v5 hash: listed length");

        SignatureFileSection signatureClassId = new SignatureFileSection(
                SIGNATURE_CLASS_ID_BYTES,
                "invalidSignatureClassId",
                incrementLastByte,
                "Unable to read signature file v5 signature: invalid signature class id");

        SignatureFileSection signatureClassVersion = new SignatureFileSection(
                SIGNATURE_CLASS_VERSION_BYTES,
                "invalidSignatureClassVersion",
                incrementLastByte,
                "Unable to read signature file v5 signature: invalid signature class version");

        SignatureFileSection signatureType = new SignatureFileSection(
                SIGNATURE_TYPE_BYTES,
                "invalidSignatureType",
                incrementLastByte,
                "Unable to read signature file v5 signature: invalid signature type");

        SignatureFileSection signatureLength = new SignatureFileSection(SIGNATURE_LENGTH_BYTES,
                null,
                null,
                null);

        SignatureFileSection signature = new SignatureFileSection(
                TestUtils.generateRandomByteArray(SIGNATURE_LENGTH),
                "incorrectSignatureLength",
                truncateLastByte,
                "Unable to read signature file v5 signature: listed signature length");

        List<SignatureFileSection> signatureFileSections = Arrays
                .asList(fileVersion, objectStreamSignatureVersion, hashClassId, hashClassVersion, hashDigestType,
                        hashLength, hash, signatureClassId, signatureClassVersion, signatureType, signatureLength,
                        signature);

        return generateCorruptedFileTests(fileReaderV5, signatureFileSections);
    }
}
