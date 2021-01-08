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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import org.junit.jupiter.api.DynamicTest;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.exception.SignatureFileParsingException;

abstract class AbstractSignatureFileReaderTest extends IntegrationTest {

    protected List<SignatureFileSection> signatureFileSections;

    protected static final SignatureFileSectionCorruptor incrementLastByte = (bytes -> {
        byte[] corruptBytes = Arrays.copyOf(bytes, bytes.length);
        corruptBytes[corruptBytes.length - 1] = (byte) (corruptBytes[corruptBytes.length - 1] + 1);
        return corruptBytes;
    });

    protected static final SignatureFileSectionCorruptor truncateLastByte = (bytes -> Arrays
            .copyOfRange(bytes, 0, bytes.length - 1));

    protected abstract SignatureFileReader getFileReader();

    protected InputStream getInputStream(File file) throws FileNotFoundException {
        return new BufferedInputStream(new FileInputStream(file));
    }

    protected InputStream getInputStream(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    protected Iterable<DynamicTest> generateCorruptedFileTests() {
        List<DynamicTest> testCases = new ArrayList<>();

        byte[] validSignatureBytes = new byte[0];
        //Add a test for an empty stream
        InputStream blankInputStream = getInputStream(new byte[0]);
        testCases.add(DynamicTest.dynamicTest(
                "blankFile",
                () -> {
                    SignatureFileParsingException e = assertThrows(SignatureFileParsingException.class,
                            () -> {
                                getFileReader().read(blankInputStream);
                            });
                    assertTrue(e.getMessage().contains("EOFException"));
                }));

        for (int i = 0; i < signatureFileSections.size(); i++) {
            //Add new valid section of the file
            validSignatureBytes = i == 0 ? validSignatureBytes :
                    Bytes.concat(validSignatureBytes, signatureFileSections.get(i - 1)
                            .getValidDataBytes());

            SignatureFileSection sectionToCorrupt = signatureFileSections.get(i);

            //Some sections are not validated
            if (sectionToCorrupt.getInvalidExceptionMessage() == null) {
                continue;
            }

            byte[] fullSignatureBytes = Bytes.concat(validSignatureBytes, sectionToCorrupt.getCorruptBytes());
            InputStream corruptInputStream = getInputStream(fullSignatureBytes);
            testCases.add(DynamicTest.dynamicTest(
                    signatureFileSections.get(i).getCorruptTestName(),
                    () -> {
                        SignatureFileParsingException e = assertThrows(SignatureFileParsingException.class,
                                () -> {
                                    getFileReader().read(corruptInputStream);
                                });
                        sectionToCorrupt.validateError(e.getMessage());
                    }));
        }
        return testCases;
    }

    @Value
    @AllArgsConstructor
    protected class SignatureFileSection {
        private final byte[] validDataBytes;
        private final String corruptTestName;
        private final SignatureFileSectionCorruptor byteCorrupter;
        private final String invalidExceptionMessage;
        @Getter(lazy = true)
        private final byte[] corruptBytes = byteCorrupter.corruptBytes(validDataBytes);

        public void validateError(String errorMessage) {
            assertTrue(errorMessage.contains(invalidExceptionMessage));
        }
    }

    protected interface SignatureFileSectionCorruptor {
        byte[] corruptBytes(byte[] bytes);
    }
}
