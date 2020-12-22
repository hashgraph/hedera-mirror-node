package com.hedera.mirror.importer.reader.signature;/*
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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;

import com.hedera.mirror.importer.exception.InvalidDatasetException;

@Log4j2
@Named
@RequiredArgsConstructor
public class CompositeSignatureFileReader implements SignatureFileReader {

    private final SignatureFileReaderV2 signatureFileReaderV2;

    @Override
    public Pair<byte[], byte[]> read(BufferedInputStream bufferedInputStream) {

        try (DataInputStream dataInputStream =
                     new DataInputStream(bufferedInputStream)) {
            dataInputStream.mark(Integer.BYTES);
            int version = dataInputStream.readInt();
            dataInputStream.reset();
            switch (version) {
                default:
                    return signatureFileReaderV2.read(bufferedInputStream);
            }
        } catch (
                IOException ex) {
            throw new InvalidDatasetException("Error reading account balance file", ex);
        }
    }
}
