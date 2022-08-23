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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.domain.FileStreamSignature;
import com.hedera.mirror.importer.exception.SignatureVerificationException;
import com.hedera.mirror.importer.repository.NodeStakeRepository;

import lombok.CustomLog;
import javax.inject.Named;
import java.util.Collection;
import java.util.HashMap;

@CustomLog
@Named
public class ConsensusValidatorImpl implements ConsensusValidator {
    private final AddressBookService addressBookService;
    private final CommonDownloaderProperties commonDownloaderProperties;
    private final NodeStakeRepository nodeStakeRepository;

    public ConsensusValidatorImpl(AddressBookService addressBookService,
                                  CommonDownloaderProperties commonDownloaderProperties,
                                  NodeStakeRepository nodeStakeRepository) {
        this.addressBookService = addressBookService;
        this.commonDownloaderProperties = commonDownloaderProperties;
        this.nodeStakeRepository = nodeStakeRepository;
    }

    /**
     * Validates that the signature files satisfy the consensus requirement:
     * <ol>
     *  <li>If NodeStakes are within the NodeStakeRepository, at least 1/3 of the total node stake amount has been
     *  signature verified.</li>
     *  <li>If no NodeStakes are in the NodeStakeRepository, At least 1/3 signature files are present</li>
     * </ol>
     *
     * @param signatures a list of signature files which have the same filename
     * @throws SignatureVerificationException
     */
    @Override
    public void validate(Collection<FileStreamSignature> signatures) throws SignatureVerificationException {
        String filename = signatures.stream().map(FileStreamSignature::getFilename).findFirst().orElse("unknown");

        Multimap<String, FileStreamSignature> signatureHashMap = HashMultimap.create();
        for (var signature : signatures) {
            if (signature.getStatus() == FileStreamSignature.SignatureStatus.VERIFIED) {
                signatureHashMap.put(signature.getFileHashAsHex(), signature);
            }
        }

        if (commonDownloaderProperties.getConsensusRatio() == 0 && signatureHashMap.size() > 0) {
            log.debug("Signature file {} does not require consensus, skipping consensus check", filename);
            return;
        }

        var nodeStakes = nodeStakeRepository.findLatest();
        if (nodeStakes.isEmpty()) {
            signatureConsensus(signatures.size(), signatureHashMap, filename);
            return;
        }

        var nodeStakeMap = new HashMap<Long, Long>();
        long totalStake = 0L;
        for (var nodeStake : nodeStakes) {
            totalStake += nodeStake.getStake();
            nodeStakeMap.put(nodeStake.getNodeId(), nodeStake.getStake());
        }

        var staked = 0L;
        for (var key : signatureHashMap.keySet()) {
            var validatedSignatures = signatureHashMap.get(key);
            var nodeAccountId = validatedSignatures.iterator().next().getNodeAccountId().getId();
            var nodeStakeAmount = nodeStakeMap.getOrDefault(nodeAccountId, 0L);
            staked += nodeStakeAmount;
        }

        if (canReachConsensus(staked, totalStake)) {
            signatureHashMap.values()
                    .forEach(signature -> signature.setStatus(FileStreamSignature.SignatureStatus.CONSENSUS_REACHED));
            return;
        }

        throw new SignatureVerificationException(String.format("Consensus not reached for file %s", filename));
    }

    private void signatureConsensus(int signatureCount, Multimap<String, FileStreamSignature> signatureHashMap,
                                    String filename) throws SignatureVerificationException {
        var currentAddressBook = addressBookService.getCurrent();
        var nodeAccountIDPubKeyMap = currentAddressBook.getNodeAccountIDPubKeyMap();
        long nodeCount = nodeAccountIDPubKeyMap.size();
        if (!canReachConsensus(signatureCount, nodeCount)) {
            throw new SignatureVerificationException(String.format(
                    "Insufficient downloaded signature file count, requires at least %.03f to reach consensus, got %d" +
                            " out of %d for file %s",
                    commonDownloaderProperties.getConsensusRatio(),
                    signatureCount,
                    nodeCount,
                    filename));
        }

        if (!signatureHashMap.isEmpty() && canReachConsensus(signatureHashMap.values().size(), signatureCount)) {
            for (String key : signatureHashMap.keySet()) {
                Collection<FileStreamSignature> validatedSignatures = signatureHashMap.get(key);
                validatedSignatures.forEach(s -> s.setStatus(FileStreamSignature.SignatureStatus.CONSENSUS_REACHED));
            }

            return;
        }

        throw new SignatureVerificationException(String.format(
                "Insufficient signature file count, requires at least %.03f to reach consensus, got %d" +
                        " out of %d for file %s",
                commonDownloaderProperties.getConsensusRatio(), signatureHashMap.keySet().size(),
                signatureCount, filename));
    }

    private boolean canReachConsensus(long staked, long totalStaked) {
        return staked >= Math.ceil(totalStaked * commonDownloaderProperties.getConsensusRatio());
    }
}
