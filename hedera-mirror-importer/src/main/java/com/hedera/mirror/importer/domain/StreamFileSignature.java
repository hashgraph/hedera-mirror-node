package com.hedera.mirror.importer.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import java.util.Comparator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.reader.signature.ProtoSignatureFileReader;

@AllArgsConstructor
@Builder
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@ToString(exclude = {"bytes", "fileHash", "fileHashSignature", "metadataHash", "metadataHashSignature"})
public class StreamFileSignature implements Comparable<StreamFileSignature> {

    private static final String COMPRESSED_EXTENSION = ".gz";
    private static final Comparator<StreamFileSignature> COMPARATOR = Comparator
            .comparing(StreamFileSignature::getNode)
            .thenComparing(StreamFileSignature::getFilename);

    private byte[] bytes;
    private byte[] fileHash;
    private byte[] fileHashSignature;
    @EqualsAndHashCode.Include
    private StreamFilename filename;
    private byte[] metadataHash;
    private byte[] metadataHashSignature;
    @EqualsAndHashCode.Include
    private ConsensusNode node;
    private SignatureType signatureType;

    @Builder.Default
    private SignatureStatus status = SignatureStatus.DOWNLOADED;
    private StreamType streamType;
    private byte version;

    @Override
    public int compareTo(StreamFileSignature other) {
        return COMPARATOR.compare(this, other);
    }

    public StreamFilename getDataFilename() {
        String dataFilename = filename.getFilename().replace(StreamType.SIGNATURE_SUFFIX, "");

        if (hasCompressedDataFile() && !dataFilename.endsWith(COMPRESSED_EXTENSION)) {
            dataFilename += COMPRESSED_EXTENSION;
        }

        return new StreamFilename(dataFilename);
    }

    public String getFileHashAsHex() {
        return DomainUtils.bytesToHex(fileHash);
    }

    public String getMetadataHashAsHex() {
        return DomainUtils.bytesToHex(metadataHash);
    }

    private boolean hasCompressedDataFile() {
        return version >= ProtoSignatureFileReader.VERSION || filename.isCompressed();
    }

    public enum SignatureStatus {
        DOWNLOADED,         // Signature has been downloaded and parsed but not verified
        VERIFIED,           // Signature has been verified against the node's public key
        CONSENSUS_REACHED,  // Signature verification consensus reached by a node count greater than the consensusRatio
        NOT_FOUND,          // Signature for given node was not found for download
    }

    @Getter
    @RequiredArgsConstructor
    public enum SignatureType {
        SHA_384_WITH_RSA(1, 384, "SHA384withRSA", "SunRsaSign");

        private final int fileMarker;
        private final int maxLength;
        private final String algorithm;
        private final String provider;

        public static SignatureType of(int signatureTypeIndicator) {
            for (SignatureType signatureType : SignatureType.values()) {
                if (signatureType.fileMarker == signatureTypeIndicator) {
                    return signatureType;
                }
            }
            return null;
        }
    }
}
