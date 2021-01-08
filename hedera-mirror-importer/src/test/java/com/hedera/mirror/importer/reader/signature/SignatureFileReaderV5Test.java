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
import static com.hedera.mirror.importer.reader.signature.SignatureFileReaderV5.HASH_LENGTH;
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

    private static final byte[] OBJECT_STREAM_SIGNATURE_VERSION_BYTES =
            TestUtils.intToByteArray(OBJECT_STREAM_SIGNATURE_VERSION);

    private static final byte[] HASH_CLASS_ID_BYTES = TestUtils.longToByteArray(HASH_CLASS_ID);
    private static final byte[] HASH_CLASS_VERSION_BYTES = TestUtils.intToByteArray(HASH_CLASS_VERSION);
    private static final byte[] HASH_DIGEST_TYPE_BYTES = TestUtils.intToByteArray(HASH_DIGEST_TYPE);
    private static final byte[] HASH_LENGTH_BYTES = TestUtils.intToByteArray(HASH_LENGTH);

    private static final byte[] SIGNATURE_CLASS_ID_BYTES = TestUtils.longToByteArray(SIGNATURE_CLASS_ID);
    private static final byte[] SIGNATURE_CLASS_VERSION_BYTES = TestUtils.intToByteArray(SIGNATURE_CLASS_VERSION);
    private static final byte[] SIGNATURE_TYPE_BYTES = TestUtils.intToByteArray(SIGNATURE_TYPE);

    private static final int SIGNATURE_LENGTH = 48;
    private static final byte[] SIGNATURE_LENGTH_BYTES = TestUtils.intToByteArray(SIGNATURE_LENGTH);

    @TestFactory
    Iterable<DynamicTest> corruptSignatureFileV5() {

        CorruptSignatureFileSection blankStart = new CorruptSignatureFileSection("blankFile", new byte[0],
                "EOFException",
                returnValidBytes);

        CorruptSignatureFileSection fileVersion = new CorruptSignatureFileSection("invalidFileFormatVersion",
                SIGNATURE_FILE_FORMAT_VERSION_BYTES, "Unable to read signature file v5: file version",
                incrementLastByte);

        CorruptSignatureFileSection objectStreamSignatureVersion =
                new CorruptSignatureFileSection("invalidObjectStreamSignatureVersion",
                        OBJECT_STREAM_SIGNATURE_VERSION_BYTES, "Unable to read signature file v5: object stream " +
                        "signature version", incrementLastByte);

        CorruptSignatureFileSection entireHashClassId = new CorruptSignatureFileSection("invalidHashClassId",
                HASH_CLASS_ID_BYTES, "Unable to read signature file v5 hash: invalid class id", incrementLastByte);

        CorruptSignatureFileSection entireHashClassVersion = new CorruptSignatureFileSection("invalidHashClassVersion",
                HASH_CLASS_VERSION_BYTES, "Unable to " + "read signature file v5 hash: invalid class version",
                incrementLastByte);
        CorruptSignatureFileSection entireHashDigestType = new CorruptSignatureFileSection("invalidHashDigestType",
                HASH_DIGEST_TYPE_BYTES,
                "Unable to read " +
                        "signature file v5 hash: invalid digest type", incrementLastByte);
        CorruptSignatureFileSection entireHashLegnthOfHash = new CorruptSignatureFileSection("invalidHashLength",
                HASH_LENGTH_BYTES,
                "Unable to read " +
                        "signature file v5 hash: invalid length", incrementLastByte);

        CorruptSignatureFileSection entireHashBytes =
                new CorruptSignatureFileSection("incorrectHashLength", generateRandomByteArray(HASH_LENGTH), "Unable" +
                        " to read signature file v5 hash: listed length", truncateLastByte);

        CorruptSignatureFileSection signatureClassId = new CorruptSignatureFileSection("invalidSignatureClassId",
                SIGNATURE_CLASS_ID_BYTES,
                "Unable to read " +
                        "signature file v5 signature: invalid signature class id", incrementLastByte);

        CorruptSignatureFileSection signatureClassVersion =
                new CorruptSignatureFileSection("invalidSignatureClassVersion", SIGNATURE_CLASS_VERSION_BYTES,
                        "Unable " +
                        "to read signature file v5 signature: invalid signature class version", incrementLastByte);

        CorruptSignatureFileSection signatureType = new CorruptSignatureFileSection("invalidSignatureType",
                SIGNATURE_TYPE_BYTES,
                "Unable to " +
                        "read signature" +
                        " file v5 signature: invalid signature type", incrementLastByte);

        CorruptSignatureFileSection signatureLength = new CorruptSignatureFileSection(null,
                SIGNATURE_LENGTH_BYTES, null,
                null);

        CorruptSignatureFileSection signature =
                new CorruptSignatureFileSection("incorrectSignatureLength", generateRandomByteArray(SIGNATURE_LENGTH)
                        , "Unable " +
                        "to read signature file v5 " +
                        "signature: listed signature length",
                        truncateLastByte);

        signatureFileSections = Arrays
                .asList(blankStart, fileVersion, objectStreamSignatureVersion, entireHashClassId,
                        entireHashClassVersion,
                        entireHashDigestType, entireHashLegnthOfHash, entireHashBytes, signatureClassId,
                        signatureClassVersion, signatureType, signatureLength, signature);

        return generateCorruptedFileTests();
    }

    @Test
    void testReadValidFile() throws IOException {
        try (InputStream stream = getInputStream(signatureFile)) {
            FileStreamSignature answer = fileReaderV5.read(stream);
            assertNotNull(answer);
            assertNotNull(answer.getSignature());
            assertNotNull(answer.getHash());
        }
    }

    @Override
    protected SignatureFileReader getFileReader() {
        return fileReaderV5;
    }
}
