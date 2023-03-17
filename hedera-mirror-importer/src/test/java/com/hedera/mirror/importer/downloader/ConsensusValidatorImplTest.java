package com.hedera.mirror.importer.downloader;

/*
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

import static com.hedera.mirror.common.util.DomainUtils.TINYBARS_IN_ONE_HBAR;
import static com.hedera.mirror.importer.domain.StreamFileSignature.SignatureStatus.CONSENSUS_REACHED;
import static com.hedera.mirror.importer.domain.StreamFileSignature.SignatureStatus.DOWNLOADED;
import static com.hedera.mirror.importer.domain.StreamFileSignature.SignatureStatus.VERIFIED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.ConsensusNodeStub;
import com.hedera.mirror.importer.domain.StreamFileSignature;
import com.hedera.mirror.importer.domain.StreamFileSignature.SignatureType;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.exception.SignatureVerificationException;

class ConsensusValidatorImplTest {

    private static final BigDecimal MAX_TINYBARS = BigDecimal.valueOf(50_000_000_000L)
            .multiply(BigDecimal.valueOf(TINYBARS_IN_ONE_HBAR));

    private final DomainBuilder domainBuilder = new DomainBuilder();
    private CommonDownloaderProperties commonDownloaderProperties;
    private ConsensusValidatorImpl consensusValidator;
    private long nodeId = 0;

    @BeforeEach
    void setup() {
        commonDownloaderProperties = new CommonDownloaderProperties(new MirrorProperties());
        commonDownloaderProperties.setConsensusRatio(BigDecimal.ONE.divide(BigDecimal.valueOf(3), 19,
                RoundingMode.DOWN));
        consensusValidator = new ConsensusValidatorImpl(commonDownloaderProperties);
    }

    @Test
    void failureWithLargeStakes() {
        var oneThirdStake = MAX_TINYBARS.divide(BigDecimal.valueOf(3), 0, RoundingMode.CEILING);
        long nodeOneStake = oneThirdStake.subtract(BigDecimal.ONE).longValue();
        long nodeTwoStake = oneThirdStake.add(BigDecimal.ONE).longValue();
        long nodeThreeStake = oneThirdStake.subtract(BigDecimal.ONE).longValue();
        var signatures = signatures(nodeOneStake, nodeTwoStake, nodeThreeStake);
        signatures.remove(1);
        signatures.remove(1);

        assertConsensusNotReached(signatures);
    }

    @Test
    void successWithLargeStakes() {
        var oneThirdStake = MAX_TINYBARS.divide(BigDecimal.valueOf(3), 0, RoundingMode.CEILING).longValue();
        var signatures = signatures(oneThirdStake, oneThirdStake, oneThirdStake);

        consensusValidator.validate(signatures);
        assertThat(signatures)
                .hasSize(signatures.size())
                .map(StreamFileSignature::getStatus)
                .containsOnly(CONSENSUS_REACHED);
    }

    @Test
    void oneThirdStake() {
        var signatures = signatures(1, 1, 1);
        signatures.remove(0);
        signatures.remove(0);

        consensusValidator.validate(signatures);
        assertThat(signatures)
                .hasSize(signatures.size())
                .map(StreamFileSignature::getStatus)
                .containsOnly(CONSENSUS_REACHED);
    }

    @Test
    void oneThirdStakeWithZeroStake() {
        var signatures = signatures(0, 0, 0); // Fallback to node counts when 0

        consensusValidator.validate(signatures);
        assertThat(signatures)
                .hasSize(signatures.size())
                .map(StreamFileSignature::getStatus)
                .containsOnly(CONSENSUS_REACHED);
    }

    @ParameterizedTest
    @EnumSource(value = StreamFileSignature.SignatureStatus.class, names = {"DOWNLOADED", "CONSENSUS_REACHED",
            "NOT_FOUND"})
    void notVerified(StreamFileSignature.SignatureStatus status) {
        var signatures = signatures(3, 3, 3);
        signatures.forEach(s -> s.setStatus(status));
        assertThatThrownBy(() -> consensusValidator.validate(signatures))
                .isInstanceOf(SignatureVerificationException.class)
                .hasMessageContaining("Consensus not reached for file");
        assertThat(signatures)
                .map(StreamFileSignature::getStatus)
                .containsOnly(status);
    }

    @Test
    void fullNodeStakeConsensus() {
        commonDownloaderProperties.setConsensusRatio(BigDecimal.ONE);
        var oneThirdStake = MAX_TINYBARS.divide(BigDecimal.valueOf(3), 0, RoundingMode.CEILING).longValue();
        var signatures = signatures(oneThirdStake, oneThirdStake, oneThirdStake - 1);

        consensusValidator.validate(signatures);
        assertThat(signatures)
                .hasSize(signatures.size())
                .map(StreamFileSignature::getStatus)
                .containsOnly(CONSENSUS_REACHED);
    }

    @Test
    void noSignatures() {
        List<StreamFileSignature> emptyList = Collections.emptyList();
        assertConsensusNotReached(emptyList);
    }

    @Test
    void skipConsensus() {
        commonDownloaderProperties.setConsensusRatio(BigDecimal.ZERO);
        var signatures = signatures(1, 7);

        consensusValidator.validate(signatures);
        assertThat(signatures)
                .map(StreamFileSignature::getStatus)
                .doesNotContain(CONSENSUS_REACHED);
    }

    @Test
    void multipleFileHashes() {
        var signatures = signatures(0, 0, 0, 0, 0);
        signatures.get(2).setFileHash(domainBuilder.bytes(256));
        signatures.get(3).setFileHash(domainBuilder.bytes(256));
        signatures.get(4).setFileHash(domainBuilder.bytes(256));

        consensusValidator.validate(signatures);
        assertThat(signatures)
                .map(StreamFileSignature::getStatus)
                .containsExactly(CONSENSUS_REACHED, CONSENSUS_REACHED, VERIFIED, VERIFIED, VERIFIED);
    }

    @Test
    void fullConsensusRatio() {
        commonDownloaderProperties.setConsensusRatio(BigDecimal.ONE);
        var signatures = signatures(0, 0, 0);
        consensusValidator.validate(signatures);
        assertThat(signatures)
                .map(StreamFileSignature::getStatus)
                .containsOnly(CONSENSUS_REACHED);
    }

    @Test
    void failureSignatureConsensus() {
        // Zero node stakes occurs when falling back to counting signatures
        commonDownloaderProperties.setConsensusRatio(BigDecimal.ONE);
        var signatures = signatures(0, 0, 0, 0);
        signatures.get(1).setStatus(DOWNLOADED);
        signatures.get(2).setStatus(DOWNLOADED);
        signatures.get(3).setStatus(DOWNLOADED);
        assertConsensusNotReached(signatures);
    }

    private void assertConsensusNotReached(List<StreamFileSignature> signatures) {
        assertThatThrownBy(() -> consensusValidator.validate(signatures))
                .isInstanceOf(SignatureVerificationException.class)
                .hasMessageContaining("Consensus not reached for file");
        assertThat(signatures)
                .map(StreamFileSignature::getStatus)
                .doesNotContain(CONSENSUS_REACHED);
    }

    private List<StreamFileSignature> signatures(long... stakes) {
        List<StreamFileSignature> signatures = new ArrayList<>();
        long totalStake = Arrays.stream(stakes).sum();
        if (totalStake == 0) {
            stakes = Arrays.stream(stakes).map(s -> 1L).toArray();
            totalStake = stakes.length;
        }
        var fileHash = domainBuilder.bytes(256);

        for (long stake : stakes) {
            var node = ConsensusNodeStub.builder()
                    .nodeId(nodeId++)
                    .stake(stake)
                    .totalStake(totalStake)
                    .build();

            var signature = new StreamFileSignature();
            signature.setFileHash(fileHash);
            signature.setFilename(StreamFilename.EPOCH);
            signature.setNode(node);
            signature.setSignatureType(SignatureType.SHA_384_WITH_RSA);
            signature.setStatus(StreamFileSignature.SignatureStatus.VERIFIED);
            signature.setStreamType(StreamType.RECORD);
            signatures.add(signature);
        }

        return signatures;
    }
}
