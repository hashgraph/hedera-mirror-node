package com.hedera.mirror.importer.downloader;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import java.security.PublicKey;
import java.security.Signature;
import java.util.Collection;
import java.util.Map;
import javax.inject.Named;
import lombok.CustomLog;

import com.hedera.mirror.common.domain.addressbook.AddressBook;
import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.domain.FileStreamSignature;
import com.hedera.mirror.importer.domain.FileStreamSignature.SignatureStatus;
import com.hedera.mirror.importer.exception.SignatureVerificationException;

@Named
@CustomLog
public class NodeSignatureVerifier {

    private final AddressBookService addressBookService;
    private final ConsensusValidator consensusValidator;

    public NodeSignatureVerifier(AddressBookService addressBookService,
                                 ConsensusValidator consensusValidator) {
        this.addressBookService = addressBookService;
        this.consensusValidator = consensusValidator;
    }

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
    public void verify(Collection<FileStreamSignature> signatures) throws SignatureVerificationException {
        AddressBook currentAddressBook = addressBookService.getCurrent();
        Map<String, PublicKey> nodeAccountIDPubKeyMap = currentAddressBook.getNodeAccountIDPubKeyMap();

        for (FileStreamSignature fileStreamSignature : signatures) {
            if (verifySignature(fileStreamSignature, nodeAccountIDPubKeyMap)) {
                fileStreamSignature.setStatus(SignatureStatus.VERIFIED);
            }
        }

        consensusValidator.validate(signatures);
    }

    /**
     * check whether the given signature is valid
     *
     * @param fileStreamSignature    the data that was signed
     * @param nodeAccountIDPubKeyMap map of node account ids (as Strings) and their public keys
     * @return true if the signature is valid
     */
    private boolean verifySignature(FileStreamSignature fileStreamSignature,
                                    Map<String, PublicKey> nodeAccountIDPubKeyMap) {
        PublicKey publicKey = nodeAccountIDPubKeyMap.get(fileStreamSignature.getNodeAccountIdString());
        if (publicKey == null) {
            log.warn("Missing PublicKey for node {}", fileStreamSignature.getNodeAccountIdString());
            return false;
        }

        if (fileStreamSignature.getFileHashSignature() == null) {
            log.error("Missing signature data: {}", fileStreamSignature);
            return false;
        }

        try {
            log.trace("Verifying signature: {}", fileStreamSignature);

            Signature sig = Signature.getInstance(fileStreamSignature.getSignatureType().getAlgorithm(),
                    fileStreamSignature.getSignatureType().getProvider());
            sig.initVerify(publicKey);
            sig.update(fileStreamSignature.getFileHash());

            if (!sig.verify(fileStreamSignature.getFileHashSignature())) {
                return false;
            }

            if (fileStreamSignature.getMetadataHashSignature() != null) {
                sig.update(fileStreamSignature.getMetadataHash());
                return sig.verify(fileStreamSignature.getMetadataHashSignature());
            }

            return true;
        } catch (Exception e) {
            log.error("Failed to verify signature with public key {}: {}", publicKey, fileStreamSignature, e);
        }
        return false;
    }


}
