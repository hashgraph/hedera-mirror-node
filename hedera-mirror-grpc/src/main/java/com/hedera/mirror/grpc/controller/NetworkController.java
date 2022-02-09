package com.hedera.mirror.grpc.controller;

/*-
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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import java.net.InetAddress;
import java.net.UnknownHostException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.devh.boot.grpc.server.service.GrpcService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.hedera.mirror.api.proto.AddressBookQuery;
import com.hedera.mirror.api.proto.ReactorNetworkServiceGrpc;
import com.hedera.mirror.common.domain.addressbook.AddressBookEntry;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.grpc.domain.AddressBookFilter;
import com.hedera.mirror.grpc.service.NetworkService;
import com.hedera.mirror.grpc.util.ProtoUtil;

@GrpcService
@Log4j2
@RequiredArgsConstructor
public class NetworkController extends ReactorNetworkServiceGrpc.NetworkServiceImplBase {

    private final NetworkService networkService;

    @Override
    public Flux<NodeAddress> getNodes(Mono<AddressBookQuery> request) {
        return request.map(this::toFilter)
                .flatMapMany(networkService::getNodes)
                .map(this::toNodeAddress)
                .onErrorMap(ProtoUtil::toStatusRuntimeException);
    }

    private AddressBookFilter toFilter(AddressBookQuery query) {
        var filter = AddressBookFilter.builder()
                .limit(query.getLimit());

        if (query.hasFileId()) {
            filter.fileId(EntityId.of(query.getFileId()));
        }

        return filter.build();
    }

    @SuppressWarnings("deprecation")
    private NodeAddress toNodeAddress(AddressBookEntry addressBookEntry) {
        var nodeAddress = NodeAddress.newBuilder()
                .setNodeAccountId(ProtoUtil.toAccountID(addressBookEntry.getNodeAccountId()))
                .setNodeId(addressBookEntry.getNodeId());

        if (addressBookEntry.getDescription() != null) {
            nodeAddress.setDescription(addressBookEntry.getDescription());
        }

        if (addressBookEntry.getMemo() != null) {
            nodeAddress.setMemo(ByteString.copyFromUtf8(addressBookEntry.getMemo()));
        }

        if (addressBookEntry.getNodeCertHash() != null) {
            nodeAddress.setNodeCertHash(ProtoUtil.toByteString(addressBookEntry.getNodeCertHash()));
        }

        if (addressBookEntry.getPublicKey() != null) {
            nodeAddress.setRSAPubKey(addressBookEntry.getPublicKey());
        }

        if (addressBookEntry.getStake() != null) {
            nodeAddress.setStake(addressBookEntry.getStake());
        }

        for (var s : addressBookEntry.getServiceEndpoints()) {
            try {
                var ipAddressV4 = InetAddress.getByName(s.getIpAddressV4()).getAddress();
                var serviceEndpoint = ServiceEndpoint.newBuilder()
                        .setIpAddressV4(ProtoUtil.toByteString(ipAddressV4))
                        .setPort(s.getPort())
                        .build();
                nodeAddress.addServiceEndpoint(serviceEndpoint);
            } catch (UnknownHostException e) {
                // Shouldn't occur since we never pass hostnames to InetAddress.getByName()
                log.warn("Unable to convert IP address to byte array", e.getMessage());
            }
        }

        return nodeAddress.build();
    }
}
