package com.hedera.mirror.importer.domain;

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

import java.io.File;
import lombok.Data;
import lombok.ToString;

import com.hedera.mirror.importer.util.Utility;

@Data
@ToString(exclude = {"hash", "signature"})
public class FileStreamSignature implements Comparable<FileStreamSignature> {

    private File file;
    private byte[] hash;
    private String node;
    private byte[] signature;
    private SignatureStatus status = SignatureStatus.DOWNLOADED;

    @Override
    public int compareTo(FileStreamSignature other) {
        return file.compareTo(other.getFile());
    }

    public String getHashAsHex() {
        return Utility.bytesToHex(hash);
    }

    public enum SignatureStatus {
        DOWNLOADED,        // Signature has been downloaded but not verified
        PARSED,            // Extracted hash and signature data from file
        VERIFIED,          // Signature has been verified against the node's public key
        CONSENSUS_REACHED  // At least 1/3 of all nodes have been verified
    }
}
