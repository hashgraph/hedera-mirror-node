package com.hedera.mirror.importer.reader.signature;

/*
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

import static com.hedera.mirror.importer.reader.signature.SignatureFileReaderV5.HASH_DIGEST_TYPE;
import static com.hedera.mirror.importer.reader.signature.SignatureFileReaderV5.HASH_SIZE;
import static com.hedera.mirror.importer.reader.signature.SignatureFileReaderV5.OBJECT_STREAM_SIGNATURE_VERSION;
import static com.hedera.mirror.importer.reader.signature.SignatureFileReaderV5.SIGNATURE_FILE_FORMAT_VERSION;
import static com.hedera.mirror.importer.reader.signature.SignatureFileReaderV5.SIGNATURE_TYPE;
import static org.junit.Assert.assertNotNull;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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

    private static final int SIGNATURE_LENGTH = 48;
    private static final long HASH_CLASS_ID = 0xf422da83a251741eL;
    private static final int HASH_CLASS_VERSION = 1;
    private static final long SIGNATURE_CLASS_ID = 0x13dc4b399b245c69L;
    private static final int SIGNATURE_CLASS_VERSION = 1;

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
                new byte[] {SIGNATURE_FILE_FORMAT_VERSION},
                "invalidFileFormatVersion",
                incrementLastByte,
                "fileVersion");

        SignatureFileSection objectStreamSignatureVersion = new SignatureFileSection(
                Ints.toByteArray(OBJECT_STREAM_SIGNATURE_VERSION),
                "invalidObjectStreamSignatureVersion",
                incrementLastByte,
                "objectStreamSignatureVersion");

        List<SignatureFileSection> signatureFileSections = new ArrayList<>();
        signatureFileSections.add(fileVersion);
        signatureFileSections.add(objectStreamSignatureVersion);

        signatureFileSections.addAll(buildHashSections("entire"));
        signatureFileSections.addAll(buildSignatureSections("entire"));
        signatureFileSections.addAll(buildHashSections("metadata"));
        signatureFileSections.addAll(buildSignatureSections("metadata"));

        return generateCorruptedFileTests(fileReaderV5, signatureFileSections);
    }

    private List<SignatureFileSection> buildHashSections(String hashName) {
        SignatureFileSection hashClassId = new SignatureFileSection(
                Longs.toByteArray(HASH_CLASS_ID),
                null,
                null,
                null);

        SignatureFileSection hashClassVersion = new SignatureFileSection(
                Ints.toByteArray(HASH_CLASS_VERSION),
                null,
                null,
                null);

        SignatureFileSection hashDigestType = new SignatureFileSection(
                Ints.toByteArray(HASH_DIGEST_TYPE),
                "invalidHashDigestType:" + hashName,
                incrementLastByte,
                "hashDigestType:" + hashName);

        SignatureFileSection hashLength = new SignatureFileSection(
                Ints.toByteArray(HASH_SIZE),
                "invalidHashLength:" + hashName,
                incrementLastByte,
                "hashLength:" + hashName);

        SignatureFileSection hash = new SignatureFileSection(
                TestUtils.generateRandomByteArray(HASH_SIZE),
                "incorrectHashLength:" + hashName,
                truncateLastByte,
                "actualHashLength:" + hashName);
        return Arrays.asList(hashClassId, hashClassVersion, hashDigestType, hashLength, hash);
    }

    private List<SignatureFileSection> buildSignatureSections(String hashName) {
        SignatureFileSection signatureClassId = new SignatureFileSection(
                Longs.toByteArray(SIGNATURE_CLASS_ID),
                null,
                null,
                null);

        SignatureFileSection signatureClassVersion = new SignatureFileSection(
                Ints.toByteArray(SIGNATURE_CLASS_VERSION),
                null,
                null,
                null);

        SignatureFileSection signatureType = new SignatureFileSection(
                Ints.toByteArray(SIGNATURE_TYPE),
                "invalidSignatureType:" + hashName,
                incrementLastByte,
                "signatureType:" + hashName);

        SignatureFileSection signatureLength = new SignatureFileSection(
                Ints.toByteArray(SIGNATURE_LENGTH),
                null,
                null,
                null);

        SignatureFileSection signature = new SignatureFileSection(
                TestUtils.generateRandomByteArray(SIGNATURE_LENGTH),
                "incorrectSignatureLength:" + hashName,
                truncateLastByte,
                "actualSignatureLength:" + hashName);
        return Arrays.asList(signatureClassId, signatureClassVersion, signatureType, signatureLength, signature);
    }
}
