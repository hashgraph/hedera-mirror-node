package com.hedera.mirror.importer.downloader;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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
import java.security.PublicKey;
import java.security.Signature;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;

import com.hedera.mirror.importer.addressbook.NetworkAddressBook;
import com.hedera.mirror.importer.domain.NodeAddress;
import com.hedera.mirror.importer.domain.SignatureStream;
import com.hedera.mirror.importer.exception.SignatureVerificationException;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
public class NodeSignatureVerifier {

    private final Map<String, PublicKey> nodeIDPubKeyMap;

    public NodeSignatureVerifier(NetworkAddressBook networkAddressBook) {
        nodeIDPubKeyMap = networkAddressBook
                .load()
                .stream()
                .collect(Collectors.toMap(NodeAddress::getId, NodeAddress::getPublicKeyAsObject));
    }

    private static boolean greaterThanSuperMajorityNum(long n, long N) {
        return n > N * 2 / 3.0;
    }

    /**
     * Verifies that the signature files are signed by corresponding node's PublicKey. For valid signature files, we
     * compare their hashes to see if more than 2/3 Hashes match. If a signature is valid, we put the hash in its
     * content and its File to the map, to see if more than 2/3 valid signatures have the same hash.
     *
     * @param signatures a list of a sig files which have the same timestamp
     */
    public void verify(Collection<SignatureStream> signatures) {
        Multimap<String, SignatureStream> signatureHashMap = HashMultimap.create();
        String filename = signatures.stream().map(s -> s.getFile().getName()).findFirst().orElse("empty");

        if (!greaterThanSuperMajorityNum(signatures.size(), nodeIDPubKeyMap.size())) {
            Set<String> seenNodes = signatures.stream().map(SignatureStream::getNode).collect(Collectors.toSet());
            Set<String> missingNodes = new TreeSet<>(Sets.difference(nodeIDPubKeyMap.keySet(), seenNodes));
            String message = String.format("Signature verification failed for %s with %s of %s nodes missing: %s",
                    filename, missingNodes.size(), nodeIDPubKeyMap.size(), missingNodes);
            throw new SignatureVerificationException(message);
        }

        for (SignatureStream signatureStream : signatures) {
            Pair<byte[], byte[]> hashAndSig = Utility.extractHashAndSigFromFile(signatureStream.getFile());
            if (hashAndSig == null) {
                continue;
            }

            signatureStream.setHash(hashAndSig.getLeft());
            signatureStream.setSignature(hashAndSig.getRight());

            if (verifySignature(signatureStream)) {
                signatureHashMap.put(signatureStream.getHashAsHex(), signatureStream);
            }
        }

        for (String key : signatureHashMap.keySet()) {
            Collection<SignatureStream> validatedSignatures = signatureHashMap.get(key);
            if (greaterThanSuperMajorityNum(validatedSignatures.size(), nodeIDPubKeyMap.size())) {
                validatedSignatures.stream().forEach(s -> s.setValid(true));
                log.debug("Verified signature file matches more than 2/3 of nodes: {}", filename);
                return;
            }
        }

        Collection<String> invalidNodes = signatures.stream()
                .filter(s -> !s.isValid())
                .map(SignatureStream::getNode)
                .sorted()
                .collect(Collectors.toList());
        String message = String.format("Signature verification failed for %s with %s of %s nodes not passing: %s",
                filename, invalidNodes.size(), nodeIDPubKeyMap.size(), invalidNodes);
        throw new SignatureVerificationException(message);
    }

    /**
     * check whether the given signature is valid
     *
     * @param signatureStream the data that was signed
     * @return true if the signature is valid
     */
    private boolean verifySignature(SignatureStream signatureStream) {
        PublicKey publicKey = nodeIDPubKeyMap.get(signatureStream.getNode());
        if (publicKey == null) {
            log.warn("Missing PublicKey for node {}", signatureStream.getNode());
            return false;
        }

        if (signatureStream.getSignature() == null) {
            log.error("Missing signature data: {}", signatureStream);
            return false;
        }

        try {
            log.trace("Verifying signature: {}", signatureStream);
            Signature sig = Signature.getInstance("SHA384withRSA", "SunRsaSign");
            sig.initVerify(publicKey);
            sig.update(signatureStream.getHash());
            return sig.verify(signatureStream.getSignature());
        } catch (Exception e) {
            log.error("Failed to verify signature with public key {}: {}", publicKey, signatureStream, e);
        }
        return false;
    }
}
