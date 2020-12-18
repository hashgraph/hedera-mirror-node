package com.hedera.mirror.importer.reader.record;

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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.inject.Named;

import com.hedera.mirror.importer.exception.RecordFileReaderException;

@Named
public class RecordFileReaderImplV1 extends AbstractRecordFileReader {

    public RecordFileReaderImplV1() {
        super(1);
    }

    @Override
    protected RecordFileDigest getRecordFileDigest() {
        try {
            return new RecordFileDigestV1();
        } catch (NoSuchAlgorithmException e) {
            throw new RecordFileReaderException("Unable to instantiate RecordFileDigestV1" , e);
        }
    }

    private static class RecordFileDigestV1 implements RecordFileDigest {

        private final MessageDigest md;

        RecordFileDigestV1() throws NoSuchAlgorithmException {
            md = MessageDigest.getInstance(HASH_ALGORITHM);
        }

        @Override
        public void updateHeader(byte input) {
            md.update(input);
        }

        @Override
        public void updateHeader(byte[] input) {
            md.update(input);
        }

        @Override
        public void updateBody(byte input) {
            updateHeader(input);
        }

        @Override
        public void updateBody(byte[] input) {
            updateHeader(input);
        }

        @Override
        public byte[] digest() {
            return md.digest();
        }
    }
}
