/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.grpc.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.hedera.mirror.api.proto.AddressBookQuery;
import com.hedera.mirror.api.proto.ReactorNetworkServiceGrpc;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.addressbook.AddressBook;
import com.hedera.mirror.common.domain.addressbook.AddressBookEntry;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.grpc.GrpcIntegrationTest;
import com.hedera.mirror.grpc.util.ProtoUtil;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.Resource;
import java.net.InetAddress;
import java.time.Duration;
import lombok.extern.log4j.Log4j2;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Log4j2
class NetworkControllerTest extends GrpcIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(10L);
    private static final long CONSENSUS_TIMESTAMP = 1L;

    @GrpcClient("local")
    private ReactorNetworkServiceGrpc.ReactorNetworkServiceStub reactiveService;

    @Resource
    private DomainBuilder domainBuilder;

    @Test
    void missingFileId() {
        AddressBookQuery query = AddressBookQuery.newBuilder().build();
        StepVerifier.withVirtualTime(() -> reactiveService.getNodes(Mono.just(query)))
                .thenAwait(WAIT)
                .expectErrorSatisfies(t -> assertException(t, Status.Code.INVALID_ARGUMENT, "fileId: must not be null"))
                .verify(WAIT);
    }

    @Test
    void invalidFileId() {
        AddressBookQuery query = AddressBookQuery.newBuilder()
                .setFileId(FileID.newBuilder().setFileNum(-1).build())
                .build();
        StepVerifier.withVirtualTime(() -> reactiveService.getNodes(Mono.just(query)))
                .thenAwait(WAIT)
                .expectErrorSatisfies(t -> assertException(t, Status.Code.INVALID_ARGUMENT, "Invalid entity ID"))
                .verify(WAIT);
    }

    @Test
    void invalidLimit() {
        AddressBookQuery query = AddressBookQuery.newBuilder()
                .setFileId(FileID.newBuilder().build())
                .setLimit(-1)
                .build();

        StepVerifier.withVirtualTime(() -> reactiveService.getNodes(Mono.just(query)))
                .thenAwait(WAIT)
                .expectErrorSatisfies(t -> assertException(
                        t, Status.Code.INVALID_ARGUMENT, "limit: must be greater " + "than or equal to 0"))
                .verify(WAIT);
    }

    @Test
    void notFound() {
        AddressBookQuery query = AddressBookQuery.newBuilder()
                .setFileId(FileID.newBuilder().setFileNum(102L).build())
                .build();

        StepVerifier.withVirtualTime(() -> reactiveService.getNodes(Mono.just(query)))
                .thenAwait(WAIT)
                .expectErrorSatisfies(t -> assertException(t, Status.Code.NOT_FOUND, "does not exist"))
                .verify(WAIT);
    }

    @Test
    void noLimit() {
        AddressBook addressBook = addressBook();
        AddressBookEntry addressBookEntry1 = addressBookEntry();
        AddressBookEntry addressBookEntry2 = addressBookEntry();
        AddressBookQuery query = AddressBookQuery.newBuilder()
                .setFileId(FileID.newBuilder()
                        .setFileNum(addressBook.getFileId().getEntityNum())
                        .build())
                .build();

        StepVerifier.withVirtualTime(() -> reactiveService.getNodes(Mono.just(query)))
                .thenAwait(WAIT)
                .consumeNextWith(n -> assertEntry(addressBookEntry1, n))
                .consumeNextWith(n -> assertEntry(addressBookEntry2, n))
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void limitReached() {
        AddressBook addressBook = addressBook();
        AddressBookEntry addressBookEntry1 = addressBookEntry();
        addressBookEntry();
        AddressBookQuery query = AddressBookQuery.newBuilder()
                .setFileId(FileID.newBuilder()
                        .setFileNum(addressBook.getFileId().getEntityNum())
                        .build())
                .setLimit(1)
                .build();

        StepVerifier.withVirtualTime(() -> reactiveService.getNodes(Mono.just(query)))
                .thenAwait(WAIT)
                .consumeNextWith(n -> assertEntry(addressBookEntry1, n))
                .expectComplete()
                .verify(WAIT);
    }

    @SuppressWarnings("deprecation")
    @Test
    void nullFields() {
        AddressBook addressBook = addressBook();
        AddressBookEntry addressBookEntry = domainBuilder
                .addressBookEntry()
                .customize(a -> a.consensusTimestamp(CONSENSUS_TIMESTAMP)
                        .description(null)
                        .memo(null)
                        .nodeCertHash(null)
                        .publicKey(null)
                        .stake(null))
                .persist();
        AddressBookQuery query = AddressBookQuery.newBuilder()
                .setFileId(FileID.newBuilder()
                        .setFileNum(addressBook.getFileId().getEntityNum())
                        .build())
                .build();

        StepVerifier.withVirtualTime(() -> reactiveService.getNodes(Mono.just(query)))
                .thenAwait(WAIT)
                .consumeNextWith(n -> assertThat(n)
                        .isNotNull()
                        .returns("", NodeAddress::getDescription)
                        .returns(ByteString.EMPTY, NodeAddress::getMemo)
                        .returns(addressBookEntry.getNodeAccountId(), t -> EntityId.of(n.getNodeAccountId()))
                        .returns(ByteString.EMPTY, NodeAddress::getNodeCertHash)
                        .returns(addressBookEntry.getNodeId(), NodeAddress::getNodeId)
                        .returns("", NodeAddress::getRSAPubKey)
                        .returns(0L, NodeAddress::getStake))
                .expectComplete()
                .verify(WAIT);
    }

    private AddressBook addressBook() {
        return domainBuilder
                .addressBook()
                .customize(a -> a.startConsensusTimestamp(CONSENSUS_TIMESTAMP))
                .persist();
    }

    private AddressBookEntry addressBookEntry() {
        return domainBuilder
                .addressBookEntry(1)
                .customize(a -> a.consensusTimestamp(CONSENSUS_TIMESTAMP))
                .persist();
    }

    @SuppressWarnings("deprecation")
    private void assertEntry(AddressBookEntry addressBookEntry, NodeAddress nodeAddress) {
        assertThat(nodeAddress)
                .isNotNull()
                .returns(addressBookEntry.getDescription(), NodeAddress::getDescription)
                .returns(ByteString.copyFromUtf8(addressBookEntry.getMemo()), NodeAddress::getMemo)
                .returns(addressBookEntry.getNodeAccountId(), n -> EntityId.of(n.getNodeAccountId()))
                .returns(ProtoUtil.toByteString(addressBookEntry.getNodeCertHash()), NodeAddress::getNodeCertHash)
                .returns(addressBookEntry.getNodeId(), NodeAddress::getNodeId)
                .returns(addressBookEntry.getPublicKey(), NodeAddress::getRSAPubKey)
                .returns(addressBookEntry.getStake(), NodeAddress::getStake);

        var serviceEndpoint = addressBookEntry.getServiceEndpoints().iterator().next();
        ByteString ipAddress = null;
        try {
            ipAddress = ProtoUtil.toByteString(
                    InetAddress.getByName(serviceEndpoint.getIpAddressV4()).getAddress());
        } catch (Exception e) {
            // Ignore
        }

        assertThat(nodeAddress.getServiceEndpointList())
                .hasSize(1)
                .first()
                .returns(ipAddress, ServiceEndpoint::getIpAddressV4)
                .returns(serviceEndpoint.getPort(), ServiceEndpoint::getPort);
    }

    private void assertException(Throwable t, Status.Code status, String message) {
        assertThat(t).isNotNull().isInstanceOf(StatusRuntimeException.class).hasMessageContaining(message);

        StatusRuntimeException statusRuntimeException = (StatusRuntimeException) t;
        assertThat(statusRuntimeException.getStatus().getCode()).isEqualTo(status);
    }
}
