/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.reader.signature;

import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamFileSignature;

public interface SignatureFileReader {
    /**
     * 1. Extract the Hash of the content of corresponding RecordStream file. This Hash is the signed Content of this
     * signature 2. Extract signature from the file.
     *
     * @param signatureFileData {@link StreamFileData} object for the signature file
     * @return streamFileSignature containing the hash of the corresponding RecordStream file and the signature
     */
    StreamFileSignature read(StreamFileData signatureFileData);
}
