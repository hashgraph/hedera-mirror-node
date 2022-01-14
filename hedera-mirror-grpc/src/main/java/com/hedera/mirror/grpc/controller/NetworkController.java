package com.hedera.mirror.grpc.controller;

/*-
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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;
import javax.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.dao.NonTransientDataAccessResourceException;
import org.springframework.dao.TransientDataAccessException;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.hedera.mirror.api.proto.AddressBookQuery;
import com.hedera.mirror.api.proto.ReactorNetworkServiceGrpc;
import com.hedera.mirror.common.domain.addressbook.AddressBookEntry;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hedera.mirror.grpc.domain.AddressBookFilter;
import com.hedera.mirror.grpc.exception.AddressBookNotFoundException;
import com.hedera.mirror.grpc.service.NetworkService;
import com.hedera.mirror.grpc.util.ProtoUtil;

@GrpcService
@Log4j2
@RequiredArgsConstructor
public class NetworkController extends ReactorNetworkServiceGrpc.NetworkServiceImplBase {

    private static final String DB_ERROR = "Error querying the data source. Please retry later";
    private static final String OVERFLOW_ERROR = "Client lags too much behind. Please retry later";
    private static final String UNKNOWN_ERROR = "Unknown error";

    private final NetworkService networkService;

    @Override
    public Flux<NodeAddress> getNodes(Mono<AddressBookQuery> request) {
        return request.map(this::toFilter)
                .flatMapMany(networkService::getNodes)
                .map(this::toNodeAddress)
                .onErrorMap(this::mapError);
    }

    private AddressBookFilter toFilter(AddressBookQuery query) {
        var filter = AddressBookFilter.builder();

        if (query.hasFileId()) {
            filter.fileId(EntityId.of(query.getFileId()));
        }

        return filter.limit(query.getLimit()).build();
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

    private StatusRuntimeException mapError(Throwable t) {
        if (t instanceof ConstraintViolationException || t instanceof IllegalArgumentException || t instanceof InvalidEntityException) {
            return clientError(t, Status.INVALID_ARGUMENT, t.getMessage());
        } else if (Exceptions.isOverflow(t)) {
            return clientError(t, Status.DEADLINE_EXCEEDED, OVERFLOW_ERROR);
        } else if (t instanceof NonTransientDataAccessResourceException) {
            return serverError(t, Status.UNAVAILABLE, DB_ERROR);
        } else if (t instanceof AddressBookNotFoundException) {
            return clientError(t, Status.NOT_FOUND, t.getMessage());
        } else if (t instanceof TransientDataAccessException || t instanceof TimeoutException) {
            return serverError(t, Status.RESOURCE_EXHAUSTED, DB_ERROR);
        } else {
            return serverError(t, Status.UNKNOWN, UNKNOWN_ERROR);
        }
    }

    private StatusRuntimeException clientError(Throwable t, Status status, String message) {
        log.warn("Client error {} subscribing to topic: {}", t.getClass().getSimpleName(), t.getMessage());
        return status.augmentDescription(message).asRuntimeException();
    }

    private StatusRuntimeException serverError(Throwable t, Status status, String message) {
        log.error("Server error subscribing to topic: ", t);
        return status.augmentDescription(message).asRuntimeException();
    }
}
