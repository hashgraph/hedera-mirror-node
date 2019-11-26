package com.hedera.mirror.downloader;

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

import java.io.File;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.tuple.Pair;

import com.hedera.mirror.addressbook.NetworkAddressBook;
import com.hedera.mirror.domain.NodeAddress;
import com.hedera.mirror.util.Utility;

@Log4j2
public class NodeSignatureVerifier {

    private final Map<String, PublicKey> nodeIDPubKeyMap;

    public NodeSignatureVerifier(NetworkAddressBook networkAddressBook) {
        nodeIDPubKeyMap = networkAddressBook
                .load()
                .stream()
                .collect(Collectors.toMap(NodeAddress::getId, NodeAddress::getPublicKeyAsObject));
    }

    /**
     * Verifies that the signature files are signed by corresponding node's PublicKey. For valid signature files, we
     * compare their Hashes to see if more than 2/3 Hashes match. If more than 2/3 Hashes match, we return a List of
     * Files which contains this Hash.
     *
     * @param sigFiles a list of a sig files which have the same timestamp
     * @return Pair of <hash of valid data file, list of valid sig files>. Valid means the signature is valid and the
     * Hash is agreed by super-majority nodes. If validity of signatures can not be established, then returns <null,
     * empty list>.
     */
    public Pair<byte[], List<File>> verifySignatureFiles(List<File> sigFiles) {
        // If a signature is valid, we put the Hash in its content and its File to the map, to see if more than 2/3
        // valid signatures have the same Hash
        Map<String, Set<File>> hashToSigFiles = new HashMap<>();
        for (File sigFile : sigFiles) {
            Pair<byte[], byte[]> hashAndSig = Utility.extractHashAndSigFromFile(sigFile);
            if (hashAndSig == null) {
                continue;
            }
            String nodeAccountID = Utility.getAccountIDStringFromFilePath(sigFile.getPath());
            if (verifySignature(hashAndSig.getLeft(), hashAndSig.getRight(), nodeAccountID, sigFile.getPath())) {
                String hashString = Hex.encodeHexString(hashAndSig.getLeft());
                hashToSigFiles
                        .putIfAbsent(hashString, new HashSet<>());  // only one key present in common case, no
                // efficiency issues.
                hashToSigFiles.get(hashString).add(sigFile);
            } else {
                log.error("Invalid signature in file {}", sigFile.getPath());
            }
        }

        for (String key : hashToSigFiles.keySet()) {
            if (Utility.greaterThanSuperMajorityNum(hashToSigFiles.get(key).size(),
                    nodeIDPubKeyMap.size())) {
                byte[] hash = null;
                try {
                    hash = Hex.decodeHex(key);
                } catch (DecoderException e) {
                    log.error("Error decoding hex string {}", key);
                }
                return Pair.of(hash, new ArrayList<>(hashToSigFiles.get(key)));
            }
        }
        return Pair.of(null, new ArrayList<>());
    }

    /**
     * check whether the given signature is valid
     *
     * @param data          the data that was signed
     * @param signature     the claimed signature of that data
     * @param nodeAccountID the node's accountID string
     * @return true if the signature is valid
     */
    private boolean verifySignature(byte[] data, byte[] signature,
                                    String nodeAccountID, String filePath) {
        PublicKey publicKey = nodeIDPubKeyMap.get(nodeAccountID);
        if (publicKey == null) {
            log.warn("Missing PublicKey for node {}", nodeAccountID);
            return false;
        }

        if (signature == null) {
            log.error("Missing signature for file {}", filePath);
            return false;
        }

        try {
            log.trace("Verifying signature of file {} with public key of node {}", filePath, nodeAccountID);
            Signature sig = Signature.getInstance("SHA384withRSA", "SunRsaSign");
            sig.initVerify(publicKey);
            sig.update(data);
            return sig.verify(signature);
        } catch (Exception e) {
            log.error("Failed to verify Signature: {}, PublicKey: {}, NodeID: {}, File: {}", signature, publicKey, nodeAccountID, filePath, e);
        }
        return false;
    }
}
