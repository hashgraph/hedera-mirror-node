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

import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import javax.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.addressbook.AddressBook;
import com.hedera.mirror.common.domain.addressbook.AddressBookEntry;
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
        var addressBookEntry3 = AddressBookEntry.builder().nodeId(100).nodeAccountId(entity3).build();
        var addressBookEntry4 = AddressBookEntry.builder().nodeId(200).nodeAccountId(entity4).build();
        var addressBookEntry5 = AddressBookEntry.builder().nodeId(300).nodeAccountId(entity5).build();
        when(currentAddressBook.getEntries()).thenReturn(List.of(addressBookEntry3, addressBookEntry4,
                addressBookEntry5));
        when(addressBookService.getCurrent()).thenReturn(currentAddressBook);
        when(commonDownloaderProperties.getConsensusRatio()).thenReturn(0.333f);
    }

    @Test
    void testFailedVerificationWithEmptyAddressBook() {
        when(addressBookService.getCurrent()).thenReturn(AddressBook.builder().entries(Collections.emptyList())
                .build());
        nodeStakes(3, 3, 3);
        var fileStreamSignatures = List.of(buildFileStreamSignature());

        Exception e = assertThrows(SignatureVerificationException.class, () -> consensusValidator
                .validate(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Consensus not reached for file"));
    }

    @Test
    void testVerifiedWithOneThirdNodeStakeConsensus() {
        nodeStakes(3, 3, 3);
        var fileStreamSignatures = List.of(buildFileStreamSignature());
        consensusValidator.validate(fileStreamSignatures);
    }

    @Test
    void testFailedVerificationWithLessThanOneThirdNodeStakeConsensus() {
        nodeStakes(3, 4, 3);
        var fileStreamSignatures = List.of(
                buildFileStreamSignature(),
                buildFileStreamSignature()
        );

        Exception e = assertThrows(SignatureVerificationException.class, () -> consensusValidator
                .validate(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Consensus not reached for file"));
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
                        .nodeId(200)
                        .consensusTimestamp(timestamp)
                        .stake(3L))
                .persist();

        var fileStreamSignatures = List.of(buildFileStreamSignature());

        Exception e = assertThrows(SignatureVerificationException.class, () -> consensusValidator
                .validate(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Consensus not reached for file"));
    }

    @Test
    void testVerifiedWithFullNodeStakeConsensus() {
        when(commonDownloaderProperties.getConsensusRatio()).thenReturn(1f);
        nodeStakes(3, 3, 3);

        FileStreamSignature fileStreamSignatureNode3 = buildFileStreamSignature();
        FileStreamSignature fileStreamSignatureNode4 = buildFileStreamSignature();
        fileStreamSignatureNode4.setNodeAccountId(entity4);
        fileStreamSignatureNode4.setFileHash(fileStreamSignatureNode3.getFileHash());
        FileStreamSignature fileStreamSignatureNode5 = buildFileStreamSignature();
        fileStreamSignatureNode5.setNodeAccountId(entity5);
        fileStreamSignatureNode5.setFileHash(fileStreamSignatureNode3.getFileHash());

        var fileStreamSignatures = List.of(fileStreamSignatureNode3, fileStreamSignatureNode4,
                fileStreamSignatureNode5);

        consensusValidator
                .validate(fileStreamSignatures);
    }

    @Test
    void testFailedVerificationNodeStakeConsensus() {
        nodeStakes(2, 3, 3);
        var fileStreamSignatures = List.of(buildFileStreamSignature());
        Exception e = assertThrows(SignatureVerificationException.class, () -> consensusValidator
                .validate(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Consensus not reached for file"));
    }

    @Test
    void testFailedVerificationNoSignaturesNodeStakeConsensus() {
        nodeStakes(3, 3, 3);
        Collection<FileStreamSignature> emptyList = Collections.emptyList();
        Exception e = assertThrows(SignatureVerificationException.class, () -> consensusValidator
                .validate(emptyList));
        assertTrue(e.getMessage().contains("Consensus not reached for file"));
    }

    @Test
    void testSkipNodeStakeConsensus() {
        when(commonDownloaderProperties.getConsensusRatio()).thenReturn(0f);
        nodeStakes(1, 3, 3);
        var fileStreamSignatures = List.of(buildFileStreamSignature());

        consensusValidator
                .validate(fileStreamSignatures);
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

        consensusValidator
                .validate(fileStreamSignatures);
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
        when(commonDownloaderProperties.getConsensusRatio()).thenReturn(1f);

        FileStreamSignature fileStreamSignatureNode3 = buildFileStreamSignature();
        FileStreamSignature fileStreamSignatureNode4 = buildFileStreamSignature();
        fileStreamSignatureNode4.setNodeAccountId(entity4);
        fileStreamSignatureNode4.setFileHash(fileStreamSignatureNode3.getFileHash());

        FileStreamSignature fileStreamSignatureNode5 = buildFileStreamSignature();
        fileStreamSignatureNode5.setNodeAccountId(entity5);
        fileStreamSignatureNode5.setFileHash(fileStreamSignatureNode3.getFileHash());

        var fileStreamSignatures = List.of(fileStreamSignatureNode3, fileStreamSignatureNode4,
                fileStreamSignatureNode5);

        consensusValidator
                .validate(fileStreamSignatures);
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

        Exception e = assertThrows(SignatureVerificationException.class, () -> consensusValidator
                .validate(fileStreamSignatures));
        assertTrue(e.getMessage()
                .contains("Insufficient downloaded signature file count, requires at least 0.333 to reach consensus, " +
                        "got 1 out of 4 for file"));
    }

    private void nodeStakes(long... stakes) {
        var timestamp = domainBuilder.timestamp();
        domainBuilder.nodeStake()
                .customize(n -> n
                        .nodeId(100)
                        .consensusTimestamp(timestamp)
                        .stake(stakes[0]))
                .persist();
        domainBuilder.nodeStake()
                .customize(n -> n
                        .nodeId(200)
                        .consensusTimestamp(timestamp)
                        .stake(stakes[1]))
                .persist();
        domainBuilder.nodeStake()
                .customize(n -> n
                        .nodeId(300)
                        .consensusTimestamp(timestamp)
                        .stake(stakes[2]))
                .persist();
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
