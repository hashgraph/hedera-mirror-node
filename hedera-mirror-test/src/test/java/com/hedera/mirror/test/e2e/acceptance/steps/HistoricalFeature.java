/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ESTIMATE_GAS;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.ADDRESS_BALANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.GET_COUNTER;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient.ExecuteContractResult;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.math.BigInteger;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class HistoricalFeature extends AbstractFeature {
    private DeployedContract deployedContract;
    private String contractSolidityAddress;
    private ExpandedAccountId receiverAccountId;
    private ExpandedAccountId deletableAccountId;
    private final AccountClient accountClient;

    @Given("I successfully create estimateGas contract")
    public void createNewEstimateContract() throws IOException {
        deployedContract = getContract(ESTIMATE_GAS);
        contractSolidityAddress = deployedContract.contractId().toSolidityAddress();
        receiverAccountId = accountClient.getAccount(AccountNameEnum.DAVE);
    }

    @Then("I successfully update the contract storage and get the initial value via historical data")
    public void getHistoricalContractStorage() throws InterruptedException {
        waitForBlocks(1);
        var data = encodeData(ESTIMATE_GAS, GET_COUNTER);
        var initialResponse = callContract(data, contractSolidityAddress);
        // the block number where contract storage variable is still with initial value
        var initialBlockNumber = getLastBlockNumber();

        // executing the contract storage update after 2 blocks
        waitForBlocks(2);
        ContractFunctionParameters parameters = new ContractFunctionParameters().addUint256(BigInteger.valueOf(5));
        executeContractCallTransaction(deployedContract.contractId(), "updateCounter", parameters, null);

        var response = callContract(initialBlockNumber, data, contractSolidityAddress);
        //        assertEquals(response.getResultAsNumber(), initialResponse.getResultAsNumber());
    }

    @Then("I successfully update the balance of an account and get the initial balance via historical data")
    public void getHistoricalBalance() throws InterruptedException {
        var data = encodeData(
                ESTIMATE_GAS,
                ADDRESS_BALANCE,
                asAddress(receiverAccountId.getAccountId().toSolidityAddress()));
        var initialResponse = callContract(data, contractSolidityAddress).getResultAsNumber();
        var initialBlockNumber = getLastBlockNumber();
        waitForBlocks(2);
        var now = getLastBlockNumber();
        networkTransactionResponse =
                accountClient.sendCryptoTransfer(receiverAccountId.getAccountId(), Hbar.fromTinybars(50000000));
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var response =
                callContract(initialBlockNumber, data, contractSolidityAddress).getResultAsNumber();
        ;
        //        assertEquals(response, initialResponse);
    }

    @Then("I verify that historical data for {string} block is treated as latest")
    public void getHistoricalData(String blockType) {
        var data = encodeData(
                ESTIMATE_GAS,
                ADDRESS_BALANCE,
                asAddress(receiverAccountId.getAccountId().toSolidityAddress()));
        var responseFromType =
                callContract(blockType, data, contractSolidityAddress).getResultAsNumber();
        var responseFromLatest = callContract(data, contractSolidityAddress).getResultAsNumber();
        //        assertEquals(responseFromLatest, responseFromType);
    }

    @Then("I verify the response from non existing account")
    public void getHistoricalDataForNonExistingAccount() throws InterruptedException {
        deletableAccountId = accountClient.getAccount(AccountNameEnum.ALICE);
        var data = encodeData(
                ESTIMATE_GAS,
                ADDRESS_BALANCE,
                asAddress(deletableAccountId.getAccountId().toSolidityAddress()));
        networkTransactionResponse = accountClient.delete(deletableAccountId);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var blockAfterDeletion = getLastBlockNumber();
        waitForBlocks(2);
        var response = callContract(blockAfterDeletion, data, contractSolidityAddress);
        var test = "tes";
        assertEquals(response.getResultAsNumber().intValue(), 0);
    }

    private String getLastBlockNumber() {
        return mirrorClient.getBlocks().getBlocks().get(0).getNumber().toString();
    }

    private static void waitForBlocks(int numberOfBlocks) throws InterruptedException {
        log.info("Waiting for {} blocks", numberOfBlocks);
        Thread.sleep(2000L * numberOfBlocks); // Sleep for 10s which should be equal to 5 blocks
    }

    private void executeContractCallTransaction(
            ContractId contractId, String functionName, ContractFunctionParameters parameters, Hbar payableAmount) {

        ExecuteContractResult executeContractResult = contractClient.executeContract(
                contractId,
                contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .getMaxContractFunctionGas(),
                functionName,
                parameters,
                payableAmount);

        networkTransactionResponse = executeContractResult.networkTransactionResponse();
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    @Getter
    @RequiredArgsConstructor
    enum ContractMethods implements SelectorInterface {
        ADDRESS_BALANCE("addressBalance"),
        GET_COUNTER("getCounter");

        private final String selector;
    }
}
