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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import com.hedera.mirror.importer.domain.FileStreamSignature;

class SignatureFileReaderV5Test extends AbstractSignatureFileReaderTest {

    @Value("classpath:data/signature/v5/2020-12-29T21_28_40.972713000Z.rcd_sig")
    private File signatureFile;

    @Resource
    SignatureFileReaderV5 fileReaderV5;

    @Test
    void testReadValidFile() throws IOException {
        try (InputStream stream = getInputStream(signatureFile)) {
            FileStreamSignature answer = fileReaderV5.read(stream);
            assertNotNull(answer);
            assertNotNull(answer.getSignature());
            assertNotNull(answer.getHash());
        }
    }
}
