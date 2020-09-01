package com.hedera.mirror.importer.downloader;

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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import java.io.File;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;

import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.AddressBookEntry;
import com.hedera.mirror.importer.domain.FileStreamSignature;
import com.hedera.mirror.importer.domain.FileStreamSignature.SignatureStatus;
import com.hedera.mirror.importer.exception.SignatureVerificationException;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
public class NodeSignatureVerifier {

    private final Map<String, PublicKey> nodeAccountIDPubKeyMap;

    public NodeSignatureVerifier(AddressBook currentAddressBook) {
        nodeAccountIDPubKeyMap = currentAddressBook
                .getEntries()
                .stream()
                .collect(Collectors
                        .toMap(AddressBookEntry::getNodeAccountIdString, AddressBookEntry::getPublicKeyAsObject));
    }

    private static boolean canReachConsensus(long actualNodes, long expectedNodes) {
        return actualNodes >= Math.ceil(expectedNodes / 3.0);
    }

    /**
     * Verifies that the signature files satisfy the consensus requirement:
     * <ol>
     *  <li>At least 1/3 signature files are present</li>
     *  <li>For a signature file, we validate it by checking if it's signed by corresponding node's PublicKey. For valid
     *      signature files, we compare their hashes to see if at least 1/3 have hashes that match. If a signature is
     *      valid, we put the hash in its content and its file to the map, to see if at lest 1/3 valid signatures have
     *      the same hash</li>
     * </ol>
     *
     * @param signatures a list of signature files which have the same filename
     * @throws SignatureVerificationException
     */
    public void verify(Collection<FileStreamSignature> signatures) throws SignatureVerificationException {
        Multimap<String, FileStreamSignature> signatureHashMap = HashMultimap.create();
        String filename = signatures.stream().map(FileStreamSignature::getFile).map(File::getName).findFirst()
                .orElse(null);
        int consensusCount = 0;

        long sigFileCount = signatures.size();
        long nodeCount = nodeAccountIDPubKeyMap.size();
        if (!canReachConsensus(sigFileCount, nodeCount)) {
            throw new SignatureVerificationException("Require at least 1/3 signature files to reach consensus, got " +
                    sigFileCount + " out of " + nodeCount + " for file " + filename + ": " + statusMap(signatures));
        }

        for (FileStreamSignature fileStreamSignature : signatures) {
            Pair<byte[], byte[]> hashAndSig = Utility.extractHashAndSigFromFile(fileStreamSignature.getFile());
            if (hashAndSig == null) {
                continue;
            }

            fileStreamSignature.setHash(hashAndSig.getLeft());
            fileStreamSignature.setSignature(hashAndSig.getRight());
            fileStreamSignature.setStatus(SignatureStatus.PARSED);

            if (verifySignature(fileStreamSignature)) {
                fileStreamSignature.setStatus(SignatureStatus.VERIFIED);
                signatureHashMap.put(fileStreamSignature.getHashAsHex(), fileStreamSignature);
            }
        }

        for (String key : signatureHashMap.keySet()) {
            Collection<FileStreamSignature> validatedSignatures = signatureHashMap.get(key);

            if (canReachConsensus(validatedSignatures.size(), nodeCount)) {
                consensusCount += validatedSignatures.size();
                validatedSignatures.forEach(s -> s.setStatus(SignatureStatus.CONSENSUS_REACHED));
            }
        }

        if (consensusCount == nodeCount) {
            log.debug("Verified signature file {} reached consensus", filename);
            return;
        } else if (consensusCount > 0) {
            log.warn("Verified signature file {} reached consensus but with some errors: {}", filename,
                    statusMap(signatures));
            return;
        }

        throw new SignatureVerificationException("Signature verification failed for file " + filename + ": " + statusMap(signatures));
    }

    /**
     * check whether the given signature is valid
     *
     * @param fileStreamSignature the data that was signed
     * @return true if the signature is valid
     */
    private boolean verifySignature(FileStreamSignature fileStreamSignature) {
        PublicKey publicKey = nodeAccountIDPubKeyMap.get(fileStreamSignature.getNodeAccountIdString());
        if (publicKey == null) {
            log.warn("Missing PublicKey for node {}", fileStreamSignature.getNodeAccountIdString());
            return false;
        }

        if (fileStreamSignature.getSignature() == null) {
            log.error("Missing signature data: {}", fileStreamSignature);
            return false;
        }

        try {
            log.trace("Verifying signature: {}", fileStreamSignature);
            Signature sig = Signature.getInstance("SHA384withRSA", "SunRsaSign");
            sig.initVerify(publicKey);
            sig.update(fileStreamSignature.getHash());
            return sig.verify(fileStreamSignature.getSignature());
        } catch (Exception e) {
            log.error("Failed to verify signature with public key {}: {}", publicKey, fileStreamSignature, e);
        }
        return false;
    }

    private Map<String, Collection<String>> statusMap(Collection<FileStreamSignature> signatures) {
        Map<String, Collection<String>> statusMap = signatures.stream()
                .collect(Collectors.groupingBy(fss -> fss.getStatus().toString(),
                        Collectors.mapping(FileStreamSignature::getNodeAccountIdString, Collectors.toCollection(TreeSet::new))));
        Set<String> seenNodes = signatures.stream().map(FileStreamSignature::getNodeAccountIdString).collect(Collectors.toSet());
        Set<String> missingNodes = new TreeSet<>(Sets.difference(nodeAccountIDPubKeyMap.keySet(), seenNodes));
        statusMap.put("MISSING", missingNodes);
        statusMap.remove(SignatureStatus.CONSENSUS_REACHED.toString());
        return statusMap;
    }
}
