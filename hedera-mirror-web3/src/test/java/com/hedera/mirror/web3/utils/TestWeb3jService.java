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

package com.hedera.mirror.web3.utils;

import io.reactivex.Flowable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.BatchRequest;
import org.web3j.protocol.core.BatchResponse;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthSyncing;
import org.web3j.protocol.websocket.events.Notification;

@SuppressWarnings({"rawtypes", "unchecked"})
public class TestWeb3jService implements Web3jService {

    @Override
    public <T extends Response> T send(Request request, Class<T> responseType) throws IOException {
        final var method = request.getMethod();
        return switch (method) {
            case "eth_syncing" -> (T) getEthSyncingRes();
            case "eth_getBlockByNumber" -> getEthBlockRes(responseType);
            case "net_version" -> getNetVersionRes(responseType);
            case "eth_getTransactionCount" -> getTransactionCountRes(responseType);
            case "eth_sendRawTransaction", "eth_call" -> call(request.getParams(), responseType, request);
            default -> throw new UnsupportedOperationException(request.getMethod());
        };
    }

    private <T extends Response> T getResObj(Class<T> responseType) {
        try {
            return responseType.getDeclaredConstructor().newInstance();
        } catch (InstantiationException
                | IllegalAccessException
                | NoSuchMethodException
                | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private <T extends Response> T call(List serviceParameters, Class<T> responseType, Request request) {
        T res = getResObj(responseType);
        RawTransaction transaction =
                TransactionDecoder.decode(serviceParameters.get(0).toString());
        res.setResult("Good");

        return res;
    }

    private EthSyncing getEthSyncingRes() {
        var result = new EthSyncing.Result();
        result.setSyncing(false);

        var response = new EthSyncing();
        response.setResult(result);

        return response;
    }

    private <T extends Response> T getEthBlockRes(Class<T> responseType) {
        T res = getResObj(responseType);
        final var block = new EthBlock.Block();
        block.setTimestamp(String.valueOf(System.currentTimeMillis()));
        res.setResult(block);

        return res;
    }

    private <T extends Response> T getNetVersionRes(Class<T> responseType) {
        T res = getResObj(responseType);
        res.setResult("1");

        return res;
    }

    private <T extends Response> T getTransactionCountRes(Class<T> responseType) {
        T res = getResObj(responseType);
        res.setResult("1");

        return res;
    }

    @Override
    @SneakyThrows
    public <T extends Response> CompletableFuture<T> sendAsync(Request request, Class<T> responseType) {
        return CompletableFuture.completedFuture(send(request, responseType));
    }

    @Override
    public BatchResponse sendBatch(BatchRequest batchRequest) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("sendBatch");
    }

    @Override
    public CompletableFuture<BatchResponse> sendBatchAsync(BatchRequest batchRequest)
            throws UnsupportedOperationException {
        throw new UnsupportedOperationException("sendBatchAsync");
    }

    @Override
    public <T extends Notification<?>> Flowable<T> subscribe(
            Request request, String unsubscribeMethod, Class<T> responseType) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("subscribe");
    }

    @Override
    public void close() throws IOException {
        throw new UnsupportedOperationException("Close");
    }
}
