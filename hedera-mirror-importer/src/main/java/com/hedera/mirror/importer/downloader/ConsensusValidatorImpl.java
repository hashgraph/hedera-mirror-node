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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import javax.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.domain.FileStreamSignature;
import com.hedera.mirror.importer.exception.SignatureVerificationException;
import com.hedera.mirror.importer.repository.NodeStakeRepository;

@CustomLog
@Named
@RequiredArgsConstructor
public class ConsensusValidatorImpl implements ConsensusValidator {
    private final AddressBookService addressBookService;
    private final CommonDownloaderProperties commonDownloaderProperties;
    private final NodeStakeRepository nodeStakeRepository;

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
        Multimap<String, FileStreamSignature> signatureHashMap = HashMultimap.create();
        for (var signature : signatures) {
            if (signature.getStatus() == FileStreamSignature.SignatureStatus.VERIFIED) {
                signatureHashMap.put(signature.getFileHashAsHex(), signature);
            }
        }

        var filename = signatures.stream().map(FileStreamSignature::getFilename).findFirst().orElse("unknown");
        if (BigDecimal.ZERO.equals(commonDownloaderProperties.getConsensusRatio()) && signatureHashMap.size() > 0) {
            log.debug("Signature file {} does not require consensus, skipping consensus check", filename);
            return;
        }

        long totalStake = 0;
        var nodeAccountIdToStakeMap = new HashMap<EntityId, Long>();
        var addressBook = addressBookService.getCurrent();
        var nodeStakes = nodeStakeRepository.findLatest();
        if (nodeStakes.isEmpty()) {
            var nodeAccountIDPubKeyMap = addressBook.getNodeAccountIDPubKeyMap();
            totalStake = nodeAccountIDPubKeyMap.size();
        } else {
            var nodeIdToNodeAccountIdMap = addressBook.getNodeIdNodeAccountIdMap();
            for (var nodeStake : nodeStakes) {
                totalStake += nodeStake.getStake();
                var nodeAccountId = nodeIdToNodeAccountIdMap.get(nodeStake.getNodeId());
                if (nodeAccountId == null) {
                    log.warn("Node Stake found for Node Id {} but no Node Account Id found", nodeStake.getNodeId());
                    continue;
                }

                nodeAccountIdToStakeMap.put(nodeAccountId, nodeStake.getStake());
            }
        }

        long consensusCount = 0;
        for (String key : signatureHashMap.keySet()) {
            var validatedSignatures = signatureHashMap.get(key);
            long stake = 0L;
            for (var signature : validatedSignatures) {
                // If the map has no entry for the node account id, a default value of 1 is used to count a signature.
                stake += nodeAccountIdToStakeMap.getOrDefault(signature.getNodeAccountId(), 1L);
            }

            if (canReachConsensus(stake, totalStake)) {
                consensusCount += validatedSignatures.size();
                validatedSignatures.forEach(s -> s.setStatus(FileStreamSignature.SignatureStatus.CONSENSUS_REACHED));
            }
        }

        if (consensusCount > 0) {
            return;
        }

        throw new SignatureVerificationException(String.format("Consensus not reached for file %s", filename));
    }

    private boolean canReachConsensus(long stake, long totalStake) {
        log.info("Stake: {}, Total Stake: {}", stake, totalStake);
        log.info("Consensus Ratio: {}", commonDownloaderProperties.getConsensusRatio());

        var stakeRequiredForConsensus = BigDecimal.valueOf(totalStake)
                .multiply(commonDownloaderProperties.getConsensusRatio())
                .setScale(0, RoundingMode.CEILING);

        log.info("Stake Required For Consensus: {}", stakeRequiredForConsensus);
        log.info("Result: {}", BigDecimal.valueOf(stake).compareTo(stakeRequiredForConsensus) >= 0);

        return BigDecimal.valueOf(stake).compareTo(stakeRequiredForConsensus) >= 0;
    }
}
