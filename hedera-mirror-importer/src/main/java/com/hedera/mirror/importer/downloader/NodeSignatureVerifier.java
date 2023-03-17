package com.hedera.mirror.importer.downloader;

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

import java.security.Signature;
import java.util.Collection;
import javax.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.importer.domain.StreamFileSignature;
import com.hedera.mirror.importer.domain.StreamFileSignature.SignatureStatus;
import com.hedera.mirror.importer.exception.SignatureVerificationException;

@Named
@CustomLog
@RequiredArgsConstructor
public class NodeSignatureVerifier {

    private final ConsensusValidator consensusValidator;

    /**
     * Verifies that the signature files satisfy the consensus requirement:
     * <ol>
     *  <li>If NodeStakes are within the NodeStakeRepository, at least 1/3 of the total node stake amount has been
     *  signature verified.</li>
     *  <li>If no NodeStakes are in the NodeStakeRepository, At least 1/3 signature files are present</li>
     *  <li>For a signature file, we validate it by checking if it's signed by corresponding node's PublicKey. For valid
     *      signature files, we compare their hashes to see if at least 1/3 have hashes that match. If a signature is
     *      valid, we put the hash in its content and its file to the map, to see if at least 1/3 valid signatures have
     *      the same hash</li>
     * </ol>
     *
     * @param signatures a list of signature files which have the same filename
     * @throws SignatureVerificationException
     */
    public void verify(Collection<StreamFileSignature> signatures) throws SignatureVerificationException {

        for (StreamFileSignature streamFileSignature : signatures) {
            if (verifySignature(streamFileSignature)) {
                streamFileSignature.setStatus(SignatureStatus.VERIFIED);
            }
        }

        consensusValidator.validate(signatures);
    }

    /**
     * check whether the given signature is valid
     *
     * @param streamFileSignature the data that was signed
     * @return true if the signature is valid
     */
    private boolean verifySignature(StreamFileSignature streamFileSignature) {
        var publicKey = streamFileSignature.getNode().getPublicKey();

        if (publicKey == null) {
            log.warn("Missing PublicKey for node {}", streamFileSignature.getNode());
            return false;
        }

        if (streamFileSignature.getFileHashSignature() == null) {
            log.error("Missing signature data: {}", streamFileSignature);
            return false;
        }

        try {
            log.trace("Verifying signature: {}", streamFileSignature);

            Signature sig = Signature.getInstance(streamFileSignature.getSignatureType().getAlgorithm(),
                    streamFileSignature.getSignatureType().getProvider());
            sig.initVerify(publicKey);
            sig.update(streamFileSignature.getFileHash());

            if (!sig.verify(streamFileSignature.getFileHashSignature())) {
                return false;
            }

            if (streamFileSignature.getMetadataHashSignature() != null) {
                sig.update(streamFileSignature.getMetadataHash());
                return sig.verify(streamFileSignature.getMetadataHashSignature());
            }

            return true;
        } catch (Exception e) {
            log.error("Failed to verify signature with public key {}: {}", publicKey, streamFileSignature, e);
        }
        return false;
    }
}
