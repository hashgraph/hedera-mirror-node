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

import java.io.InputStream;
import org.apache.commons.lang3.tuple.Pair;

public interface SignatureFileReader {
    /**
     * Read an InputStream containing signature file data and extract the signature and the file hash. The InputStream
     * is closed internally,
     *
     * @param signatureFileData The input stream from a signature file to read
     * @return Pair of byte arrays, the left holding the file hash and the right the signature
     */
    Pair<byte[], byte[]> read(InputStream signatureFileData);
}
