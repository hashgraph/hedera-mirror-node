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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.addressbook.AddressBook;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.domain.FileStreamSignature;
import com.hedera.mirror.importer.domain.FileStreamSignature.SignatureType;
import com.hedera.mirror.importer.exception.SignatureVerificationException;
import com.hedera.mirror.importer.repository.NodeStakeRepository;

class NodeSignatureVerifierTest extends IntegrationTest {

    private static PrivateKey privateKey;
    private static PublicKey publicKey;

    private static final EntityId nodeId = new EntityId(0L, 0L, 3L, EntityType.ACCOUNT);
    private Signature signer;

    @Mock
    private AddressBookService addressBookService;
    @Mock
    private AddressBook currentAddressBook;
    @Mock
    private CommonDownloaderProperties commonDownloaderProperties;
    private NodeSignatureVerifier nodeSignatureVerifier;
    @Mock
    private NodeStakeRepository nodeStakeRepository;

    @SneakyThrows
    @BeforeAll
    static void generateKeys() {
        KeyPair nodeKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        privateKey = nodeKeyPair.getPrivate();
        publicKey = nodeKeyPair.getPublic();
    }

    @SneakyThrows
    @BeforeEach
    void setup() {
        var consensusValidator = new ConsensusValidatorImpl(addressBookService, commonDownloaderProperties,
                nodeStakeRepository);
        nodeSignatureVerifier = new NodeSignatureVerifier(
                addressBookService,
                consensusValidator);
        signer = Signature.getInstance("SHA384withRSA", "SunRsaSign");
        signer.initSign(privateKey);
        Map<String, PublicKey> nodeAccountIDPubKeyMap = new HashMap();
        nodeAccountIDPubKeyMap.put("0.0.3", publicKey);
        when(addressBookService.getCurrent()).thenReturn(currentAddressBook);
        when(currentAddressBook.getNodeAccountIDPubKeyMap()).thenReturn(nodeAccountIDPubKeyMap);
        var nodeIdNodeAccountIdMap = Map.of(100L, nodeId);
        when(currentAddressBook.getNodeIdNodeAccountIdMap()).thenReturn(nodeIdNodeAccountIdMap);
        when(commonDownloaderProperties.getConsensusRatio()).thenReturn(BigDecimal.ONE.divide(BigDecimal.valueOf(3),
                19, RoundingMode.DOWN));
    }

    @SneakyThrows
    @Test
    void testV5FileStreamSignature() {
        byte[] fileHash = TestUtils.generateRandomByteArray(48);
        byte[] metadataHash = TestUtils.generateRandomByteArray(48);

        FileStreamSignature fileStreamSignature = buildFileStreamSignature(fileHash, signHash(fileHash),
                metadataHash, signHash(metadataHash));
        nodeSignatureVerifier.verify(Arrays.asList(fileStreamSignature));
    }

    @SneakyThrows
    @Test
    void testV2FileStreamSignature() {

        byte[] fileHash = TestUtils.generateRandomByteArray(48);

        FileStreamSignature fileStreamSignature = buildFileStreamSignature(fileHash, signHash(fileHash),
                null, null);

        nodeSignatureVerifier.verify(Arrays.asList(fileStreamSignature));
    }

