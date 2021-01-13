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

import java.io.File;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
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
import com.hedera.mirror.importer.exception.SignatureVerificationException;

@ExtendWith(MockitoExtension.class)
class NodeSignatureVerifierTest {

    private static KeyPair nodeKeyPair;
    private static final EntityId nodeId = new EntityId(0L, 0L, 3L, EntityTypeEnum.ACCOUNT.getId());
    private Signature signer;

    @Mock
    private AddressBookService addressBookService;

    @Mock
    private AddressBook currentAddressBook;

    NodeSignatureVerifier nodeSignatureVerifier;

    @BeforeAll
    static void key() throws NoSuchAlgorithmException {
        nodeKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    }

    @BeforeEach
    void setup() throws GeneralSecurityException {
        nodeSignatureVerifier = new NodeSignatureVerifier(addressBookService);
        signer = Signature.getInstance("SHA384withRSA", "SunRsaSign");
        signer.initSign(nodeKeyPair.getPrivate());
        Map<String, PublicKey> nodeAccountIDPubKeyMap = new HashMap();
        nodeAccountIDPubKeyMap.put("0.0.3", nodeKeyPair.getPublic());
        when(addressBookService.getCurrent()).thenReturn(currentAddressBook);
        when(currentAddressBook.getNodeAccountIDPubKeyMap()).thenReturn(nodeAccountIDPubKeyMap);
    }

    @Test
    void testV5FileStreamSignature() throws GeneralSecurityException {
        byte[] entireFileHash = TestUtils.generateRandomByteArray(48);
        byte[] metadataHash = TestUtils.generateRandomByteArray(48);

        FileStreamSignature fileStreamSignature = buildFileStreamSignature(entireFileHash, signHash(entireFileHash),
                metadataHash, signHash(metadataHash));
        nodeSignatureVerifier.verify(Arrays.asList(fileStreamSignature));
    }

    @Test
    void testV2FileStreamSignature() throws GeneralSecurityException {

        byte[] entireFileHash = TestUtils.generateRandomByteArray(48);

        FileStreamSignature fileStreamSignature = buildFileStreamSignature(entireFileHash, signHash(entireFileHash),
                null, null);

        nodeSignatureVerifier.verify(Arrays.asList(fileStreamSignature));
    }

    @Test
    void testCannotReachConsensus() {
        Map<String, PublicKey> nodeAccountIDPubKeyMap = new HashMap();
        nodeAccountIDPubKeyMap.put("0.0.3", nodeKeyPair.getPublic());
        nodeAccountIDPubKeyMap.put("0.0.4", nodeKeyPair.getPublic());
        nodeAccountIDPubKeyMap.put("0.0.5", nodeKeyPair.getPublic());
        nodeAccountIDPubKeyMap.put("0.0.6", nodeKeyPair.getPublic());

        when(currentAddressBook.getNodeAccountIDPubKeyMap()).thenReturn(nodeAccountIDPubKeyMap);

        List<FileStreamSignature> fileStreamSignatures = Arrays.asList(buildBareBonesFileStreamSignature());
        Exception e = assertThrows(SignatureVerificationException.class, () -> nodeSignatureVerifier
                .verify(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Require at least 1/3 signature files to reach consensus"));
    }

    @Test
    void testVerifiedWithPartialSuccess() throws GeneralSecurityException {
        Map<String, PublicKey> nodeAccountIDPubKeyMap = new HashMap();
        nodeAccountIDPubKeyMap.put("0.0.3", nodeKeyPair.getPublic());
        nodeAccountIDPubKeyMap.put("0.0.4", nodeKeyPair.getPublic());
        when(currentAddressBook.getNodeAccountIDPubKeyMap()).thenReturn(nodeAccountIDPubKeyMap);

        byte[] entireFileHash = TestUtils.generateRandomByteArray(48);
        byte[] entireFileSignature = signHash(entireFileHash);

        FileStreamSignature fileStreamSignatureNode3 = buildFileStreamSignature(entireFileHash, entireFileSignature,
                null, null);
        //Node 4 will not verify due to missing signature, but 1/2 verified will confirm consensus reached
        FileStreamSignature fileStreamSignatureNode4 = buildFileStreamSignature(entireFileHash, null,
                null, null);
        fileStreamSignatureNode4.setNodeAccountId(new EntityId(0L, 0L, 4L, EntityTypeEnum.ACCOUNT.getId()));

        nodeSignatureVerifier.verify(Arrays.asList(fileStreamSignatureNode3, fileStreamSignatureNode3));
    }

    @Test
    void testNoSignatureType() throws GeneralSecurityException {

        byte[] entireFileHash = TestUtils.generateRandomByteArray(48);

        FileStreamSignature fileStreamSignature = buildFileStreamSignature(entireFileHash, signHash(entireFileHash),
                null, null);
        fileStreamSignature.setSignatureType(null);

        List<FileStreamSignature> fileStreamSignatures = Arrays.asList(fileStreamSignature);
        Exception e = assertThrows(SignatureVerificationException.class, () -> nodeSignatureVerifier
                .verify(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Signature verification failed for file"));
    }

    @Test
    void testNoSignature() throws GeneralSecurityException {

        byte[] entireFileHash = TestUtils.generateRandomByteArray(48);

        FileStreamSignature fileStreamSignature = buildFileStreamSignature(entireFileHash, null,
                null, null);

        List<FileStreamSignature> fileStreamSignatures = Arrays.asList(fileStreamSignature);
        Exception e = assertThrows(SignatureVerificationException.class, () -> nodeSignatureVerifier
                .verify(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Signature verification failed for file"));
    }

    @Test
    void testNoPublicKey() throws GeneralSecurityException {

        byte[] entireFileHash = TestUtils.generateRandomByteArray(48);

        FileStreamSignature fileStreamSignature = buildFileStreamSignature(null, signHash(entireFileHash),
                null, null);

        List<FileStreamSignature> fileStreamSignatures = Arrays.asList(fileStreamSignature);
        Exception e = assertThrows(SignatureVerificationException.class, () -> nodeSignatureVerifier
                .verify(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Signature verification failed for file"));
    }

    @Test
    void testSignedWithWrongAlgorithm() throws GeneralSecurityException {

        signer = Signature.getInstance("SHA1withRSA", "SunRsaSign");
        signer.initSign(nodeKeyPair.getPrivate());
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

    private FileStreamSignature buildFileStreamSignature(byte[] entireFileHash, byte[] entireFileSignature,
                                                         byte[] metadataHash, byte[] metadataSignature) {
        FileStreamSignature fileStreamSignature = buildBareBonesFileStreamSignature();

        fileStreamSignature.setEntireFileHash(entireFileHash);
        fileStreamSignature.setMetadataHash(metadataHash);

        fileStreamSignature.setEntireFileSignature(entireFileSignature);
        fileStreamSignature.setMetadataSignature(metadataSignature);

        return fileStreamSignature;
    }

    private FileStreamSignature buildBareBonesFileStreamSignature() {
        FileStreamSignature fileStreamSignature = new FileStreamSignature();
        fileStreamSignature.setFile(new File(""));
        fileStreamSignature.setNodeAccountId(nodeId);
        fileStreamSignature.setSignatureType(SignatureType.SHA_384_WITH_RSA);
        return fileStreamSignature;
    }
}
