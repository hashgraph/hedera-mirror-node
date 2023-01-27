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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.importer.domain.StreamFileSignature.SignatureStatus.DOWNLOADED;
import static com.hedera.mirror.importer.domain.StreamFileSignature.SignatureStatus.VERIFIED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isA;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.ConsensusNodeStub;
import com.hedera.mirror.importer.domain.StreamFileSignature;
import com.hedera.mirror.importer.domain.StreamFileSignature.SignatureType;
import com.hedera.mirror.importer.domain.StreamFilename;

@ExtendWith(MockitoExtension.class)
class NodeSignatureVerifierTest {

    private static PrivateKey privateKey;
    private static PublicKey publicKey;
    private Signature signer;

    private CommonDownloaderProperties commonDownloaderProperties;

    @Mock
    private ConsensusValidator consensusValidator;

    @InjectMocks
    private NodeSignatureVerifier nodeSignatureVerifier;

    @BeforeAll
    @SneakyThrows
    static void generateKeys() {
        KeyPair nodeKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        privateKey = nodeKeyPair.getPrivate();
        publicKey = nodeKeyPair.getPublic();
    }

    @BeforeEach
    @SneakyThrows
    void setup() {
        commonDownloaderProperties = new CommonDownloaderProperties(new MirrorProperties());
        commonDownloaderProperties.setConsensusRatio(BigDecimal.ONE.divide(BigDecimal.valueOf(3), 19,
                RoundingMode.DOWN));
        nodeSignatureVerifier = new NodeSignatureVerifier(consensusValidator);
        signer = Signature.getInstance("SHA384withRSA", "SunRsaSign");
        signer.initSign(privateKey);
        consensusValidator.validate(isA(List.class));
    }

    @Test
    void v2() {
        var signature = streamFileSignature();
        signature.setMetadataHash(null);
        signature.setMetadataHashSignature(null);
        var signatures = List.of(signature);

        nodeSignatureVerifier.verify(signatures);

        assertThat(signatures).isNotEmpty().extracting(StreamFileSignature::getStatus).containsOnly(VERIFIED);
    }

    @Test
    void v5() {
        var signature = streamFileSignature();
        var signatures = List.of(signature);

        nodeSignatureVerifier.verify(signatures);
        assertThat(signatures).isNotEmpty().extracting(StreamFileSignature::getStatus).containsOnly(VERIFIED);
    }

    @Test
    void partialFailure() {
        var signature1 = streamFileSignature();
        var signature2 = streamFileSignature();
        var signature3 = streamFileSignature();
        signature3.setFileHashSignature(corruptSignature(signature3.getFileHashSignature()));
        var signatures = List.of(signature1, signature2, signature3);

        nodeSignatureVerifier.verify(signatures);
        assertThat(signatures).isNotEmpty().extracting(StreamFileSignature::getStatus)
                .containsExactly(VERIFIED, VERIFIED, DOWNLOADED);
    }

    @Test
    void invalidFileSignature() {
        var signature = streamFileSignature();
        signature.setFileHashSignature(corruptSignature(signature.getFileHashSignature()));
        var signatures = List.of(signature);

        nodeSignatureVerifier.verify(signatures);
        assertThat(signatures).isNotEmpty().extracting(StreamFileSignature::getStatus).doesNotContain(VERIFIED);
    }

    @Test
    void invalidMetadataSignature() {
        var signature = streamFileSignature();
        signature.setMetadataHashSignature(corruptSignature(signature.getMetadataHashSignature()));
        var signatures = List.of(signature);

        nodeSignatureVerifier.verify(signatures);
        assertThat(signatures).isNotEmpty().extracting(StreamFileSignature::getStatus).doesNotContain(VERIFIED);
    }

    @Test
    void noSignatureType() {
        var signature = streamFileSignature();
        signature.setSignatureType(null);
        var signatures = List.of(signature);

        nodeSignatureVerifier.verify(signatures);
        assertThat(signatures).isNotEmpty().extracting(StreamFileSignature::getStatus).doesNotContain(VERIFIED);
    }

    @Test
    void noFileHashSignature() {
        var signature = streamFileSignature();
        signature.setFileHashSignature(null);
        var signatures = List.of(signature);

        nodeSignatureVerifier.verify(signatures);
        assertThat(signatures).isNotEmpty().extracting(StreamFileSignature::getStatus).doesNotContain(VERIFIED);
    }

    @Test
    void noFileHash() {
        var signature = streamFileSignature();
        signature.setFileHash(null);
        var signatures = List.of(signature);

        nodeSignatureVerifier.verify(signatures);
        assertThat(signatures).isNotEmpty().extracting(StreamFileSignature::getStatus).doesNotContain(VERIFIED);
    }

    @SneakyThrows
    @Test
    void signedWithWrongAlgorithm() {
        signer = Signature.getInstance("SHA1withRSA", "SunRsaSign");
        signer.initSign(privateKey);
        var signatures = List.of(streamFileSignature());

        nodeSignatureVerifier.verify(signatures);
        assertThat(signatures).isNotEmpty().extracting(StreamFileSignature::getStatus).doesNotContain(VERIFIED);
    }

    private StreamFileSignature streamFileSignature() {
        var fileHash = TestUtils.generateRandomByteArray(48);
        var metadataHash = TestUtils.generateRandomByteArray(48);
        var node = ConsensusNodeStub.builder()
                .nodeAccountId(EntityId.of("0.0.3", ACCOUNT))
                .publicKey(publicKey)
                .build();

        StreamFileSignature streamFileSignature = new StreamFileSignature();
        streamFileSignature.setFileHash(fileHash);
        streamFileSignature.setFileHashSignature(signHash(fileHash));
        streamFileSignature.setFilename(StreamFilename.EPOCH);
        streamFileSignature.setMetadataHash(metadataHash);
        streamFileSignature.setMetadataHashSignature(signHash(metadataHash));
        streamFileSignature.setNode(node);
        streamFileSignature.setSignatureType(SignatureType.SHA_384_WITH_RSA);
        streamFileSignature.setStreamType(StreamType.RECORD);
        return streamFileSignature;
    }

    private byte[] corruptSignature(byte[] signature) {
        signature[0] = (byte) (signature[0] + 1);
        return signature;
    }

    private byte[] signHash(byte[] hash) {
        try {
            signer.update(hash);
            return signer.sign();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
