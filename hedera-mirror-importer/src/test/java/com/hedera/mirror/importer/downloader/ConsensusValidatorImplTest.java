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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import javax.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    private static final EntityId nodeId = new EntityId(0L, 0L, 3L, EntityType.ACCOUNT);

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
        when(addressBookService.getCurrent()).thenReturn(currentAddressBook);
        when(commonDownloaderProperties.getConsensusRatio()).thenReturn(0.333f);
    }

    @Test
    void testVerifiedWithOneThirdNodeStakeConsensus() {
        var timestamp = domainBuilder.timestamp();
        domainBuilder.nodeStake()
                .customize(n -> n
                        .nodeId(EntityId.of(0L, 0L, 3L, EntityType.ACCOUNT).getId())
                        .consensusTimestamp(timestamp)
                        .stake(3L))
                .persist();
        domainBuilder.nodeStake()
                .customize(n -> n
                        .nodeId(EntityId.of(0L, 0L, 4L, EntityType.ACCOUNT).getId())
                        .consensusTimestamp(timestamp)
                        .stake(3L))
                .persist();
        domainBuilder.nodeStake()
                .customize(n -> n
                        .nodeId(EntityId.of(0L, 0L, 5L, EntityType.ACCOUNT).getId())
                        .consensusTimestamp(timestamp)
                        .stake(3L))
                .persist();

        var fileStreamSignatures = Arrays.asList(buildFileStreamSignature());

        consensusValidator.validate(fileStreamSignatures);
    }

    @Test
    void testVerifiedWithFullNodeStakeConsensus() {
        when(commonDownloaderProperties.getConsensusRatio()).thenReturn(1f);

        var timestamp = domainBuilder.timestamp();
        domainBuilder.nodeStake()
                .customize(n -> n
                        .nodeId(EntityId.of(0L, 0L, 3L, EntityType.ACCOUNT).getId())
                        .consensusTimestamp(timestamp)
                        .stake(3L))
                .persist();
        domainBuilder.nodeStake()
                .customize(n -> n
                        .nodeId(EntityId.of(0L, 0L, 4L, EntityType.ACCOUNT).getId())
                        .consensusTimestamp(timestamp)
                        .stake(3L))
                .persist();
        domainBuilder.nodeStake()
                .customize(n -> n
                        .nodeId(EntityId.of(0L, 0L, 5L, EntityType.ACCOUNT).getId())
                        .consensusTimestamp(timestamp)
                        .stake(3L))
                .persist();

        FileStreamSignature fileStreamSignatureNode3 = buildFileStreamSignature();

        FileStreamSignature fileStreamSignatureNode4 = buildFileStreamSignature();
        fileStreamSignatureNode4.setNodeAccountId(new EntityId(0L, 0L, 4L, EntityType.ACCOUNT));

        FileStreamSignature fileStreamSignatureNode5 = buildFileStreamSignature();
        fileStreamSignatureNode5.setNodeAccountId(new EntityId(0L, 0L, 5L, EntityType.ACCOUNT));

        consensusValidator
                .validate(Arrays.asList(fileStreamSignatureNode3, fileStreamSignatureNode4, fileStreamSignatureNode5));
    }

    @Test
    void testFailedVerificationNodeStakeConsensus() {
        var timestamp = domainBuilder.timestamp();
        domainBuilder.nodeStake()
                .customize(n -> n
                        .nodeId(EntityId.of(0L, 0L, 3L, EntityType.ACCOUNT).getId())
                        .consensusTimestamp(timestamp)
                        .stake(2L))
                .persist();
        domainBuilder.nodeStake()
                .customize(n -> n
                        .nodeId(EntityId.of(0L, 0L, 4L, EntityType.ACCOUNT).getId())
                        .consensusTimestamp(timestamp)
                        .stake(3L))
                .persist();
        domainBuilder.nodeStake()
                .customize(n -> n
                        .nodeId(EntityId.of(0L, 0L, 5L, EntityType.ACCOUNT).getId())
                        .consensusTimestamp(timestamp)
                        .stake(3L))
                .persist();

        var fileStreamSignatures = Arrays.asList(buildFileStreamSignature());

        Exception e = assertThrows(SignatureVerificationException.class, () -> consensusValidator
                .validate(fileStreamSignatures));
        assertTrue(e.getMessage().contains("Consensus not reached for file"));
    }

    @Test
    void testFailedVerificationNoSignaturesNodeStakeConsensus() {
        var timestamp = domainBuilder.timestamp();
        domainBuilder.nodeStake()
                .customize(n -> n
                        .nodeId(EntityId.of(0L, 0L, 3L, EntityType.ACCOUNT).getId())
                        .consensusTimestamp(timestamp)
                        .stake(3L))
                .persist();
        domainBuilder.nodeStake()
                .customize(n -> n
                        .nodeId(EntityId.of(0L, 0L, 4L, EntityType.ACCOUNT).getId())
                        .consensusTimestamp(timestamp)
                        .stake(3L))
                .persist();
        domainBuilder.nodeStake()
                .customize(n -> n
                        .nodeId(EntityId.of(0L, 0L, 5L, EntityType.ACCOUNT).getId())
                        .consensusTimestamp(timestamp)
                        .stake(3L))
                .persist();

        Exception e = assertThrows(SignatureVerificationException.class, () -> consensusValidator
                .validate(Collections.emptyList()));
        assertTrue(e.getMessage().contains("Consensus not reached for file"));
    }

    @Test
    void testSkipNodeStakeConsensus() {
        when(commonDownloaderProperties.getConsensusRatio()).thenReturn(0f);

        var timestamp = domainBuilder.timestamp();
        domainBuilder.nodeStake()
                .customize(n -> n
                        .nodeId(EntityId.of(0L, 0L, 3L, EntityType.ACCOUNT).getId())
                        .consensusTimestamp(timestamp)
                        .stake(1L))
                .persist();
        domainBuilder.nodeStake()
                .customize(n -> n
                        .nodeId(EntityId.of(0L, 0L, 4L, EntityType.ACCOUNT).getId())
                        .consensusTimestamp(timestamp)
                        .stake(3L))
                .persist();
        domainBuilder.nodeStake()
                .customize(n -> n
                        .nodeId(EntityId.of(0L, 0L, 5L, EntityType.ACCOUNT).getId())
                        .consensusTimestamp(timestamp)
                        .stake(3L))
                .persist();

        consensusValidator
                .validate(Arrays.asList(buildFileStreamSignature()));
    }

    @Test
    void testSignaturesVerifiedWithOneThirdConsensusWithMissingSignatures() {
        FileStreamSignature fileStreamSignatureNode3 = buildFileStreamSignature();

        //Node 4 and 5 will not verify due to missing signature, but 1/3 verified will confirm consensus reached
        FileStreamSignature fileStreamSignatureNode4 = buildFileStreamSignature();
        fileStreamSignatureNode4.setNodeAccountId(new EntityId(0L, 0L, 4L, EntityType.ACCOUNT));
        fileStreamSignatureNode4.setStatus(FileStreamSignature.SignatureStatus.DOWNLOADED);

        FileStreamSignature fileStreamSignatureNode5 = buildFileStreamSignature();
        fileStreamSignatureNode5.setNodeAccountId(new EntityId(0L, 0L, 5L, EntityType.ACCOUNT));
        fileStreamSignatureNode5.setStatus(FileStreamSignature.SignatureStatus.DOWNLOADED);

        consensusValidator
                .validate(Arrays.asList(fileStreamSignatureNode3, fileStreamSignatureNode4, fileStreamSignatureNode5));
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
        fileStreamSignatureNode4.setNodeAccountId(new EntityId(0L, 0L, 4L, EntityType.ACCOUNT));

        FileStreamSignature fileStreamSignatureNode5 = buildFileStreamSignature();
        fileStreamSignatureNode5.setNodeAccountId(new EntityId(0L, 0L, 5L, EntityType.ACCOUNT));

        consensusValidator
                .validate(Arrays.asList(fileStreamSignatureNode3, fileStreamSignatureNode4, fileStreamSignatureNode5));
    }

    @Test
    void testFailedVerificationSignatureConsensus() {
        FileStreamSignature fileStreamSignatureNode3 = buildFileStreamSignature();

        FileStreamSignature fileStreamSignatureNode4 = buildFileStreamSignature();
        fileStreamSignatureNode4.setNodeAccountId(new EntityId(0L, 0L, 4L, EntityType.ACCOUNT));
        fileStreamSignatureNode4.setStatus(FileStreamSignature.SignatureStatus.DOWNLOADED);

        FileStreamSignature fileStreamSignatureNode5 = buildFileStreamSignature();
        fileStreamSignatureNode5.setNodeAccountId(new EntityId(0L, 0L, 5L, EntityType.ACCOUNT));
        fileStreamSignatureNode5.setStatus(FileStreamSignature.SignatureStatus.DOWNLOADED);

        FileStreamSignature fileStreamSignatureNode6 = buildFileStreamSignature();
        fileStreamSignatureNode6.setNodeAccountId(new EntityId(0L, 0L, 6L, EntityType.ACCOUNT));
        fileStreamSignatureNode6.setStatus(FileStreamSignature.SignatureStatus.DOWNLOADED);

        Exception e = assertThrows(SignatureVerificationException.class, () -> consensusValidator
                .validate(Arrays.asList(fileStreamSignatureNode3, fileStreamSignatureNode4, fileStreamSignatureNode5,
                        fileStreamSignatureNode6)));

        assertTrue(e.getMessage().contains("Insufficient signature file count, requires at least 0.333 to reach " +
                "consensus, got 1 out of 4 for file"));
    }

    private FileStreamSignature buildFileStreamSignature() {
        FileStreamSignature fileStreamSignature = new FileStreamSignature();
        fileStreamSignature.setFileHash(domainBuilder.bytes(256));
        fileStreamSignature.setFilename("");
        fileStreamSignature.setNodeAccountId(nodeId);
        fileStreamSignature.setSignatureType(SignatureType.SHA_384_WITH_RSA);
        fileStreamSignature.setStatus(FileStreamSignature.SignatureStatus.VERIFIED);
        fileStreamSignature.setStreamType(StreamType.RECORD);
        return fileStreamSignature;
    }
}
