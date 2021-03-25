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

import com.google.common.primitives.Bytes;
import java.io.IOException;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.SignatureFileParsingException;

@ExtendWith(MockitoExtension.class)
class CompositeSignatureFileReaderTest {

    private static final String SIGNATURE_FILENAME = "2021-03-10T16_30_00Z.rcd_sig";

    @Mock
    SignatureFileReaderV2 signatureFileReaderV2;

    @Mock
    SignatureFileReaderV5 signatureFileReaderV5;

    private CompositeSignatureFileReader compositeBalanceFileReader;

    @BeforeEach
    void setUp() {
        compositeBalanceFileReader = new CompositeSignatureFileReader(signatureFileReaderV2, signatureFileReaderV5);
    }

    @Test
    void testValidV2() throws Exception {
        byte[] versionNumber = {SignatureFileReaderV2.SIGNATURE_TYPE_FILE_HASH};
        byte[] randomExtraBytes = new byte[3];
        SecureRandom.getInstanceStrong().nextBytes(randomExtraBytes);
        byte[] bytes = Bytes.concat(versionNumber, randomExtraBytes);
        StreamFileData streamFileData = StreamFileData.from(SIGNATURE_FILENAME, bytes);
        compositeBalanceFileReader.read(streamFileData);
        verify(signatureFileReaderV2, times(1)).read(any(StreamFileData.class));
        verify(signatureFileReaderV5, times(0)).read(any(StreamFileData.class));
    }

    @Test
    void testValidV5() throws Exception {
        byte[] versionNumber = {SignatureFileReaderV5.SIGNATURE_FILE_FORMAT_VERSION};
        byte[] randomExtraBytes = new byte[3];
        SecureRandom.getInstanceStrong().nextBytes(randomExtraBytes);
        byte[] bytes = Bytes.concat(versionNumber, randomExtraBytes);
        StreamFileData streamFileData = StreamFileData.from(SIGNATURE_FILENAME, bytes);
        compositeBalanceFileReader.read(streamFileData);
        verify(signatureFileReaderV5, times(1)).read(any(StreamFileData.class));
        verify(signatureFileReaderV2, times(0)).read(any(StreamFileData.class));
    }

    @Test
    void testBlankFile() {
        StreamFileData blankFileData = StreamFileData.from(SIGNATURE_FILENAME, new byte[0]);
        SignatureFileParsingException exception = assertThrows(SignatureFileParsingException.class, () -> {
            compositeBalanceFileReader.read(blankFileData);
        });
        assertAll(
                () -> assertTrue(exception.getMessage().contains("Error reading signature file")),
                () -> assertTrue(exception.getCause() instanceof IOException)
        );
    }

    @Test
    void testInvalidFileVersion() {
        byte[] invalidVersionNumber = {12};
        StreamFileData invalidFileData = StreamFileData.from(SIGNATURE_FILENAME, invalidVersionNumber);
        SignatureFileParsingException exception = assertThrows(SignatureFileParsingException.class, () -> {
            compositeBalanceFileReader.read(invalidFileData);
        });
        assertTrue(exception.getMessage().contains("Unsupported signature file version: " + invalidVersionNumber[0]));
    }
}
