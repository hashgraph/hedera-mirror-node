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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import com.hedera.mirror.importer.util.Utility;

@Data
@ToString(exclude = {"fileHash", "fileHashSignature", "metadataHash", "metadataHashSignature"})
public class FileStreamSignature implements Comparable<FileStreamSignature> {

    private File file;
    private byte[] fileHash;
    private EntityId nodeAccountId;
    private SignatureType signatureType;
    private byte[] fileHashSignature;
    private SignatureStatus status = SignatureStatus.DOWNLOADED;
    private byte[] metadataHash;
    private byte[] metadataHashSignature;

    @Override
    public int compareTo(FileStreamSignature other) {
        return file.compareTo(other.getFile());
    }

    public String getFileHashAsHex() {
        return Utility.bytesToHex(fileHash);
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

    @Getter
    @RequiredArgsConstructor
    public enum SignatureType {
        SHA_384_WITH_RSA(1, 384, "SHA384withRSA", "SunRsaSign");

        private final int fileMarker;
        private final int maxLength;
        private final String algorithm;
        private final String provider;

        public static SignatureType fromSignatureTypeIndicator(int signatureTypeIndicator) {
            for (SignatureType signatureType : SignatureType.values()) {
                if (signatureType.fileMarker == signatureTypeIndicator) {
                    return signatureType;
                }
            }
            return null;
        }
    }
}
