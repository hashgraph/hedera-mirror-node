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
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.PRECOMPILE;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.ADDRESS_BALANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.GET_COUNTER;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.GET_TOKEN_INFORMATION;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asAddress;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.getAbiFunctionAsJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.util.FastHex;
import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient.ExecuteContractResult;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse;
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
    private DeployedContract deployedEstimateContract;
    private String estimateContractSolidityAddress;
    private DeployedContract deployedPrecompileContract;
    private String precompileContractSolidityAddress;
    private ExpandedAccountId receiverAccountId;
    private ExpandedAccountId deletableAccountId;
    private ExpandedAccountId admin;
    private final AccountClient accountClient;
    private TokenId fungibleKycTokenId;
    private TokenId nonFungibleTokenId;
    private final TokenClient tokenClient;

    @Given("I successfully create estimateGas contract")
    public void createNewEstimateContract() throws IOException {
        deployedEstimateContract = getContract(ESTIMATE_GAS);
        estimateContractSolidityAddress = deployedEstimateContract.contractId().toSolidityAddress();
        receiverAccountId = accountClient.getAccount(AccountNameEnum.DAVE);
    }

    @Given("I successfully create precompile contract")
    public void createNewPrecompileContract() throws IOException {
        deployedPrecompileContract = getContract(PRECOMPILE);
        precompileContractSolidityAddress =
                deployedPrecompileContract.contractId().toSolidityAddress();
        receiverAccountId = accountClient.getAccount(AccountNameEnum.DAVE);
        admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
    }

    @Then("the mirror node REST API should return status {int} for the contracts creation")
    public void verifyMirrorAPIResponse(int status) {
        if (networkTransactionResponse != null) {
            verifyMirrorTransactionsResponse(mirrorClient, status);
        }
    }

    @Given("I create {string} token")
    public void createFungibleToken(String token) {
        var tokenResponse = tokenClient.getToken(TokenNameEnum.valueOf(token));
        if (tokenResponse.response() != null) {
            networkTransactionResponse = tokenResponse.response();
            verifyMirrorTransactionsResponse(mirrorClient, 200);
        }
    }

    @Then("I successfully update the contract storage and get the initial value via historical data")
    public void getHistoricalContractStorage() throws InterruptedException {
        waitForBlocks(1);
        var data = encodeData(ESTIMATE_GAS, GET_COUNTER);
        var initialResponse = callContract(data, estimateContractSolidityAddress);
        // the block number where contract storage variable is still with initial value
        var initialBlockNumber = getLastBlockNumber();

        // executing the contract storage update after 2 blocks
        waitForBlocks(2);
        ContractFunctionParameters parameters = new ContractFunctionParameters().addUint256(BigInteger.valueOf(5));
        executeContractCallTransaction(deployedEstimateContract.contractId(), "updateCounter", parameters, null);

        var response = callContract(initialBlockNumber, data, estimateContractSolidityAddress);
        //        assertEquals(response.getResultAsNumber(), initialResponse.getResultAsNumber());
    }

    @Then("I successfully update the balance of an account and get the initial balance via historical data")
    public void getHistoricalBalance() throws InterruptedException {
        var data = encodeData(
                ESTIMATE_GAS,
                ADDRESS_BALANCE,
                asAddress(receiverAccountId.getAccountId().toSolidityAddress()));
        var initialResponse =
                callContract(data, estimateContractSolidityAddress).getResultAsNumber();
        var initialBlockNumber = getLastBlockNumber();
        waitForBlocks(2);
        var now = getLastBlockNumber();
        networkTransactionResponse =
                accountClient.sendCryptoTransfer(receiverAccountId.getAccountId(), Hbar.fromTinybars(50000000));
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var response = callContract(initialBlockNumber, data, estimateContractSolidityAddress)
                .getResultAsNumber();
        //        assertEquals(response, initialResponse);
    }

    @Then("I verify that historical data for {string} block is treated as latest")
    public void getHistoricalData(String blockType) {
        var data = encodeData(
                ESTIMATE_GAS,
                ADDRESS_BALANCE,
                asAddress(receiverAccountId.getAccountId().toSolidityAddress()));
        var responseFromType =
                callContract(blockType, data, estimateContractSolidityAddress).getResultAsNumber();
        var responseFromLatest =
                callContract(data, estimateContractSolidityAddress).getResultAsNumber();
        assertEquals(responseFromLatest, responseFromType);
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
        var response = callContract(blockAfterDeletion, data, estimateContractSolidityAddress);
        var test = "tes";
        assertEquals(response.getResultAsNumber().intValue(), 0);
    }

    @Then("I verify that historical data for {string} is returned via getTokenInfo")
    public void getHistoricalDataForTokenSymbol(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();

        var data = encodeData(PRECOMPILE, GET_TOKEN_INFORMATION, asAddress(tokenId));
        log.info("data:{}, contract:{}", data, precompileContractSolidityAddress);
        log.info("data:{}, contract:{}", data, precompileContractSolidityAddress);
        log.info("data:{}, contract:{}", data, precompileContractSolidityAddress);
        log.info("data:{}, contract:{}", data, precompileContractSolidityAddress);
        log.info("data:{}, contract:{}", data, precompileContractSolidityAddress);
        log.info("data:{}, contract:{}", data, precompileContractSolidityAddress);
        var initialBlockNumber = getLastBlockNumber();
        var response = callContract(data, precompileContractSolidityAddress);
        var initialInfo = getTokenInfo(response);

        waitForBlocks(2);

        networkTransactionResponse = tokenClient.updateToken(tokenId, admin);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        // var check = getTokenInfo(callContract(data, precompileContractSolidityAddress)); //delete
        // var test = "test"; // delete
        // var afterSymbol = getTokenInfo(callContract(initialBlockNumber, data, estimateContractSolidityAddress));
        // assertEquals(initialInfo, afterSymbol);
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

    private Tuple getTokenInfo(ContractCallResponse response) {
        String abiFunctionAsJsonString;
        try (var in = getResourceAsStream(PRECOMPILE.getPath())) {
            abiFunctionAsJsonString =
                    getAbiFunctionAsJsonString(readCompiledArtifact(in), GET_TOKEN_INFORMATION.selector);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Function function = Function.fromJson(abiFunctionAsJsonString);
        Tuple result = function.decodeReturn(FastHex.decode(response.getResult().replace("0x", "")));

        assertThat(result).isNotEmpty();

        Tuple tokenInfo = result.get(0);
        Tuple token = tokenInfo.get(0);
        boolean deleted = tokenInfo.get(2);
        boolean defaultKycStatus = tokenInfo.get(3);
        boolean pauseStatus = tokenInfo.get(4);

        assertFalse(token.isEmpty());
        assertFalse(deleted);
        assertFalse(defaultKycStatus);
        assertFalse(pauseStatus);

        return tokenInfo;
    }

    private String getTokenSymbol(ContractCallResponse response) {
        var tokenInfo = getTokenInfo(response);
        Tuple nestedTuple = tokenInfo.get(0);
        return nestedTuple.get(1);
    }

    @Getter
    @RequiredArgsConstructor
    enum ContractMethods implements SelectorInterface {
        ADDRESS_BALANCE("addressBalance"),
        GET_COUNTER("getCounter"),
        GET_TOKEN_INFORMATION("getInformationForToken");

        private final String selector;
    }
}
