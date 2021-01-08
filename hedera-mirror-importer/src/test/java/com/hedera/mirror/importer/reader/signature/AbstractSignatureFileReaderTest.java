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

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.common.primitives.Bytes;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.junit.jupiter.api.DynamicTest;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.exception.SignatureFileParsingException;

abstract class AbstractSignatureFileReaderTest extends IntegrationTest {

    protected List<CorruptSignatureFileSection> signatureFileSections;

    protected static final ByteCorrupter incrementLastByte = (bytes -> {
        byte[] corruptBytes = Arrays.copyOf(bytes, bytes.length);
        corruptBytes[corruptBytes.length - 1] = (byte) (corruptBytes[corruptBytes.length - 1] + 1);
        return corruptBytes;
    });

    protected static final ByteCorrupter truncateLastByte = (bytes -> Arrays.copyOfRange(bytes, 0, bytes.length - 1));

    protected static final ByteCorrupter returnValidBytes = (bytes -> bytes);

    protected byte[] generateRandomByteArray(int size) {
        byte[] hashBytes = new byte[size];
        new SecureRandom().nextBytes(hashBytes);
        return hashBytes;
    }

    protected abstract SignatureFileReader getFileReader();

    protected InputStream getInputStream(File file) throws FileNotFoundException {
        return new BufferedInputStream(new FileInputStream(file));
    }

    protected InputStream getInputStream(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    protected Iterable<DynamicTest> generateCorruptedFileTests() {
        List<DynamicTest> test = new ArrayList<>();

        byte[] validSignatureBytes = new byte[] {};

        for (int i = 0; i < signatureFileSections.size(); i++) {

            validSignatureBytes = Bytes
                    .concat(validSignatureBytes, i > 0 ? signatureFileSections.get(i - 1)
                            .getValidDataBytes() : new byte[0]);
            CorruptSignatureFileSection corruptSection = signatureFileSections.get(i);
            if (corruptSection.getByteCorrupter() == null) {
                continue;
            }
            byte[] fullSignatureBytes = Bytes.concat(validSignatureBytes, corruptSection.corruptSection());
            test.add(DynamicTest
                    .dynamicTest("testReader 2 " + signatureFileSections.get(i).getInvalidExceptionMessage(), () -> {
                        SignatureFileParsingException e = assertThrows(SignatureFileParsingException.class,
                                () -> {
                                    getFileReader().read(getInputStream(fullSignatureBytes));
                                });
                        corruptSection.validateError(e.getMessage());
                    }));
        }
        return test;
    }

    @Data
    @AllArgsConstructor
    protected class CorruptSignatureFileSection {
        String corruptTestName;
        private byte[] validDataBytes;
        private String invalidExceptionMessage;
        private ByteCorrupter byteCorrupter;

        public void validateError(String errorMessage) {
            assertTrue(errorMessage.contains(invalidExceptionMessage));
        }

        public byte[] corruptSection() {
            return byteCorrupter.corruptBytes(validDataBytes);
        }
    }

    protected interface ByteCorrupter {
        byte[] corruptBytes(byte[] bytes);
    }
}
