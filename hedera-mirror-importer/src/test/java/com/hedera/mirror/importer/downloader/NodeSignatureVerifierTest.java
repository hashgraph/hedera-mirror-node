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
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.codec.binary.Base64;
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

    private static final String privateKeyBase64String =
            "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCXEhtKQJAZ32pYguWe" +
                    "+2VR7XI2RzY4k4hK4MaQd7VqDzJV9nDh34M2nK+3TWDL29zAypyZx1v2cgdrnsdWRnD+ZubMCMedyB/SZRnBhaTp+MvWA" +
                    "/Q3Bmf3VK5M3k4/EUUI92HbvOoj4dndcmwsKXfH/NlKP8UZ1ZmEm1LFPGgqHCtEZpfD8p" +
                    "+uQRh8QURmT8m3XeaDQBsiOD8x4ZDo1PgKN0BxV9KWrnK125OjRn+CzCMWPCnMdSnsN" +
                    "/YbPO6yqNS1wBwj14FDdoEvrTjeb5azlcvzNe+hayMzU6ITKm2/uKbwW+Zd1skuH/bx65HVVPME9Rni9" +
                    "/xrqXC7ryGwqxi3AgMBAAECggEAPtSoBvzNMgWKnF9sku+p1yYzX0HE2kj54XKVAxbWm9LQM5J4pmiokPkf19PV01OQ" +
                    "/5oFAaw5okkQrwDtlQNdEWHI0clBBG1sVrv3t1YXHbx9QniIhK4kZWiRyaSX1IEhPjZtO8/Zba0MSJ7DQKbKi6Gs2cWl" +
                    "+zWsUMus5B1YkVJcQSTSggqz3V5pjNFdl8+70kVOxpNkALnHZHwDTgVlY46N/s67P3io1cQPCfUmGreCzfWCziF0peumgjIY" +
                    "/1qbBu18KKs" +
                    "/zqm6rhFGj1c6O4tHd7yeEQbdABrCwUOtTIdBqY3ZIvVnVVxFZ3OOtibGOl2kAoxLB5HVZFp9RDXRiQKBgQDKgqOM3a" +
                    "+dqIJWh92xVx4sdUF1D6JTC2G3vBK9sBE5yMFsXvNOMm4xWp2+CgciWbo8m8MRL7MeTQ4CysCGQbFE" +
                    "/yPFMwXofpJZPyOHPhoGdnmJnqW6xi7Me+eHhv+iR2VwIz0MwLSVy9hKNaBbhM//YtwSr7g+6McQHXbeWyxxNQKBgQC" +
                    "++TiugsT59p8M/F9+ktSF/nfWS6bGFm1C9hTHgAZxINxif9dVuG7f7afdCXjEilQcDkrEaAJ1WEnVITNV9BMpQAV" +
                    "+GEfKqSHxfHiOioS3N872T4dB9jEO/7S75zz2k+eFzMJBpG+BE9J97" +
                    "/gqdH7v7AiR1b96AJRu0S6tYf6ruwKBgGRCNTKCdnV5fb3VWh54YQnlq1iHOvgeRGywgh7DUmPnTkuW3qIyOXfZwwrY8BtDjP" +
                    "6ApxyVHvq7b1pWguZ1E4xzPIRe9GfcchwZND+6sSvN7/IAR1Cm2XiHR2NDpL/01PWlnI35we3" +
                    "/k795uUBWCpwHl6jwsikDGbq" +
                    "Su8zuGpyZAoGAGk00s0QrYMnIif9QH5yVTIcJdighJfL8xVYi8n79ZCNEdwRoYdPu4URX9CdTzK3Ie7y0K2yvuf2Y3ZOfAF2H" +
                    "Lg01NHKfoJe+pwWfjPIi6SD0jhPR6xG/G/O3rpFgYg1ou5LBxkyhVsOmH9Ym9aHpwZ1eaMdpgaIGz2Rb62Ets" +
                    "/UCgYARvEfr3NLtJHqRQQIdExWgqpi+emrblVM+F94IoXCGLY" +
                    "6FJLKo+vujur/AeipC66IsPVfELCnjJLECFH6vdU5k/zchmEw" +
                    "/7Lw7T6bK0ndvzPG2oC8ud4fsxyUxZu9sRe1fxI0CSgKQ9IUifUrTvY4u/Jj8bAPPIuNEnoupQs5TXQ==";

    private static final String publicKeyBase64String =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlxIbSkCQGd9qWILlnvtlUe1yNkc2OJOISuDGkHe1ag8yVfZw4d" +
                    "+DNpyvt01gy9vcwMqcmcdb9nIHa57HVkZw/mbmzAjHncgf0mUZwYWk6fjL1gP0NwZn91SuTN5OPxFFCPdh27zqI" +
                    "+HZ3XJsLCl3x/zZSj/FGdWZhJtSxTxoKhwrRGaXw/KfrkEYfEFEZk/Jt13mg0AbIjg/MeGQ6NT4CjdAcVfSlq5ytduTo0Z" +
                    "/gswjFjwpzHUp7Df2GzzusqjUtcAcI9eBQ3aBL6043m+Ws5XL8zXvoWsjM1OiEyptv7im8FvmXdbJLh" +
                    "/28euR1VTzBPUZ4vf8a6lwu68hsKsYtwIDAQAB";

    private static PrivateKey privateKey;
    private static PublicKey publicKey;

    private static final EntityId nodeId = new EntityId(0L, 0L, 3L, EntityTypeEnum.ACCOUNT.getId());
    private Signature signer;

    @Mock
    private AddressBookService addressBookService;

    @Mock
    private AddressBook currentAddressBook;

    NodeSignatureVerifier nodeSignatureVerifier;

    @BeforeAll
    static void keys() throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory kf = KeyFactory.getInstance("RSA");
        privateKey = kf
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.decodeBase64(privateKeyBase64String)));
        publicKey = kf.generatePublic(new X509EncodedKeySpec(Base64.decodeBase64(publicKeyBase64String)));
    }

    @BeforeEach
    void setup() throws GeneralSecurityException {
        nodeSignatureVerifier = new NodeSignatureVerifier(addressBookService);
        signer = Signature.getInstance("SHA384withRSA", "SunRsaSign");
        signer.initSign(privateKey);
        Map<String, PublicKey> nodeAccountIDPubKeyMap = new HashMap();
        nodeAccountIDPubKeyMap.put("0.0.3", publicKey);
        when(addressBookService.getCurrent()).thenReturn(currentAddressBook);
        when(currentAddressBook.getNodeAccountIDPubKeyMap()).thenReturn(nodeAccountIDPubKeyMap);
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

        Exception e = assertThrows(SignatureVerificationException.class, () -> nodeSignatureVerifier
                .verify(Arrays.asList(fileStreamSignature)));
        assertTrue(e.getMessage().contains("Signature verification failed for file"));
    }

    @Test
    void testInvalidMetadataSignature() throws GeneralSecurityException {

        byte[] fileHash = TestUtils.generateRandomByteArray(48);
        byte[] metadataHash = TestUtils.generateRandomByteArray(48);

        FileStreamSignature fileStreamSignature = buildFileStreamSignature(fileHash, signHash(fileHash),
                metadataHash, corruptSignature(signHash(fileHash)));

        Exception e = assertThrows(SignatureVerificationException.class, () -> nodeSignatureVerifier
                .verify(Arrays.asList(fileStreamSignature)));
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
        assertTrue(e.getMessage().contains("Require at least 1/3 signature files to reach consensus"));
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

        FileStreamSignature fileStreamSignatureNode3 = buildFileStreamSignature(fileHash, fileHashSignature,
                null, null);
        //Node 4 will not verify due to missing signature, but 1/2 verified will confirm consensus reached
        FileStreamSignature fileStreamSignatureNode4 = buildFileStreamSignature(fileHash, null,
                null, null);
        fileStreamSignatureNode4.setNodeAccountId(new EntityId(0L, 0L, 4L, EntityTypeEnum.ACCOUNT.getId()));

        nodeSignatureVerifier.verify(Arrays.asList(fileStreamSignatureNode3, fileStreamSignatureNode3));
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
        fileStreamSignature.setFile(new File(""));
        fileStreamSignature.setNodeAccountId(nodeId);
        fileStreamSignature.setSignatureType(SignatureType.SHA_384_WITH_RSA);
        return fileStreamSignature;
    }

    private byte[] corruptSignature(byte[] signature) {
        signature[0] = (byte) (signature[0] + 1);
        return signature;
    }
}