    @SneakyThrows
    @Test
    void testInvalidFileSignature() {

        byte[] fileHash = TestUtils.generateRandomByteArray(48);

        FileStreamSignature fileStreamSignature = buildFileStreamSignature(fileHash,
                corruptSignature(signHash(fileHash)),
                null, null);

        List<FileStreamSignature> fileStreamSignatures = Arrays.asList(buildBareBonesFileStreamSignature());
        Exception e = assertThrows(SignatureVerificationException.class, () -> nodeSignatureVerifier
                .verify(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Consensus not reached for file"));
    }

    @SneakyThrows
    @Test
    void testInvalidMetadataSignature() {

        byte[] fileHash = TestUtils.generateRandomByteArray(48);
        byte[] metadataHash = TestUtils.generateRandomByteArray(48);

        FileStreamSignature fileStreamSignature = buildFileStreamSignature(fileHash, signHash(fileHash),
                metadataHash, corruptSignature(signHash(fileHash)));

        List<FileStreamSignature> fileStreamSignatures = Arrays.asList(fileStreamSignature);
        Exception e = assertThrows(SignatureVerificationException.class, () -> nodeSignatureVerifier
                .verify(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Consensus not reached for file"));
    }

    @Test
    void testCannotReachConsensus() {
        Map<String, PublicKey> nodeAccountIDPubKeyMap = new HashMap();
        nodeAccountIDPubKeyMap.put("0.0.3", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.4", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.5", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.6", publicKey);

        when(currentAddressBook.getNodeAccountIDPubKeyMap()).thenReturn(nodeAccountIDPubKeyMap);
        when(currentAddressBook.getNodeIdNodeAccountIdMap()).thenReturn(Map.of(100L, nodeId, 101L,
                nodeId, 102L, nodeId, 103L, nodeId));

        List<FileStreamSignature> fileStreamSignatures = Arrays.asList(buildBareBonesFileStreamSignature());
        Exception e = assertThrows(SignatureVerificationException.class, () -> nodeSignatureVerifier
                .verify(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Consensus not reached for file"));
    }

    @SneakyThrows
    @Test
    void testNoConsensusRequiredWithVerifiedSignatureFiles() {
        Map<String, PublicKey> nodeAccountIDPubKeyMap = new HashMap();
        nodeAccountIDPubKeyMap.put("0.0.3", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.4", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.5", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.6", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.7", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.8", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.9", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.10", publicKey);

        when(currentAddressBook.getNodeAccountIDPubKeyMap()).thenReturn(nodeAccountIDPubKeyMap);
        when(currentAddressBook.getNodeIdNodeAccountIdMap()).thenReturn(Map.of(100L, nodeId, 101L,
                nodeId, 102L, nodeId, 103L, nodeId, 104L, nodeId, 105L, nodeId, 106L, nodeId, 107L, nodeId));
        when(commonDownloaderProperties.getConsensusRatio()).thenReturn(BigDecimal.ZERO);

        byte[] fileHash = TestUtils.generateRandomByteArray(48);
        byte[] fileHashSignature = signHash(fileHash);

        FileStreamSignature fileStreamSignatureNode = buildFileStreamSignature(fileHash, fileHashSignature,
                null, null);

        // only 1 node necessary
        nodeSignatureVerifier.verify(List.of(fileStreamSignatureNode));
    }

    @Test
    void testNoConsensusRequiredWithNoVerifiedSignatureFiles() {
        Map<String, PublicKey> nodeAccountIDPubKeyMap = new HashMap();
        nodeAccountIDPubKeyMap.put("0.0.3", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.4", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.5", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.6", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.7", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.8", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.9", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.10", publicKey);

        when(currentAddressBook.getNodeAccountIDPubKeyMap()).thenReturn(nodeAccountIDPubKeyMap);
        when(currentAddressBook.getNodeIdNodeAccountIdMap()).thenReturn(Map.of(100L, nodeId, 101L, nodeId, 102L, nodeId,
                103L, nodeId, 104L, nodeId, 105L, nodeId, 106L, nodeId, 107L, nodeId));
        when(commonDownloaderProperties.getConsensusRatio()).thenReturn(BigDecimal.ZERO);

        Exception e = assertThrows(SignatureVerificationException.class, () -> nodeSignatureVerifier
                .verify(List.of()));
        assertTrue(e.getMessage().contains("Consensus not reached for file"));
    }

    @SneakyThrows
    @Test
    void testVerifiedWithOneThirdConsensus() {
        Map<String, PublicKey> nodeAccountIDPubKeyMap = new HashMap();
        nodeAccountIDPubKeyMap.put("0.0.3", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.4", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.5", publicKey);
        when(currentAddressBook.getNodeAccountIDPubKeyMap()).thenReturn(nodeAccountIDPubKeyMap);
        when(currentAddressBook.getNodeIdNodeAccountIdMap()).thenReturn(Map.of(100L, nodeId, 101L, nodeId, 102L,
                nodeId));

        byte[] fileHash = TestUtils.generateRandomByteArray(48);
        byte[] fileHashSignature = signHash(fileHash);

        nodeSignatureVerifier
                .verify(Arrays.asList(buildFileStreamSignature(fileHash, fileHashSignature,
                        null, null)));
    }

    @SneakyThrows
    @Test
    void testVerifiedWithOneThirdConsensusWithMissingSignatures() {
        Map<String, PublicKey> nodeAccountIDPubKeyMap = new HashMap();
        nodeAccountIDPubKeyMap.put("0.0.3", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.4", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.5", publicKey);
        when(currentAddressBook.getNodeAccountIDPubKeyMap()).thenReturn(nodeAccountIDPubKeyMap);
        when(currentAddressBook.getNodeIdNodeAccountIdMap()).thenReturn(Map.of(100L, nodeId, 101L, nodeId, 102L,
                nodeId));

        byte[] fileHash = TestUtils.generateRandomByteArray(48);
        byte[] fileHashSignature = signHash(fileHash);

        FileStreamSignature fileStreamSignatureNode3 = buildFileStreamSignature(fileHash, fileHashSignature,
                null, null);

        //Node 4 and 5 will not verify due to missing signature, but 1/3 verified will confirm consensus reached
        FileStreamSignature fileStreamSignatureNode4 = buildFileStreamSignature(fileHash, null,
                null, null);
        fileStreamSignatureNode4.setNodeAccountId(new EntityId(0L, 0L, 4L, EntityType.ACCOUNT));
        FileStreamSignature fileStreamSignatureNode5 = buildFileStreamSignature(fileHash, null,
                null, null);
        fileStreamSignatureNode5.setNodeAccountId(new EntityId(0L, 0L, 5L, EntityType.ACCOUNT));

        nodeSignatureVerifier
                .verify(Arrays.asList(fileStreamSignatureNode3, fileStreamSignatureNode4, fileStreamSignatureNode5));
    }

    @SneakyThrows
    @Test
    void testVerifiedWithFullConsensusRequired() {
        Map<String, PublicKey> nodeAccountIDPubKeyMap = new HashMap();
        nodeAccountIDPubKeyMap.put("0.0.3", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.4", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.5", publicKey);
        when(currentAddressBook.getNodeAccountIDPubKeyMap()).thenReturn(nodeAccountIDPubKeyMap);
        when(currentAddressBook.getNodeIdNodeAccountIdMap()).thenReturn(Map.of(100L, nodeId, 101L,
                EntityId.of(0L, 0L, 4L, EntityType.ACCOUNT), 102L,
                EntityId.of(0L, 0L, 5L, EntityType.ACCOUNT)));
        when(commonDownloaderProperties.getConsensusRatio()).thenReturn(BigDecimal.ONE);

        byte[] fileHash = TestUtils.generateRandomByteArray(48);
        byte[] fileHashSignature = signHash(fileHash);

        FileStreamSignature fileStreamSignatureNode3 = buildFileStreamSignature(fileHash, fileHashSignature,
                null, null);
        fileStreamSignatureNode3.setNodeAccountId(new EntityId(0L, 0L, 3L, EntityType.ACCOUNT));

        FileStreamSignature fileStreamSignatureNode4 = buildFileStreamSignature(fileHash, fileHashSignature,
                null, null);
        fileStreamSignatureNode4.setNodeAccountId(new EntityId(0L, 0L, 4L, EntityType.ACCOUNT));

        FileStreamSignature fileStreamSignatureNode5 = buildFileStreamSignature(fileHash, fileHashSignature,
                null, null);
        fileStreamSignatureNode5.setNodeAccountId(new EntityId(0L, 0L, 5L, EntityType.ACCOUNT));

        nodeSignatureVerifier
                .verify(Arrays.asList(fileStreamSignatureNode3, fileStreamSignatureNode4, fileStreamSignatureNode5));
    }

    @SneakyThrows
    @Test
    void testNoSignatureType() {

        byte[] fileHash = TestUtils.generateRandomByteArray(48);

        FileStreamSignature fileStreamSignature = buildFileStreamSignature(fileHash, signHash(fileHash),
                null, null);
        fileStreamSignature.setSignatureType(null);

        List<FileStreamSignature> fileStreamSignatures = Arrays.asList(fileStreamSignature);
        Exception e = assertThrows(SignatureVerificationException.class, () -> nodeSignatureVerifier
                .verify(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Consensus not reached for file"));
    }

    @Test
    void testNoFileHashSignature() {

        byte[] fileHash = TestUtils.generateRandomByteArray(48);

        FileStreamSignature fileStreamSignature = buildFileStreamSignature(fileHash, null,
                null, null);

        List<FileStreamSignature> fileStreamSignatures = Arrays.asList(fileStreamSignature);
        Exception e = assertThrows(SignatureVerificationException.class, () -> nodeSignatureVerifier
                .verify(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Consensus not reached for file"));
    }

    @Test
    void testNoFileHash() throws GeneralSecurityException {

        byte[] fileHash = TestUtils.generateRandomByteArray(48);

        FileStreamSignature fileStreamSignature = buildFileStreamSignature(null, signHash(fileHash),
                null, null);

        List<FileStreamSignature> fileStreamSignatures = Arrays.asList(fileStreamSignature);
        Exception e = assertThrows(SignatureVerificationException.class, () -> nodeSignatureVerifier
                .verify(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Consensus not reached for file"));
    }

    @SneakyThrows
    @Test
    void testSignedWithWrongAlgorithm() {
        signer = Signature.getInstance("SHA1withRSA", "SunRsaSign");
        signer.initSign(privateKey);
        byte[] entireFileHash = TestUtils.generateRandomByteArray(48);

        FileStreamSignature fileStreamSignature = buildFileStreamSignature(entireFileHash, signHash(entireFileHash),
                null, null);

        List<FileStreamSignature> fileStreamSignatures = Arrays.asList(fileStreamSignature);
        Exception e = assertThrows(SignatureVerificationException.class, () -> nodeSignatureVerifier
                .verify(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Consensus not reached for file"));
    }

    private byte[] signHash(byte[] hash) throws GeneralSecurityException {
        signer.update(hash);
        return signer.sign();
    }

    private FileStreamSignature buildFileStreamSignature(byte[] fileHash, byte[] fileHashSignature,
                                                         byte[] metadataHash, byte[] metadataSignature) {
        FileStreamSignature fileStreamSignature = buildBareBonesFileStreamSignature();

        fileStreamSignature.setFileHash(fileHash);
        fileStreamSignature.setMetadataHash(metadataHash);

        fileStreamSignature.setFileHashSignature(fileHashSignature);
        fileStreamSignature.setMetadataHashSignature(metadataSignature);

        return fileStreamSignature;
    }

    private FileStreamSignature buildBareBonesFileStreamSignature() {
        FileStreamSignature fileStreamSignature = new FileStreamSignature();
        fileStreamSignature.setFilename("");
        fileStreamSignature.setNodeAccountId(nodeId);
        fileStreamSignature.setSignatureType(SignatureType.SHA_384_WITH_RSA);
        fileStreamSignature.setStreamType(StreamType.RECORD);
        return fileStreamSignature;
    }

    private byte[] corruptSignature(byte[] signature) {
        signature[0] = (byte) (signature[0] + 1);
        return signature;
    }
}
