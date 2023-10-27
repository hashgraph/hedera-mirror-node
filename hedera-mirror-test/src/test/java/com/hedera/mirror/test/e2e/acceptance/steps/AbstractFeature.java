/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.test.e2e.acceptance.steps;

import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.getAbiFunctionAsJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.util.Strings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient;
import com.hedera.mirror.test.e2e.acceptance.client.FileClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import com.hedera.mirror.test.e2e.acceptance.props.ContractCallRequest;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTransaction;
import com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse;
import com.hedera.mirror.test.e2e.acceptance.response.ExchangeRateResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTransactionsResponse;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;

@CustomLog
abstract class AbstractFeature {
    protected NetworkTransactionResponse networkTransactionResponse;
    protected ContractId contractId;
    private static final Map<ContractResource, DeployedContract> contractIdMap = new ConcurrentHashMap<>();

    @Autowired
    protected ContractClient contractClient;

    @Autowired
    protected FileClient fileClient;

    @Autowired
    protected MirrorNodeClient mirrorClient;

    @Autowired
    protected ObjectMapper mapper;

    protected ExchangeRateResponse exchangeRates;

    @Autowired
    private ResourceLoader resourceLoader;

    protected long calculateCreateTokenFee(double usdFee, boolean useCurrentFee) {
        if (exchangeRates == null) {
            throw new RuntimeException("Exchange rates are not initialized.");
        }
        final var fee = useCurrentFee ? exchangeRates.getCurrentRate() : exchangeRates.getNextRate();
        final double hbarPriceInCents = (double) fee.getCentEquivalent() / fee.getHbarEquivalent();
        final int usdInCents = 100;
        // create token requires 1 usd in fees
        // create token with custom fees requires 2 usd in fees
        // usdInCents / hbarPriceInCents = amount of hbars equal to 1 usd. Increment that number with 1 for safety and
        // multiply that number with 10 ^ 8 to convert hbar to tinybar
        return (long) ((usdInCents * usdFee / hbarPriceInCents + 1) * 100000000);
    }

    protected MirrorTransaction verifyMirrorTransactionsResponse(MirrorNodeClient mirrorClient, int status) {
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        MirrorTransactionsResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        List<MirrorTransaction> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        MirrorTransaction mirrorTransaction = transactions.get(0);

        if (status == HttpStatus.OK.value()) {
            assertThat(mirrorTransaction.getResult()).isEqualTo("SUCCESS");
        }

        assertThat(mirrorTransaction.getValidStartTimestamp()).isNotNull();
        assertThat(mirrorTransaction.getName()).isNotNull();
        assertThat(mirrorTransaction.getResult()).isNotNull();
        assertThat(mirrorTransaction.getConsensusTimestamp()).isNotNull();

        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);

        return mirrorTransaction;
    }

    protected DeployedContract getContract(ContractResource contractResource) throws IOException {
        synchronized (contractIdMap) {
            return contractIdMap.computeIfAbsent(contractResource, x -> {
                var resource = resourceLoader.getResource(contractResource.path);
                try (var in = resource.getInputStream()) {
                    CompiledSolidityArtifact compiledSolidityArtifact = readCompiledArtifact(in);
                    var fileId = persistContractBytes(
                            compiledSolidityArtifact.getBytecode().replaceFirst("0x", ""));
                    networkTransactionResponse = contractClient.createContract(
                            fileId,
                            contractClient
                                    .getSdkClient()
                                    .getAcceptanceTestProperties()
                                    .getFeatureProperties()
                                    .getMaxContractFunctionGas(),
                            contractResource.initialBalance == 0
                                    ? null
                                    : Hbar.fromTinybars(contractResource.initialBalance),
                            null);
                    ContractId contractId = verifyCreateContractNetworkResponse();
                    return new DeployedContract(fileId, contractId, compiledSolidityArtifact);
                } catch (IOException e) {
                    log.warn("Issue creating contract: {}, ex: {}", contractResource, e);
                    throw new RuntimeException(e);
                }
            });
        }
    }

    protected FileId persistContractBytes(String contractContents) {
        networkTransactionResponse = fileClient.createFile(new byte[] {});
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        var fileId = networkTransactionResponse.getReceipt().fileId;
        assertNotNull(fileId);
        networkTransactionResponse = fileClient.appendFile(fileId, contractContents.getBytes(StandardCharsets.UTF_8));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        return fileId;
    }

    protected ContractId verifyCreateContractNetworkResponse() {
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        var contractId = networkTransactionResponse.getReceipt().contractId;
        assertNotNull(contractId);
        return contractId;
    }

    protected record DeployedContract(
            FileId fileId, ContractId contractId, CompiledSolidityArtifact compiledSolidityArtifact) {}

    protected CompiledSolidityArtifact readCompiledArtifact(InputStream in) throws IOException {
        return mapper.readValue(in, CompiledSolidityArtifact.class);
    }

    protected ContractCallResponse callContract(String data, String contractAddress) {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(data)
                .from(contractClient.getClientAddress())
                .to(contractAddress)
                .estimate(false)
                .build();

        return mirrorClient.contractsCall(contractCallRequestBody);
    }

    protected ContractCallResponse estimateContract(String data, String contractAddress) {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(data)
                .from(contractClient.getClientAddress())
                .to(contractAddress)
                .estimate(true)
                .build();

        return mirrorClient.contractsCall(contractCallRequestBody);
    }

    protected String encodeData(ContractResource resource, SelectorInterface method, Object... args) {
        String json;
        try (var in = getResourceAsStream(resource.getPath())) {
            json = getAbiFunctionAsJsonString(readCompiledArtifact(in), method.getSelector());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Function function = Function.fromJson(json);
        return Strings.encode(function.encodeCallWithArgs(args));
    }

    protected byte[] encodeDataToByteArray(ContractResource resource, SelectorInterface method, Object... args) {
        String json;
        try (var in = getResourceAsStream(resource.getPath())) {
            json = getAbiFunctionAsJsonString(readCompiledArtifact(in), method.getSelector());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Function function = Function.fromJson(json);
        ByteBuffer byteBuffer = function.encodeCallWithArgs(args);
        return byteBuffer.array();
    }

    protected String encodeData(SelectorInterface method, Object... args) {
        return Strings.encode(new Function(method.getSelector()).encodeCallWithArgs(args));
    }

    protected InputStream getResourceAsStream(String resourcePath) throws IOException {
        return resourceLoader.getResource(resourcePath).getInputStream();
    }

    protected interface SelectorInterface {
        String getSelector();
    }

    protected interface ContractMethodInterface extends SelectorInterface {
        int getActualGas();
    }

    @RequiredArgsConstructor
    @Getter
    public enum ContractResource {
        ESTIMATE_PRECOMPILE(
                "classpath:solidity/artifacts/contracts/EstimatePrecompileContract.sol/EstimatePrecompileContract.json",
                0),
        ERC("classpath:solidity/artifacts/contracts/ERCTestContract.sol/ERCTestContract.json", 0),
        PRECOMPILE("classpath:solidity/artifacts/contracts/PrecompileTestContract.sol/PrecompileTestContract.json", 0),
        ESTIMATE_GAS(
                "classpath:solidity/artifacts/contracts/EstimateGasContract.sol/EstimateGasContract.json", 1000000),
        PARENT_CONTRACT("classpath:solidity/artifacts/contracts/Parent.sol/Parent.json", 10000000);

        private final String path;
        private final int initialBalance;

        @Override
        public String toString() {
            return "ContractResource{" + "path='" + path + '\'' + '}';
        }
    }
}
