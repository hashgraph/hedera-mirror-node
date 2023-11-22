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

import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ERC;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ESTIMATE_GAS;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ESTIMATE_PRECOMPILE;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.PRECOMPILE;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.ADDRESS_BALANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.ALLOWANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.ALLOWANCE_DIRECT;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.BALANCE_OF;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.BALANCE_OF_DIRECT;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.ERC_ALLOWANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.ERC_GET_APPROVED;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.GET_APPROVED;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.GET_APPROVED_DIRECT;
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
import com.hedera.hashgraph.sdk.NftId;
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
public class HistoricalFeature extends AbstractEstimateFeature {
    private DeployedContract deployedEstimateContract;
    private DeployedContract deployedEstimatePrecompileContract;
    private DeployedContract deployedPrecompileContract;
    private DeployedContract deployedErcContract;
    private String estimateContractSolidityAddress;
    private String estimatePrecompileContractSolidityAddress;
    private String precompileContractSolidityAddress;
    private String ercContractSolidityAddress;

    private ExpandedAccountId receiverAccountId;
    private ExpandedAccountId secondReceiverAccountId;
    private ExpandedAccountId deletableAccountId;
    private ExpandedAccountId admin;
    private final AccountClient accountClient;
    private final TokenClient tokenClient;

    @Given("I successfully create estimateGas contract")
    public void createNewEstimateContract() throws IOException {
        deployedEstimateContract = getContract(ESTIMATE_GAS);
        estimateContractSolidityAddress = deployedEstimateContract.contractId().toSolidityAddress();
    }

    @Given("I successfully create estimate precompile contract")
    public void createNewEstimatePrecompileContract() throws IOException {
        deployedEstimatePrecompileContract = getContract(ESTIMATE_PRECOMPILE);
        estimatePrecompileContractSolidityAddress =
                deployedEstimatePrecompileContract.contractId().toSolidityAddress();
    }

    @Given("I successfully create erc contract")
    public void createNewErcContract() throws IOException {
        deployedErcContract = getContract(ERC);
        ercContractSolidityAddress = deployedErcContract.contractId().toSolidityAddress();
    }

