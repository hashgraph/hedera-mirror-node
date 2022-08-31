package com.hedera.mirror.importer.downloader;

/*
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.addressbook.AddressBook;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.domain.FileStreamSignature;
import com.hedera.mirror.importer.domain.FileStreamSignature.SignatureType;
import com.hedera.mirror.importer.exception.SignatureVerificationException;
import com.hedera.mirror.importer.repository.NodeStakeRepository;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class ConsensusValidatorImplTest extends IntegrationTest {

    private static final EntityId entity3 = EntityId.of(0L, 0L, 3L, EntityType.ACCOUNT);
    private static final EntityId entity4 = EntityId.of(0L, 0L, 4L, EntityType.ACCOUNT);
    private static final EntityId entity5 = EntityId.of(0L, 0L, 5L, EntityType.ACCOUNT);

    @Mock
    private AddressBookService addressBookService;
    @Mock
    private AddressBook currentAddressBook;
    @Mock
    private CommonDownloaderProperties commonDownloaderProperties;
    private ConsensusValidatorImpl consensusValidator;
    @Resource
    private NodeStakeRepository nodeStakeRepository;

    @BeforeEach
    void setup() {
        consensusValidator = new ConsensusValidatorImpl(addressBookService, commonDownloaderProperties,
                nodeStakeRepository);
        var nodeIdNodeAccountIdMap = Map.of(100L, entity3, 101L, entity4, 102L, entity5);
        when(addressBookService.getCurrent()).thenReturn(currentAddressBook);
        when(currentAddressBook.getNodeIdNodeAccountIdMap()).thenReturn(nodeIdNodeAccountIdMap);
        when(addressBookService.getCurrent()).thenReturn(currentAddressBook);
        when(commonDownloaderProperties.getConsensusRatio()).thenReturn(0.33333333333d);
    }

    @Test
    void testFailedVerificationWithEmptyAddressBook() {
        when(addressBookService.getCurrent()).thenReturn(AddressBook.builder().entries(Collections.emptyList())
                .build());
        nodeStakes(3, 3, 3);
        var fileStreamSignatures = List.of(buildFileStreamSignature());
        assertConsensusNotReached(fileStreamSignatures);
    }

    @Test
    void testVerifiedWithOneThirdNodeStakeConsensusNonVerifiedSignatures() {
        nodeStakes(3, 3, 3);

        FileStreamSignature fileStreamSignatureNode3 = buildFileStreamSignature();
        FileStreamSignature fileStreamSignatureNode4 = buildFileStreamSignature();
        fileStreamSignatureNode4.setNodeAccountId(entity4);
        fileStreamSignatureNode4.setFileHash(fileStreamSignatureNode3.getFileHash());
        fileStreamSignatureNode4.setStatus(FileStreamSignature.SignatureStatus.DOWNLOADED);
        FileStreamSignature fileStreamSignatureNode5 = buildFileStreamSignature();
        fileStreamSignatureNode5.setNodeAccountId(entity5);
        fileStreamSignatureNode5.setFileHash(fileStreamSignatureNode3.getFileHash());
        fileStreamSignatureNode5.setStatus(FileStreamSignature.SignatureStatus.DOWNLOADED);

        var fileStreamSignatures = List.of(fileStreamSignatureNode3, fileStreamSignatureNode4,
                fileStreamSignatureNode5);

        consensusValidator.validate(fileStreamSignatures);
        assertThat(fileStreamSignatures)
                .map(FileStreamSignature::getStatus)
                .containsExactly(FileStreamSignature.SignatureStatus.CONSENSUS_REACHED,
                        FileStreamSignature.SignatureStatus.DOWNLOADED, FileStreamSignature.SignatureStatus.DOWNLOADED);
    }

    @Test
    void testFailedVerificationWithLargeStakes() {
        // Total stake is 50 billion
        long nodeOneStake = 16_666_666_665L;
        long nodeTwoStake = 16_666_666_668L;
        long nodeThreeStake = 16_666_666_667L;

        nodeStakes(nodeOneStake, nodeTwoStake, nodeThreeStake);

        var fileStreamSignatureNode3 = buildFileStreamSignature();
        var fileStreamSignatureNode5 = buildFileStreamSignature();
        fileStreamSignatureNode5.setFileHash(fileStreamSignatureNode3.getFileHash());
        fileStreamSignatureNode5.setNodeAccountId(entity5);
        fileStreamSignatureNode5.setStatus(FileStreamSignature.SignatureStatus.DOWNLOADED);
        var fileStreamSignatures = List.of(
                fileStreamSignatureNode3,
                fileStreamSignatureNode5
        );

        assertConsensusNotReached(fileStreamSignatures);
    }

    @Test
    void testVerificationWithLargeStakes() {
        // Total stake is 50 billion
        long nodeOneStake = 8_333_333_334L;
        long nodeTwoStake = 33_333_333_333L;
        long nodeThreeStake = 8_333_333_333L;

        nodeStakes(nodeOneStake, nodeTwoStake, nodeThreeStake);

        var fileStreamSignatureNode3 = buildFileStreamSignature();
        var fileStreamSignatureNode5 = buildFileStreamSignature();
        fileStreamSignatureNode5.setFileHash(fileStreamSignatureNode3.getFileHash());
        fileStreamSignatureNode5.setNodeAccountId(entity5);
        var fileStreamSignatures = List.of(
                fileStreamSignatureNode3,
                fileStreamSignatureNode5
        );

        consensusValidator.validate(fileStreamSignatures);
        assertThat(fileStreamSignatures)
                .map(FileStreamSignature::getStatus)
                .containsExactly(FileStreamSignature.SignatureStatus.CONSENSUS_REACHED,
                        FileStreamSignature.SignatureStatus.CONSENSUS_REACHED);
    }

    @ParameterizedTest
    @EnumSource(value = FileStreamSignature.SignatureStatus.class, names = {"DOWNLOADED", "CONSENSUS_REACHED",
            "NOT_FOUND"})
    void testFailedVerificationInsufficientStake(FileStreamSignature.SignatureStatus status) {
        nodeStakes(1, 5, 1);

        var fileStreamSignatureNode3 = buildFileStreamSignature();
        FileStreamSignature fileStreamSignatureNode4 = buildFileStreamSignature();
        fileStreamSignatureNode4.setNodeAccountId(entity4);
        fileStreamSignatureNode4.setStatus(status);
        fileStreamSignatureNode4.setFileHash(fileStreamSignatureNode3.getFileHash());
        var fileStreamSignatureNode5 = buildFileStreamSignature();
        fileStreamSignatureNode5.setNodeAccountId(entity5);
        var fileStreamSignatures = List.of(
                fileStreamSignatureNode3,
                fileStreamSignatureNode4,
                fileStreamSignatureNode5
        );
        var signatures = fileStreamSignatures.stream().map(FileStreamSignature::getStatus).toList();

        Exception e = assertThrows(SignatureVerificationException.class, () -> consensusValidator
                .validate(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Consensus not reached for file"));

        // Assert that signature status is unchanged
        assertThat(fileStreamSignatures)
                .map(FileStreamSignature::getStatus)
                .containsExactlyElementsOf(signatures);
    }

    @Test
    void testFailedVerifiedWithAddressBookMissingNodeAccountIdConsensus() {
        var timestamp = domainBuilder.timestamp();
        domainBuilder.nodeStake()
                .customize(n -> n
                        .nodeId(500)
                        .consensusTimestamp(timestamp)
                        .stake(3L))
                .persist();
        domainBuilder.nodeStake()
                .customize(n -> n
                        .nodeId(102)
                        .consensusTimestamp(timestamp)
                        .stake(3L))
                .persist();

        var fileStreamSignatures = List.of(buildFileStreamSignature());
        assertConsensusNotReached(fileStreamSignatures);
    }

    @Test
    void testVerifiedWithFullNodeStakeConsensus() {
        when(commonDownloaderProperties.getConsensusRatio()).thenReturn(1d);
        nodeStakes(16_666_666_666L, 16_666_666_667L, 16_666_666_667L);

        FileStreamSignature fileStreamSignatureNode3 = buildFileStreamSignature();
        FileStreamSignature fileStreamSignatureNode4 = buildFileStreamSignature();
        fileStreamSignatureNode4.setNodeAccountId(entity4);
        fileStreamSignatureNode4.setFileHash(fileStreamSignatureNode3.getFileHash());
        FileStreamSignature fileStreamSignatureNode5 = buildFileStreamSignature();
        fileStreamSignatureNode5.setNodeAccountId(entity5);
        fileStreamSignatureNode5.setFileHash(fileStreamSignatureNode3.getFileHash());

        var fileStreamSignatures = List.of(fileStreamSignatureNode3, fileStreamSignatureNode4,
                fileStreamSignatureNode5);

        consensusValidator.validate(fileStreamSignatures);
        assertThat(fileStreamSignatures)
                .map(FileStreamSignature::getStatus)
                .containsOnly(FileStreamSignature.SignatureStatus.CONSENSUS_REACHED);
    }

    @Test
    void testFailedVerificationNoSignaturesNodeStakeConsensus() {
        nodeStakes(3, 3, 3);
        List<FileStreamSignature> emptyList = Collections.emptyList();
        assertConsensusNotReached(emptyList);
    }

    @Test
    void testSkipNodeStakeConsensus() {
        when(commonDownloaderProperties.getConsensusRatio()).thenReturn(0d);
        nodeStakes(1, 3, 3);
        var fileStreamSignatures = List.of(buildFileStreamSignature());

        consensusValidator.validate(fileStreamSignatures);
        assertThat(fileStreamSignatures)
                .map(FileStreamSignature::getStatus)
                .doesNotContain(FileStreamSignature.SignatureStatus.CONSENSUS_REACHED);
    }

    @Test
    void testSignaturesVerifiedWithOneThirdConsensusWithMissingSignatures() {
        FileStreamSignature fileStreamSignatureNode3 = buildFileStreamSignature();

        //Node 4 and 5 will not verify due to missing signature, but 1/3 verified will confirm consensus reached
        FileStreamSignature fileStreamSignatureNode4 = buildFileStreamSignature();
        fileStreamSignatureNode4.setNodeAccountId(entity4);
        fileStreamSignatureNode4.setStatus(FileStreamSignature.SignatureStatus.DOWNLOADED);

        FileStreamSignature fileStreamSignatureNode5 = buildFileStreamSignature();
        fileStreamSignatureNode5.setNodeAccountId(entity5);
        fileStreamSignatureNode5.setStatus(FileStreamSignature.SignatureStatus.DOWNLOADED);

        var fileStreamSignatures = List.of(fileStreamSignatureNode3, fileStreamSignatureNode4,
                fileStreamSignatureNode5);

        consensusValidator.validate(fileStreamSignatures);
        assertThat(fileStreamSignatures)
                .map(FileStreamSignature::getStatus)
                .containsExactly(FileStreamSignature.SignatureStatus.CONSENSUS_REACHED,
                        FileStreamSignature.SignatureStatus.DOWNLOADED, FileStreamSignature.SignatureStatus.DOWNLOADED);
    }

    @Test
    void testMultipleFileHashWithMultipleConsensusResults() {
        // First FileHash
        FileStreamSignature fileStreamSignature1 = buildFileStreamSignature();
        fileStreamSignature1.setStatus(FileStreamSignature.SignatureStatus.DOWNLOADED);

        // Second FileHash
        FileStreamSignature fileStreamSignature2 = buildFileStreamSignature();
        fileStreamSignature2.setFileHash(domainBuilder.bytes(256));

        // Third FileHash
        var thirdHash = domainBuilder.bytes(256);
        FileStreamSignature fileStreamSignature3 = buildFileStreamSignature();
        fileStreamSignature3.setFileHash(thirdHash);
        FileStreamSignature fileStreamSignature4 = buildFileStreamSignature();
        fileStreamSignature4.setStatus(FileStreamSignature.SignatureStatus.DOWNLOADED);
        fileStreamSignature4.setFileHash(thirdHash);

        // Forth FileHash
        FileStreamSignature fileStreamSignature5 = buildFileStreamSignature();
        fileStreamSignature5.setStatus(FileStreamSignature.SignatureStatus.DOWNLOADED);
        fileStreamSignature5.setFileHash(domainBuilder.bytes(256));

        var fileStreamSignatures = List.of(fileStreamSignature1, fileStreamSignature2, fileStreamSignature3,
                fileStreamSignature4, fileStreamSignature5);

        consensusValidator.validate(fileStreamSignatures);
        assertThat(fileStreamSignatures)
                .map(FileStreamSignature::getStatus)
                .containsExactly(FileStreamSignature.SignatureStatus.DOWNLOADED,
                        FileStreamSignature.SignatureStatus.CONSENSUS_REACHED,
                        FileStreamSignature.SignatureStatus.CONSENSUS_REACHED,
                        FileStreamSignature.SignatureStatus.DOWNLOADED,
                        FileStreamSignature.SignatureStatus.DOWNLOADED);
    }

    @SneakyThrows
    @Test
    void testSignaturesVerifiedWithFullConsensusRequired() {
        var nodeKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var publicKey = nodeKeyPair.getPublic();
        var nodeAccountIDPubKeyMap = new HashMap<String, PublicKey>();
        nodeAccountIDPubKeyMap.put("0.0.3", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.4", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.5", publicKey);
        when(currentAddressBook.getNodeAccountIDPubKeyMap()).thenReturn(nodeAccountIDPubKeyMap);
        when(commonDownloaderProperties.getConsensusRatio()).thenReturn(1d);

        FileStreamSignature fileStreamSignatureNode3 = buildFileStreamSignature();
        FileStreamSignature fileStreamSignatureNode4 = buildFileStreamSignature();
        fileStreamSignatureNode4.setNodeAccountId(entity4);
        fileStreamSignatureNode4.setFileHash(fileStreamSignatureNode3.getFileHash());

        FileStreamSignature fileStreamSignatureNode5 = buildFileStreamSignature();
        fileStreamSignatureNode5.setNodeAccountId(entity5);
        fileStreamSignatureNode5.setFileHash(fileStreamSignatureNode3.getFileHash());

        var fileStreamSignatures = List.of(fileStreamSignatureNode3, fileStreamSignatureNode4,
                fileStreamSignatureNode5);

        consensusValidator.validate(fileStreamSignatures);
        assertThat(fileStreamSignatures)
                .map(FileStreamSignature::getStatus)
                .containsOnly(FileStreamSignature.SignatureStatus.CONSENSUS_REACHED);
    }

    @SneakyThrows
    @Test
    void testFailedVerificationSignatureConsensus() {
        var nodeKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var publicKey = nodeKeyPair.getPublic();
        var nodeAccountIDPubKeyMap = new HashMap<String, PublicKey>();
        nodeAccountIDPubKeyMap.put("0.0.3", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.4", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.5", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.6", publicKey);
        when(currentAddressBook.getNodeAccountIDPubKeyMap()).thenReturn(nodeAccountIDPubKeyMap);

        FileStreamSignature fileStreamSignatureNode3 = buildFileStreamSignature();

        FileStreamSignature fileStreamSignatureNode4 = buildFileStreamSignature();
        fileStreamSignatureNode4.setNodeAccountId(entity4);
        fileStreamSignatureNode4.setStatus(FileStreamSignature.SignatureStatus.DOWNLOADED);

        FileStreamSignature fileStreamSignatureNode5 = buildFileStreamSignature();
        fileStreamSignatureNode5.setNodeAccountId(entity5);
        fileStreamSignatureNode5.setStatus(FileStreamSignature.SignatureStatus.DOWNLOADED);

        FileStreamSignature fileStreamSignatureNode6 = buildFileStreamSignature();
        fileStreamSignatureNode6.setNodeAccountId(new EntityId(0L, 0L, 6L, EntityType.ACCOUNT));
        fileStreamSignatureNode6.setStatus(FileStreamSignature.SignatureStatus.DOWNLOADED);

        var fileStreamSignatures = List.of(
                fileStreamSignatureNode3,
                fileStreamSignatureNode4,
                fileStreamSignatureNode5,
                fileStreamSignatureNode6
        );
        assertConsensusNotReached(fileStreamSignatures);
    }

    private void assertConsensusNotReached(List<FileStreamSignature> fileStreamSignatures) {
        Exception e = assertThrows(SignatureVerificationException.class, () -> consensusValidator
                .validate(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Consensus not reached for file"));
        assertThat(fileStreamSignatures)
                .map(FileStreamSignature::getStatus)
                .doesNotContain(FileStreamSignature.SignatureStatus.CONSENSUS_REACHED);
    }

    private void nodeStakes(long... stakes) {
        var timestamp = domainBuilder.timestamp();
        int nodeId = 100;
        for (long stake : stakes) {
            final int finalNodeId = nodeId;
            domainBuilder.nodeStake()
                    .customize(n -> n
                            .nodeId(finalNodeId)
                            .consensusTimestamp(timestamp)
                            .stake(stake))
                    .persist();
            nodeId++;
        }
    }

    private FileStreamSignature buildFileStreamSignature() {
        FileStreamSignature fileStreamSignature = new FileStreamSignature();
        fileStreamSignature.setFileHash(domainBuilder.bytes(256));
        fileStreamSignature.setFilename("");
        fileStreamSignature.setNodeAccountId(entity3);
        fileStreamSignature.setSignatureType(SignatureType.SHA_384_WITH_RSA);
        fileStreamSignature.setStatus(FileStreamSignature.SignatureStatus.VERIFIED);
        fileStreamSignature.setStreamType(StreamType.RECORD);
        return fileStreamSignature;
    }
}
