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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.common.primitives.Bytes;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import javax.annotation.Resource;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import com.hedera.mirror.importer.domain.FileStreamSignature;
import com.hedera.mirror.importer.exception.SignatureFileParsingException;

class SignatureFileReaderV2Test extends AbstractSignatureFileReaderTest {

    @Value("classpath:data/signature/v2/2019-08-30T18_10_00.419072Z.rcd_sig")
    private File balanceFile;

    @Resource
    SignatureFileReaderV2 fileReaderV2;

    @Test
    void testReadValidFile() throws IOException {
        try (InputStream bs = getInputStream(balanceFile)) {
            FileStreamSignature answer = fileReaderV2.read(bs);
            assertNotNull(answer);
            assertNotNull(answer.getSignature());
            assertNotNull(answer.getHash());
        }
    }

    @Test
    void testReadBlankFile() throws IOException {
        SignatureFileParsingException exception = assertThrows(SignatureFileParsingException.class, () -> {
            try (InputStream stream = getInputStream(createTempFile())) {
                fileReaderV2.read(stream);
            }
        });
        assertTrue(exception.getCause() instanceof IOException);
    }

    @Test
    void testReadFileWithExtraData() throws IOException {
        File testFile = createTempFile();
        byte[] bytes = FileUtils.readFileToByteArray(balanceFile);
        byte[] extraBytes = "extra".getBytes();
        byte[] newFileBytes = Bytes.concat(bytes, extraBytes);
        FileUtils.writeByteArrayToFile(testFile, newFileBytes);
        SignatureFileParsingException exception = assertThrows(SignatureFileParsingException.class, () -> {
            fileReaderV2.read(getInputStream(testFile));
        });
        assertTrue(exception.getMessage().contains("Extra data discovered in signature file"));
    }

    @Test
    void testReadFileHashWrongDelimiter() throws IOException {
        File testFile = createTempFile();
        byte[] bytes = FileUtils.readFileToByteArray(balanceFile);
        byte[] invalidDelimiter = {1};
        byte[] newFileBytes = Bytes.concat(invalidDelimiter, bytes);
        FileUtils.writeByteArrayToFile(testFile, newFileBytes);
        SignatureFileParsingException exception = assertThrows(SignatureFileParsingException.class, () -> {
            fileReaderV2.read(getInputStream(testFile));
        });
        assertTrue(exception.getMessage()
                .contains("Unable to read signature file hash: type delimiter " + invalidDelimiter[0]));
    }

    @Test
    void testReadFileHashTooShort() throws IOException {
        File testFile = createTempFile();
        byte[] bytes = FileUtils.readFileToByteArray(balanceFile);
        //Creating a file with only the first 47 bytes of the original (one less than the expected hash length)
        byte[] shortenedBytes = Arrays.copyOfRange(bytes, 0, 48);
        FileUtils.writeByteArrayToFile(testFile, shortenedBytes);
        SignatureFileParsingException exception = assertThrows(SignatureFileParsingException.class, () -> {
            fileReaderV2.read(getInputStream(testFile));
        });
        assertTrue(exception.getMessage()
                .contains("Unable to read signature file hash: hash length 47"));
    }

    @Test
    void testReadFileSignatureWrongDelimiter() throws IOException {
        File testFile = createTempFile();
        byte[] bytes = FileUtils.readFileToByteArray(balanceFile);
        byte[] invalidDelimiter = {1};
        byte[] hashBytes = Arrays.copyOfRange(bytes, 0, 49);
        byte[] signatureBytes = Arrays.copyOfRange(bytes, 50, bytes.length);

        byte[] newFileBytes = Bytes.concat(hashBytes, invalidDelimiter, signatureBytes);
        FileUtils.writeByteArrayToFile(testFile, newFileBytes);
        SignatureFileParsingException exception = assertThrows(SignatureFileParsingException.class, () -> {
            fileReaderV2.read(getInputStream(testFile));
        });
        assertTrue(exception.getMessage()
                .contains("Unable to read signature file signature: type delimiter " + invalidDelimiter[0]));
    }
}
