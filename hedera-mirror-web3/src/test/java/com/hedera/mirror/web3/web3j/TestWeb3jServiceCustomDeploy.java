/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.web3j;

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.service.ContractExecutionService;
import io.reactivex.Flowable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.core.BatchRequest;
import org.web3j.protocol.core.BatchResponse;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.websocket.events.Notification;

public class TestWeb3jServiceCustomDeploy extends TestWeb3jService {

    private byte[] contractRuntime;

    TestWeb3jServiceCustomDeploy(ContractExecutionService contractExecutionService, DomainBuilder domainBuilder) {
        super(contractExecutionService, domainBuilder);
    }

    @Override
    public <T extends Response> T send(Request request, Class<T> responseType) {
        return super.send(request, responseType);
    }

    @Override
    public <T extends Response> CompletableFuture<T> sendAsync(Request request, Class<T> responseType) {
        return super.sendAsync(request, responseType);
    }

    @Override
    public BatchResponse sendBatch(BatchRequest batchRequest) {
        return super.sendBatch(batchRequest);
    }

    @Override
    public CompletableFuture<BatchResponse> sendBatchAsync(BatchRequest batchRequest) {
        return super.sendBatchAsync(batchRequest);
    }

    @Override
    public <T extends Notification<?>> Flowable<T> subscribe(
            Request request, String unsubscribeMethod, Class<T> responseType) {
        return super.subscribe(request, unsubscribeMethod, responseType);
    }

    @Override
    public void close() throws IOException {
        super.close();
    }

    @Override
    public Address deployInternal(String binary) {
        final var id = domainBuilder.id();
        return toAddress(EntityId.of(id));
    }

    @Override
    protected EthSendTransaction sendTopLevelContractCreate(
            RawTransaction rawTransaction, String transactionHash, Request request) {
        final var res = new EthSendTransaction();
        var serviceParameters = serviceParametersForTopLevelContractCreate(rawTransaction.getData(), ETH_CALL, sender);
        final var mirrorNodeResult = contractExecutionService.processCall(serviceParameters);
        contractRuntime = Hex.decode(mirrorNodeResult.substring(2));
        try {
            final var contractInstance = deployInternal(mirrorNodeResult);
            res.setResult(transactionHash);
            res.setRawResponse(contractInstance.toHexString());
            res.setId(request.getId());
            res.setJsonrpc(request.getJsonrpc());
            transactionResult = contractInstance.toHexString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return res;
    }

    public void cleanupContractRuntime() {
        contractRuntime = null;
    }

    public byte[] getContractRuntime() {
        return contractRuntime;
    }
}
