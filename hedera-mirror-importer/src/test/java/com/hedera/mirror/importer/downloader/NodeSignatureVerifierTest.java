package com.hedera.mirror.importer.downloader;

/*
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.logging.LoggingMeterRegistry;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.FileStreamSignature;
import com.hedera.mirror.importer.domain.FileStreamSignature.SignatureType;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.exception.SignatureVerificationException;

@ExtendWith(MockitoExtension.class)
class NodeSignatureVerifierTest {

    private static PrivateKey privateKey;
    private static PublicKey publicKey;

    private static final EntityId nodeId = new EntityId(0L, 0L, 3L, EntityTypeEnum.ACCOUNT.getId());
    private static final MeterRegistry meterRegistry = new LoggingMeterRegistry();
    private Signature signer;

    @Mock
    private AddressBookService addressBookService;

    @Mock
    private CommonDownloaderProperties commonDownloaderProperties;

    @Mock
    private AddressBook currentAddressBook;

    NodeSignatureVerifier nodeSignatureVerifier;

    @BeforeAll
    static void generateKeys() throws NoSuchAlgorithmException {
        KeyPair nodeKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        privateKey = nodeKeyPair.getPrivate();
        publicKey = nodeKeyPair.getPublic();
    }

    @BeforeEach
    void setup() throws GeneralSecurityException {
        nodeSignatureVerifier = new NodeSignatureVerifier(
                addressBookService,
                commonDownloaderProperties,
                meterRegistry);
        signer = Signature.getInstance("SHA384withRSA", "SunRsaSign");
        signer.initSign(privateKey);
        Map<String, PublicKey> nodeAccountIDPubKeyMap = new HashMap();
        nodeAccountIDPubKeyMap.put("0.0.3", publicKey);
        when(addressBookService.getCurrent()).thenReturn(currentAddressBook);
        when(currentAddressBook.getNodeAccountIDPubKeyMap()).thenReturn(nodeAccountIDPubKeyMap);
        when(commonDownloaderProperties.getConsensusRatio()).thenReturn(0.333f);
    }

    @Test
    void testV5FileStreamSignature() throws GeneralSecurityException {
        byte[] fileHash = TestUtils.generateRandomByteArray(48);
        byte[] metadataHash = TestUtils.generateRandomByteArray(48);

        FileStreamSignature fileStreamSignature = buildFileStreamSignature(fileHash, signHash(fileHash),
                metadataHash, signHash(metadataHash));
        nodeSignatureVerifier.verify(Arrays.asList(fileStreamSignature));
    }

    @Test
    void testV2FileStreamSignature() throws GeneralSecurityException {

        byte[] fileHash = TestUtils.generateRandomByteArray(48);

        FileStreamSignature fileStreamSignature = buildFileStreamSignature(fileHash, signHash(fileHash),
                null, null);

        nodeSignatureVerifier.verify(Arrays.asList(fileStreamSignature));
    }

    @Test
    void testInvalidFileSignature() throws GeneralSecurityException {

        byte[] fileHash = TestUtils.generateRandomByteArray(48);

        FileStreamSignature fileStreamSignature = buildFileStreamSignature(fileHash,
                corruptSignature(signHash(fileHash)),
                null, null);

        List<FileStreamSignature> fileStreamSignatures = Arrays.asList(buildBareBonesFileStreamSignature());
        Exception e = assertThrows(SignatureVerificationException.class, () -> nodeSignatureVerifier
                .verify(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Signature verification failed for file"));
    }

    @Test
    void testInvalidMetadataSignature() throws GeneralSecurityException {

        byte[] fileHash = TestUtils.generateRandomByteArray(48);
        byte[] metadataHash = TestUtils.generateRandomByteArray(48);

        FileStreamSignature fileStreamSignature = buildFileStreamSignature(fileHash, signHash(fileHash),
                metadataHash, corruptSignature(signHash(fileHash)));

        List<FileStreamSignature> fileStreamSignatures = Arrays.asList(buildBareBonesFileStreamSignature());
        Exception e = assertThrows(SignatureVerificationException.class, () -> nodeSignatureVerifier
                .verify(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Signature verification failed for file"));
    }

    @Test
    void testCannotReachConsensus() {
        Map<String, PublicKey> nodeAccountIDPubKeyMap = new HashMap();
        nodeAccountIDPubKeyMap.put("0.0.3", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.4", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.5", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.6", publicKey);

        when(currentAddressBook.getNodeAccountIDPubKeyMap()).thenReturn(nodeAccountIDPubKeyMap);

        List<FileStreamSignature> fileStreamSignatures = Arrays.asList(buildBareBonesFileStreamSignature());
        Exception e = assertThrows(SignatureVerificationException.class, () -> nodeSignatureVerifier
                .verify(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Insufficient downloaded signature file count, requires at least 0.333"));
    }

    @Test
    void testNoConsensusRequiredWithVerifiedSignatureFiles() throws GeneralSecurityException {
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
        when(commonDownloaderProperties.getConsensusRatio()).thenReturn(0f);

        byte[] fileHash = TestUtils.generateRandomByteArray(48);
        byte[] fileHashSignature = signHash(fileHash);

        FileStreamSignature fileStreamSignatureNode = buildFileStreamSignature(fileHash, fileHashSignature,
                null, null);

        // only 1 node node necessary
        nodeSignatureVerifier.verify(List.of(fileStreamSignatureNode));
    }

    @Test
    void testNoConsensusRequiredWithNoVerifiedSignatureFiles() throws GeneralSecurityException {
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
        when(commonDownloaderProperties.getConsensusRatio()).thenReturn(0f);

        Exception e = assertThrows(SignatureVerificationException.class, () -> nodeSignatureVerifier
                .verify(List.of()));
        assertTrue(e.getMessage().contains("Signature verification failed for file"));
    }

    @Test
    void testVerifiedWithOneThirdConsensus() throws GeneralSecurityException {
        Map<String, PublicKey> nodeAccountIDPubKeyMap = new HashMap();
        nodeAccountIDPubKeyMap.put("0.0.3", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.4", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.5", publicKey);
        when(currentAddressBook.getNodeAccountIDPubKeyMap()).thenReturn(nodeAccountIDPubKeyMap);

        byte[] fileHash = TestUtils.generateRandomByteArray(48);
        byte[] fileHashSignature = signHash(fileHash);

        nodeSignatureVerifier
                .verify(Arrays.asList(buildFileStreamSignature(fileHash, fileHashSignature,
                        null, null)));
    }

    @Test
    void testVerifiedWithOneThirdConsensusWithMissingSignatures() throws GeneralSecurityException {
        Map<String, PublicKey> nodeAccountIDPubKeyMap = new HashMap();
        nodeAccountIDPubKeyMap.put("0.0.3", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.4", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.5", publicKey);
        when(currentAddressBook.getNodeAccountIDPubKeyMap()).thenReturn(nodeAccountIDPubKeyMap);

        byte[] fileHash = TestUtils.generateRandomByteArray(48);
        byte[] fileHashSignature = signHash(fileHash);

        FileStreamSignature fileStreamSignatureNode3 = buildFileStreamSignature(fileHash, fileHashSignature,
                null, null);

        //Node 4 and 5 will not verify due to missing signature, but 1/3 verified will confirm consensus reached
        FileStreamSignature fileStreamSignatureNode4 = buildFileStreamSignature(fileHash, null,
                null, null);
        fileStreamSignatureNode4.setNodeAccountId(new EntityId(0L, 0L, 4L, EntityTypeEnum.ACCOUNT.getId()));
        FileStreamSignature fileStreamSignatureNode5 = buildFileStreamSignature(fileHash, null,
                null, null);
        fileStreamSignatureNode5.setNodeAccountId(new EntityId(0L, 0L, 5L, EntityTypeEnum.ACCOUNT.getId()));

        nodeSignatureVerifier
                .verify(Arrays.asList(fileStreamSignatureNode3, fileStreamSignatureNode4, fileStreamSignatureNode5));
    }

    @Test
    void testVerifiedWithFullConsensusRequired() throws GeneralSecurityException {
        Map<String, PublicKey> nodeAccountIDPubKeyMap = new HashMap();
        nodeAccountIDPubKeyMap.put("0.0.3", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.4", publicKey);
        nodeAccountIDPubKeyMap.put("0.0.5", publicKey);
        when(currentAddressBook.getNodeAccountIDPubKeyMap()).thenReturn(nodeAccountIDPubKeyMap);
        when(commonDownloaderProperties.getConsensusRatio()).thenReturn(1f);

        byte[] fileHash = TestUtils.generateRandomByteArray(48);
        byte[] fileHashSignature = signHash(fileHash);

        FileStreamSignature fileStreamSignatureNode3 = buildFileStreamSignature(fileHash, fileHashSignature,
                null, null);
        fileStreamSignatureNode3.setNodeAccountId(new EntityId(0L, 0L, 3L, EntityTypeEnum.ACCOUNT.getId()));

        FileStreamSignature fileStreamSignatureNode4 = buildFileStreamSignature(fileHash, fileHashSignature,
                null, null);
        fileStreamSignatureNode4.setNodeAccountId(new EntityId(0L, 0L, 4L, EntityTypeEnum.ACCOUNT.getId()));

        FileStreamSignature fileStreamSignatureNode5 = buildFileStreamSignature(fileHash, fileHashSignature,
                null, null);
        fileStreamSignatureNode5.setNodeAccountId(new EntityId(0L, 0L, 5L, EntityTypeEnum.ACCOUNT.getId()));

        nodeSignatureVerifier
                .verify(Arrays.asList(fileStreamSignatureNode3, fileStreamSignatureNode4, fileStreamSignatureNode5));
    }

    @Test
    void testNoSignatureType() throws GeneralSecurityException {

        byte[] fileHash = TestUtils.generateRandomByteArray(48);

        FileStreamSignature fileStreamSignature = buildFileStreamSignature(fileHash, signHash(fileHash),
                null, null);
        fileStreamSignature.setSignatureType(null);

        List<FileStreamSignature> fileStreamSignatures = Arrays.asList(fileStreamSignature);
        Exception e = assertThrows(SignatureVerificationException.class, () -> nodeSignatureVerifier
                .verify(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Signature verification failed for file"));
    }

    @Test
    void testNoFileHashSignature() throws GeneralSecurityException {

        byte[] fileHash = TestUtils.generateRandomByteArray(48);

        FileStreamSignature fileStreamSignature = buildFileStreamSignature(fileHash, null,
                null, null);

        List<FileStreamSignature> fileStreamSignatures = Arrays.asList(fileStreamSignature);
        Exception e = assertThrows(SignatureVerificationException.class, () -> nodeSignatureVerifier
                .verify(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Signature verification failed for file"));
    }

    @Test
    void testNoFileHash() throws GeneralSecurityException {

        byte[] fileHash = TestUtils.generateRandomByteArray(48);

        FileStreamSignature fileStreamSignature = buildFileStreamSignature(null, signHash(fileHash),
                null, null);

        List<FileStreamSignature> fileStreamSignatures = Arrays.asList(fileStreamSignature);
        Exception e = assertThrows(SignatureVerificationException.class, () -> nodeSignatureVerifier
                .verify(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Signature verification failed for file"));
    }

    @Test
    void testSignedWithWrongAlgorithm() throws GeneralSecurityException {

        signer = Signature.getInstance("SHA1withRSA", "SunRsaSign");
        signer.initSign(privateKey);
        byte[] entireFileHash = TestUtils.generateRandomByteArray(48);

        FileStreamSignature fileStreamSignature = buildFileStreamSignature(entireFileHash, signHash(entireFileHash),
                null, null);

        List<FileStreamSignature> fileStreamSignatures = Arrays.asList(fileStreamSignature);
        Exception e = assertThrows(SignatureVerificationException.class, () -> nodeSignatureVerifier
                .verify(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Signature verification failed for file"));
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
