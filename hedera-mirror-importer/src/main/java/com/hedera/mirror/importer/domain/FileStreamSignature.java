package com.hedera.mirror.importer.domain;

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

import java.io.File;
import lombok.Data;
import lombok.ToString;

import com.hedera.mirror.importer.util.Utility;

@Data
@ToString(exclude = {"hash", "signature", "metadataHash", "metadataSignature"})
public class FileStreamSignature implements Comparable<FileStreamSignature> {

    private File file;
    private byte[] entireFileHash;
    private EntityId nodeAccountId;
    private SignatureType signatureType;
    private byte[] entireFilesignature;
    private SignatureStatus status = SignatureStatus.DOWNLOADED;
    private byte[] metadataHash;
    private byte[] metadataSignature;

    @Override
    public int compareTo(FileStreamSignature other) {
        return file.compareTo(other.getFile());
    }

    public String getEntireFileHashAsHex() {
        return Utility.bytesToHex(entireFileHash);
    }

    public String getMetadataHashAsHex() {
        return Utility.bytesToHex(metadataHash);
    }

    public String getNodeAccountIdString() {
        return nodeAccountId.entityIdToString();
    }

    public enum SignatureStatus {
        DOWNLOADED,        // Signature has been downloaded and parsed but not verified
        VERIFIED,          // Signature has been verified against the node's public key
        CONSENSUS_REACHED  // At least 1/3 of all nodes have been verified
    }

    public enum SignatureType {
        SHA_384_WITH_RSA
    }
}