    @Given("I successfully create precompile contract")
    public void createNewPrecompileContract() throws IOException {
        deployedPrecompileContract = getContract(PRECOMPILE);
        precompileContractSolidityAddress =
                deployedPrecompileContract.contractId().toSolidityAddress();
        receiverAccountId = accountClient.getAccount(AccountNameEnum.DAVE);
        secondReceiverAccountId = accountClient.getAccount(AccountNameEnum.BOB);
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

    @Then("I verify that historical data for negative block returns bad request")
    public void getHistoricalDataForNegativeBlock() {
        var data = encodeData(
                ESTIMATE_GAS,
                ADDRESS_BALANCE,
                asAddress(receiverAccountId.getAccountId().toSolidityAddress()));
        assertEthCallReturnsBadRequest("-100", data, estimateContractSolidityAddress);
    }

    @Then("I verify that historical data for unknown block returns bad request")
    public void getHistoricalDataForUnknownBlock() {
        var data = encodeData(
                ESTIMATE_GAS,
                ADDRESS_BALANCE,
                asAddress(receiverAccountId.getAccountId().toSolidityAddress()));
        var currentBlock = getLastBlockNumber();
        assertEthCallReturnsBadRequest(currentBlock+"0", data, estimateContractSolidityAddress);
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
        // var afterSymbol = getTokenInfo(callContract(initialBlockNumber, data, estimateContractSolidityAddress));
        // assertEquals(initialInfo, afterSymbol);
    }

    @Then("I verify that historical data for {string} is returned via balanceOf")
    public void getHistoricalDataForBalanceOf(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();

        var data = encodeData(ERC, BALANCE_OF, asAddress(tokenId), asAddress(admin));
        var initialBlockNumber = getLastBlockNumber();
        var response = callContract(data, ercContractSolidityAddress);
        var initialBalance = response.getResultAsNumber();

        waitForBlocks(2);
        if (tokenName.toLowerCase().contains("fungible")) {
            networkTransactionResponse = tokenClient.mint(tokenId, 10L);
        } else {
            networkTransactionResponse = tokenClient.mint(tokenId, "TEST_metadata".getBytes());
        }
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        //        var historicalResponse = callContract(initialBlockNumber, data, ercContractSolidityAddress);
        //        var balanceOfHistorical = historicalResponse.getResultAsNumber();
        // assertEquals(initialBalance, balanceOfHistorical);
    }

    @Then("I verify that historical data for {string} is returned via balanceOf by direct call")
    public void getHistoricalDataForBalanceOfDirectCall(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();

        var data = encodeData(BALANCE_OF_DIRECT, asAddress(admin));
        var initialBlockNumber = getLastBlockNumber();
        var response = callContract(data, tokenId.toSolidityAddress());
        var initialBalance = response.getResultAsNumber();

        waitForBlocks(2);
        if (tokenName.toLowerCase().contains("fungible")) {
            networkTransactionResponse = tokenClient.mint(tokenId, 10L);
        } else {
            networkTransactionResponse = tokenClient.mint(tokenId, "TEST_metadata".getBytes());
        }
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        //        var historicalResponse = callContract(initialBlockNumber, data, tokenId.toSolidityAddress());
        //        var balanceOfHistorical = historicalResponse.getResultAsNumber();
        // assertEquals(initialBalance, balanceOfHistorical);
    }

    @Then("I verify historical data for {string} is returned for allowance")
    public void getHistoricalDataForAllowance(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(
                ESTIMATE_PRECOMPILE, ALLOWANCE, asAddress(tokenId), asAddress(admin), asAddress(receiverAccountId));
        var response = callContract(data, estimatePrecompileContractSolidityAddress);
        var initialAllowance = response.getResultAsNumber();
        waitForBlocks(2);
        networkTransactionResponse = accountClient.approveToken(tokenId, receiverAccountId.getAccountId(), 100L);
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        //        var historicalResponse = callContract(initialBlockNumber, data, estimatePrecompileContractSolidityAddress);
        //        var historicalAllowance = historicalResponse.getResultAsNumber();
        //        assertEquals(initialAllowance, historicalAllowance);
    }

    @Then("I verify historical data for {string} is returned for getApproved")
    public void getHistoricalDataForGetApproved(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
          var nftId = new NftId(tokenId, 1L);
        networkTransactionResponse = accountClient.approveNft(nftId, receiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(ESTIMATE_PRECOMPILE, GET_APPROVED, asAddress(tokenId), new BigInteger("1"));
        var response = callContract(data, estimatePrecompileContractSolidityAddress);
        var initialApprovedAddress = response.getResultAsAddress();
        waitForBlocks(2);
        networkTransactionResponse = accountClient.approveNft(nftId, secondReceiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
//        var historicalResponse = callContract(initialBlockNumber, data, estimateContractSolidityAddress);
//        var historicalApprovedAddress = historicalResponse.getResultAsAddress();
//        assertEquals(initialApprovedAddress, historicalApprovedAddress);
    }

    @Then("I verify historical data for {string} is returned for ERC allowance")
    public void getHistoricalDataForERCAllowance(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(
                ERC, ERC_ALLOWANCE, asAddress(tokenId), asAddress(admin), asAddress(receiverAccountId));
        var response = callContract(data, ercContractSolidityAddress);
        var initialAllowance = response.getResultAsNumber();
        waitForBlocks(2);
        networkTransactionResponse = accountClient.approveToken(tokenId, receiverAccountId.getAccountId(), 150L);
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        //        var historicalResponse = callContract(initialBlockNumber, data, ercContractSolidityAddress);
        //        var historicalAllowance = historicalResponse.getResultAsNumber();
        //        assertEquals(initialAllowance, historicalAllowance);
    }

    @Then("I verify historical data for {string} is returned for ERC getApproved")
    public void getHistoricalDataForERCGetApproved(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var nftId = new NftId(tokenId, 1L);
        networkTransactionResponse = accountClient.approveNft(nftId, receiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(ERC, ERC_GET_APPROVED, asAddress(tokenId), new BigInteger("1"));
        var response = callContract(data, ercContractSolidityAddress);
        var initialApprovedAddress = response.getResultAsAddress();
        waitForBlocks(2);
        networkTransactionResponse = accountClient.approveNft(nftId, secondReceiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
//        var historicalResponse = callContract(initialBlockNumber, data, ercContractSolidityAddress);
//        var historicalApprovedAddress = historicalResponse.getResultAsAddress();
//        assertEquals(initialApprovedAddress, historicalApprovedAddress);
    }

    @Then("I verify historical data for {string} is returned for allowance by direct call")
    public void getHistoricalDataForAllowanceDirectCall(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var data = encodeData(ALLOWANCE_DIRECT, asAddress(admin), asAddress(receiverAccountId));
        var initialBlockNumber = getLastBlockNumber();
        var response = callContract(data, tokenId.toSolidityAddress());
        var initialAllowance = response.getResultAsNumber();

        waitForBlocks(2);
        networkTransactionResponse = accountClient.approveToken(tokenId, receiverAccountId.getAccountId(), 200L);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var test =  callContract(data, tokenId.toSolidityAddress()).getResultAsNumber();
        //        var historicalResponse = callContract(initialBlockNumber, data, tokenId.toSolidityAddress());
        //        var historicalAllowance = historicalResponse.getResultAsNumber();
        //        assertEquals(initialAllowance, historicalAllowance);
    }

    @Then("I verify historical data for {string} is returned for getApproved direct call")
    public void getHistoricalDataForGetApprovedDirectCall(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var nftId = new NftId(tokenId, 1L);
        networkTransactionResponse = accountClient.approveNft(nftId, receiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(GET_APPROVED_DIRECT, asAddress(tokenId), new BigInteger("1"));
        var response = callContract(data, tokenId.toSolidityAddress());
        var initialApprovedAddress = response.getResultAsAddress();
        waitForBlocks(2);
        networkTransactionResponse = accountClient.approveNft(nftId, secondReceiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
//        var historicalResponse = callContract(initialBlockNumber, data, tokenId.toSolidityAddress());
//        var historicalApprovedAddress = historicalResponse.getResultAsAddress();
//        assertEquals(initialApprovedAddress, historicalApprovedAddress);
    }

    private String getLastBlockNumber() {
        return mirrorClient.getBlocks().getBlocks().get(0).getNumber().toString();
    }

    private static void waitForBlocks(int numberOfBlocks) throws InterruptedException {
        log.info("Waiting for {} blocks", numberOfBlocks);
        Thread.sleep(2000L * numberOfBlocks); // Sleep for 2s which should be equal to 1 block
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
        ALLOWANCE("allowanceExternal"),
        ALLOWANCE_DIRECT("allowance(address,address)"),
        BALANCE_OF("balanceOf"),
        BALANCE_OF_DIRECT("balanceOf(address)"),
        ERC_ALLOWANCE("allowance"),
        ERC_GET_APPROVED("getApproved"),
        GET_APPROVED("getApprovedExternal"),
        GET_APPROVED_DIRECT("getApprovedExternal(address,uint256)"),
        GET_COUNTER("getCounter"),
        GET_TOKEN_INFORMATION("getInformationForToken");

        private final String selector;
    }
}
