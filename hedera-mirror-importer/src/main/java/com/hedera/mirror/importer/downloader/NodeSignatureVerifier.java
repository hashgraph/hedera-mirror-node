package com.hedera.mirror.importer.downloader;

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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.springframework.util.CollectionUtils;

import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.FileStreamSignature;
import com.hedera.mirror.importer.domain.FileStreamSignature.SignatureStatus;
import com.hedera.mirror.importer.exception.SignatureVerificationException;

@Named
@Log4j2
public class NodeSignatureVerifier {

    private final AddressBookService addressBookService;
    private final CommonDownloaderProperties commonDownloaderProperties;

    // Metrics
    private final MeterRegistry meterRegistry;
    private final Map<EntityId, Counter> nodeSignatureStatusMetricMap = new ConcurrentHashMap<>();

    public NodeSignatureVerifier(AddressBookService addressBookService,
                                 CommonDownloaderProperties commonDownloaderProperties,
                                 MeterRegistry meterRegistry) {
        this.addressBookService = addressBookService;
        this.commonDownloaderProperties = commonDownloaderProperties;
        this.meterRegistry = meterRegistry;
    }

    private boolean canReachConsensus(long actualNodes, long expectedNodes) {
        return actualNodes >= Math.ceil(expectedNodes * commonDownloaderProperties.getConsensusRatio());
    }

    /**
     * Verifies that the signature files satisfy the consensus requirement:
     * <ol>
     *  <li>At least 1/3 signature files are present</li>
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

        Multimap<String, FileStreamSignature> signatureHashMap = HashMultimap.create();
        String filename = signatures.stream().map(FileStreamSignature::getFilename).findFirst().orElse("unknown");
        int consensusCount = 0;

        long sigFileCount = signatures.size();
        long nodeCount = nodeAccountIDPubKeyMap.size();
        if (!canReachConsensus(sigFileCount, nodeCount)) {
            throw new SignatureVerificationException(String.format(
                    "Insufficient downloaded signature file count, requires at least %.03f to reach consensus, got %d" +
                            " out of %d for file %s: %s",
                    commonDownloaderProperties.getConsensusRatio(),
                    sigFileCount,
                    nodeCount,
                    filename,
                    statusMap(signatures, nodeAccountIDPubKeyMap)));
        }

        for (FileStreamSignature fileStreamSignature : signatures) {
            if (verifySignature(fileStreamSignature, nodeAccountIDPubKeyMap)) {
                fileStreamSignature.setStatus(SignatureStatus.VERIFIED);
                signatureHashMap.put(fileStreamSignature.getFileHashAsHex(), fileStreamSignature);
            }
        }

        if (commonDownloaderProperties.getConsensusRatio() == 0 && signatureHashMap.size() > 0) {
            log.debug("Signature file {} does not require consensus, skipping consensus check", filename);
            return;
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
                    statusMap(signatures, nodeAccountIDPubKeyMap));
            return;
        }

        throw new SignatureVerificationException("Signature verification failed for file " + filename + ": " + statusMap(signatures, nodeAccountIDPubKeyMap));
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

    private Map<String, Collection<EntityId>> statusMap(Collection<FileStreamSignature> signatures, Map<String,
            PublicKey> nodeAccountIDPubKeyMap) {
        Map<String, Collection<EntityId>> statusMap = signatures.stream()
                .collect(Collectors.groupingBy(fss -> fss.getStatus().toString(),
                        Collectors.mapping(FileStreamSignature::getNodeAccountId, Collectors
                                .toCollection(TreeSet::new))));

        Set<EntityId> seenNodes = new HashSet<>();
        signatures.forEach(signature -> seenNodes.add(signature.getNodeAccountId()));

        Set<EntityId> missingNodes = new TreeSet<>(Sets.difference(
                nodeAccountIDPubKeyMap.keySet().stream().map(x -> EntityId.of(x, EntityTypeEnum.ACCOUNT))
                        .collect(Collectors.toSet()),
                seenNodes));
        statusMap.put(SignatureStatus.NOT_FOUND.toString(), missingNodes);

        String streamType = CollectionUtils.isEmpty(signatures) ? "unknown" :
                signatures.stream().map(FileStreamSignature::getStreamType).findFirst().toString();
        for (Map.Entry<String, Collection<EntityId>> entry : statusMap.entrySet()) {
            entry.getValue().forEach(nodeAccountId -> {
                Counter counter = nodeSignatureStatusMetricMap.computeIfAbsent(
                        nodeAccountId,
                        n -> newStatusMetric(nodeAccountId, streamType, entry.getKey()));
                counter.increment();
            });
        }

        return statusMap;
    }

    private Counter newStatusMetric(EntityId entityId, String streamType, String status) {
        return Counter.builder("hedera.mirror.download.signature.verification")
                .description("The number of signatures verified from a particular node")
                .tag("nodeAccount", entityId.getEntityNum().toString())
                .tag("realm", entityId.getRealmNum().toString())
                .tag("shard", entityId.getShardNum().toString())
                .tag("type", streamType)
                .tag("status", status)
                .register(meterRegistry);
    }
}
