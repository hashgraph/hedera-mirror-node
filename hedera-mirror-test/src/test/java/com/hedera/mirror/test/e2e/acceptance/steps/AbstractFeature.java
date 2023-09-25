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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient;
import com.hedera.mirror.test.e2e.acceptance.client.FileClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTransaction;
import com.hedera.mirror.test.e2e.acceptance.response.ExchangeRateResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTransactionsResponse;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;

@CustomLog
abstract class AbstractFeature {
    protected NetworkTransactionResponse networkTransactionResponse;
    protected ContractId contractId;

    @Autowired
    protected ContractClient contractClient;

    @Autowired
    protected FileClient fileClient;

    @Autowired
    protected ObjectMapper mapper;

    protected ExchangeRateResponse exchangeRates;

    protected long calculateCreateTokenFee(double usdFee, boolean useCurrentFee) {
        if (exchangeRates == null) {
            throw new RuntimeException("Exchange rates are not initialized.");
        }
        final var fee = useCurrentFee ? exchangeRates.getCurrentRate() : exchangeRates.getNextRate();
        final long hbarPriceInCents = fee.getCentEquivalent() / fee.getHbarEquivalent();
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

    protected DeployedContract createContract(Resource resource, int initialBalance) throws IOException {
        try (var in = resource.getInputStream()) {
            CompiledSolidityArtifact compiledSolidityArtifact = readCompiledArtifact(in);
            var fileId =
                    persistContractBytes(compiledSolidityArtifact.getBytecode().replaceFirst("0x", ""));
            networkTransactionResponse = contractClient.createContract(
                    fileId,
                    contractClient
                            .getSdkClient()
                            .getAcceptanceTestProperties()
                            .getFeatureProperties()
                            .getMaxContractFunctionGas(),
                    initialBalance == 0 ? null : Hbar.fromTinybars(initialBalance),
                    null);
            contractId = verifyCreateContractNetworkResponse();
            return new DeployedContract(fileId, contractId, compiledSolidityArtifact);
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
}
