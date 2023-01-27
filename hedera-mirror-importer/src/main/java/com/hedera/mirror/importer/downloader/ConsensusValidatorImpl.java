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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import javax.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.importer.domain.StreamFileSignature;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.exception.SignatureVerificationException;

@CustomLog
@Named
@RequiredArgsConstructor
public class ConsensusValidatorImpl implements ConsensusValidator {

    private final CommonDownloaderProperties commonDownloaderProperties;

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
    public void validate(Collection<StreamFileSignature> signatures) throws SignatureVerificationException {
        Multimap<String, StreamFileSignature> signatureHashMap = HashMultimap.create();
        StreamFilename filename = null;
        BigDecimal stakeRequiredForConsensus = null;
        long totalStake = 0L;

        for (var signature : signatures) {
            if (filename == null) {
                filename = signature.getFilename();
                totalStake = signature.getNode().getTotalStake();
                stakeRequiredForConsensus = getStakeRequiredForConsensus(totalStake);
            }

            if (signature.getStatus() == StreamFileSignature.SignatureStatus.VERIFIED) {
                signatureHashMap.put(signature.getFileHashAsHex(), signature);
            }
        }

        if (BigDecimal.ZERO.equals(commonDownloaderProperties.getConsensusRatio()) && signatureHashMap.size() > 0) {
            log.debug("Signature file {} does not require consensus, skipping consensus check", filename);
            return;
        }

        long debugStake = 0;
        long consensusCount = 0;

        for (String key : signatureHashMap.keySet()) {
            var validatedSignatures = signatureHashMap.get(key);
            long stake = 0L;

            for (var signature : validatedSignatures) {
                stake += signature.getNode().getStake();
            }

            if (canReachConsensus(stake, stakeRequiredForConsensus)) {
                consensusCount += validatedSignatures.size();
                validatedSignatures.forEach(s -> s.setStatus(StreamFileSignature.SignatureStatus.CONSENSUS_REACHED));
            }

            if (debugStake < stake) {
                debugStake = stake;
            }
        }

        if (consensusCount > 0) {
            return;
        }

        throw new SignatureVerificationException(String.format("Consensus not reached for file %s with %d/%d stake",
                filename, debugStake, totalStake));
    }

    private boolean canReachConsensus(long stake, BigDecimal stakeRequiredForConsensus) {
        return BigDecimal.valueOf(stake).compareTo(stakeRequiredForConsensus) >= 0;
    }

    private BigDecimal getStakeRequiredForConsensus(long totalStake) {
        if (totalStake == 0) {
            throw new SignatureVerificationException("Invalid total staking weight. Consensus not " +
                    "reached");
        }

        return BigDecimal.valueOf(totalStake)
                .multiply(commonDownloaderProperties.getConsensusRatio())
                .setScale(0, RoundingMode.CEILING);
    }
}
