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

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.importer.exception.SignatureFileParsingException;

@ExtendWith(MockitoExtension.class)
class CompositeSignatureFileReaderTest {

    @Mock
    SignatureFileReaderV2 signatureFileReaderV2;

    private CompositeSignatureFileReader compositeBalanceFileReader;

    @BeforeEach
    void setUp() {
        compositeBalanceFileReader = new CompositeSignatureFileReader(signatureFileReaderV2);
    }

    @Test
    void testValidV2() throws IOException {
        byte[] versionNumber = {SignatureFileReaderV2.SIGNATURE_TYPE_FILE_HASH};
        try (InputStream stream = getInputStream(versionNumber)) {
            compositeBalanceFileReader.read(stream);
            verify(signatureFileReaderV2, times(1)).read(any(InputStream.class));
        }
    }

    @Test
    void testBlankFile() throws IOException {
        try (InputStream stream = getInputStream(new byte[0])) {
            SignatureFileParsingException exception = assertThrows(SignatureFileParsingException.class, () -> {
                compositeBalanceFileReader.read(stream);
            });
            assertAll(
                    () -> assertTrue(exception.getMessage().contains("Error reading signature file")),
                    () -> assertTrue(exception.getCause() instanceof IOException)
            );
        }
    }

    @Test
    void testInvalidFileVersion() throws IOException {
        byte[] invalidVersionNumber = {12};
        try (InputStream stream = getInputStream(invalidVersionNumber)) {
            SignatureFileParsingException exception = assertThrows(SignatureFileParsingException.class, () -> {
                compositeBalanceFileReader.read(stream);
            });
            assertTrue(exception.getMessage()
                    .contains("Unsupported signature file version: " + invalidVersionNumber[0]));
        }
    }

    private InputStream getInputStream(byte[] bytes) throws FileNotFoundException {
        return new ByteArrayInputStream(bytes);
    }
}
