/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.GET_CUSTOM_FEES;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.GET_DEFAULT_FREEZE_STATUS;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.GET_DEFAULT_KYC_STATUS;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.GET_EXPIRY_INFO;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.GET_FUNGIBLE_INFO;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.GET_NFT_INFO;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.GET_TOKEN_INFORMATION;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.GET_TOKEN_KEY_INFO;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.GET_TYPE;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.IS_APPROVED_FOR_ALL;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.IS_FROZEN;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.IS_KYC_GRANTED;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.IS_TOKEN;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.OWNER_OF;
import static com.hedera.mirror.test.e2e.acceptance.steps.HistoricalFeature.ContractMethods.UPDATE_TOKEN_KEY_INFO;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hashgraph.sdk.AccountUpdateTransaction;
import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.CustomFee;
import com.hedera.hashgraph.sdk.CustomFixedFee;
import com.hedera.hashgraph.sdk.CustomFractionalFee;
import com.hedera.hashgraph.sdk.CustomRoyaltyFee;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TokenUpdateTransaction;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeoutException;
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

    @RetryAsserts
    @Given("I verify the estimate precompile contract bytecode is deployed")
    public void contractDeployed() {
        var response = mirrorClient.getContractInfo(estimatePrecompileContractSolidityAddress);
        assertThat(response.getBytecode()).isNotBlank();
        assertThat(response.getRuntimeBytecode()).isNotBlank();
    }

    @Given("I create fungible token")
    public void createFungibleToken() {
        var tokenResponse = tokenClient.getToken(TokenNameEnum.FUNGIBLE_HISTORICAL);
        if (tokenResponse.response() != null) {
            networkTransactionResponse = tokenResponse.response();
            verifyMirrorTransactionsResponse(mirrorClient, 200);
        }
    }

    @Given("I create non-fungible token")
    public void createNonFungibleToken() {
        var tokenResponse = tokenClient.getToken(TokenNameEnum.NFT_HISTORICAL);
        if (tokenResponse.response() != null) {
            networkTransactionResponse = tokenResponse.response();
            verifyMirrorTransactionsResponse(mirrorClient, 200);
        }
    }

    @Then("I successfully update the contract storage and get the initial value via historical data")
    public void getHistoricalContractStorage() throws InterruptedException {
        var data = encodeData(ESTIMATE_GAS, GET_COUNTER);
        var initialResponse = callContract(data, estimateContractSolidityAddress);
        // the block number where contract storage variable is still with initial value
        var initialBlockNumber = getLastBlockNumber();

        // executing the contract storage update after 2 blocks
        waitForNextBlock();
        ContractFunctionParameters parameters = new ContractFunctionParameters().addUint256(BigInteger.valueOf(5));
        var maxGas = contractClient
                .getSdkClient()
                .getAcceptanceTestProperties()
                .getFeatureProperties()
                .getMaxContractFunctionGas();
        contractClient.executeContract(
                deployedEstimateContract.contractId(), maxGas, "updateCounter", parameters, null);

        var response = callContract(initialBlockNumber, data, estimateContractSolidityAddress);
        assertEquals(initialResponse.getResultAsNumber(), response.getResultAsNumber());
    }

    @Then("I successfully update the balance of an account and get the initial balance via historical data")
    public void getHistoricalBalance() throws InterruptedException {
        var data = encodeData(
                ESTIMATE_GAS,
                ADDRESS_BALANCE,
                asAddress(receiverAccountId.getAccountId().toSolidityAddress()));
        networkTransactionResponse =
                accountClient.sendCryptoTransfer(receiverAccountId.getAccountId(), Hbar.fromTinybars(50000000), null);
        var initialResponse =
                callContract(data, estimateContractSolidityAddress).getResultAsNumber();
        var initialBlockNumber = getLastBlockNumber();
        waitForNextBlock();
        networkTransactionResponse =
                accountClient.sendCryptoTransfer(receiverAccountId.getAccountId(), Hbar.fromTinybars(50000000), null);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var response = callContract(initialBlockNumber, data, estimateContractSolidityAddress)
                .getResultAsNumber();
        assertEquals(initialResponse, response);
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
        assertEthCallReturnsBadRequest(currentBlock + "0", data, estimateContractSolidityAddress);
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
        deletableAccountId = accountClient.getAccount(AccountNameEnum.DELETABLE);
        var data = encodeData(
                ESTIMATE_GAS,
                ADDRESS_BALANCE,
                asAddress(deletableAccountId.getAccountId().toSolidityAddress()));
        var initialResponse =
                callContract(data, estimateContractSolidityAddress).getResultAsNumber();
        var initialBlock = getLastBlockNumber();
        waitForNextBlock();
        networkTransactionResponse = accountClient.delete(deletableAccountId);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var response = callContract(initialBlock, data, estimateContractSolidityAddress);
        assertEquals(initialResponse.intValue(), response.getResultAsNumber().intValue());
    }

    @Then("I verify that historical data for {string} is returned via getTokenInfo")
    public void getHistoricalDataForTokenSymbol(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();

        var data = encodeData(PRECOMPILE, GET_TOKEN_INFORMATION, asAddress(tokenId));
        var initialBlockNumber = getLastBlockNumber();
        var response = callContract(data, precompileContractSolidityAddress);

        waitForNextBlock();

        networkTransactionResponse = tokenClient.updateToken(tokenId, admin);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(initialBlockNumber, data, precompileContractSolidityAddress);
        assertEquals(response, historicalResponse);
    }

    @Then("I verify that historical data for {string} is returned via getTokenInfo when doing burn")
    public void getHistoricalDataForTokenInfoWhenDoingBurn(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();

        var data = encodeData(PRECOMPILE, GET_TOKEN_INFORMATION, asAddress(tokenId));
        var initialBlockNumber = getLastBlockNumber();
        var response = callContract(data, precompileContractSolidityAddress);

        waitForNextBlock();

        networkTransactionResponse = tokenClient.burnFungible(tokenId, 5L);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(initialBlockNumber, data, precompileContractSolidityAddress);
        assertEquals(response, historicalResponse);
    }

    @Then("I verify that historical data for {string} is returned via getTokenInfo when doing mint")
    public void getHistoricalDataForTokenInfoWhenDoingMint(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();

        var data = encodeData(PRECOMPILE, GET_TOKEN_INFORMATION, asAddress(tokenId));
        var initialBlockNumber = getLastBlockNumber();
        var response = callContract(data, precompileContractSolidityAddress);

        waitForNextBlock();

        networkTransactionResponse = tokenClient.mint(tokenId, 5L);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(initialBlockNumber, data, precompileContractSolidityAddress);
        assertEquals(response, historicalResponse);
    }

    @Then("I mint new nft for {string}")
    public void mintNft(String tokenName) {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        networkTransactionResponse = tokenClient.mint(tokenId, "TEST_metadata".getBytes());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    @Then("I associate {string}")
    public void associateTokens(String tokenName) {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        networkTransactionResponse = tokenClient.associate(receiverAccountId, tokenId);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    @Then("I grant KYC to {string} to receiver account")
    public void grantKyc(String tokenName) {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        networkTransactionResponse = tokenClient.grantKyc(tokenId, receiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    @Then("I verify that historical data for {string} is returned via balanceOf")
    public void getHistoricalDataForBalanceOf(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var data = encodeData(ERC, BALANCE_OF, asAddress(tokenId), asAddress(admin));
        var initialBlockNumber = getLastBlockNumber();
        var response = callContract(data, ercContractSolidityAddress);
        var initialBalance = response.getResultAsNumber();

        waitForNextBlock();
        if (tokenName.toLowerCase().contains("fungible")) {
            networkTransactionResponse = tokenClient.transferFungibleToken(
                    tokenId,
                    tokenClient.getSdkClient().getExpandedOperatorAccountId(),
                    receiverAccountId.getAccountId(),
                    receiverAccountId.getPrivateKey(),
                    10L);
        } else {
            tokenClient.mint(tokenId, "TEST_metadata".getBytes());
            networkTransactionResponse = tokenClient.transferNonFungibleToken(
                    tokenId, admin, receiverAccountId.getAccountId(), List.of(2L), receiverAccountId.getPrivateKey());
        }
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        var historicalResponse = callContract(initialBlockNumber, data, ercContractSolidityAddress);
        var balanceOfHistorical = historicalResponse.getResultAsNumber();
        assertEquals(initialBalance, balanceOfHistorical);
    }

    @Then("I verify that historical data for {string} is returned via balanceOf by direct call")
    public void getHistoricalDataForBalanceOfDirectCall(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();

        var data = encodeData(BALANCE_OF_DIRECT, asAddress(admin));
        var initialBlockNumber = getLastBlockNumber();
        var response = callContract(data, tokenId.toSolidityAddress());
        var initialBalance = response.getResultAsNumber();

        waitForNextBlock();
        if (tokenName.toLowerCase().contains("fungible")) {
            networkTransactionResponse = tokenClient.mint(tokenId, 10L);
        } else {
            networkTransactionResponse = tokenClient.mint(tokenId, "TEST_metadata".getBytes());
        }
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        var historicalResponse = callContract(initialBlockNumber, data, tokenId.toSolidityAddress());
        var balanceOfHistorical = historicalResponse.getResultAsNumber();
        assertEquals(initialBalance, balanceOfHistorical);
    }

    @Then("I verify that historical data for {string} is returned via balanceOf when doing burn")
    public void getHistoricalDataForBalanceOfWhenBurning(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var data = encodeData(ERC, BALANCE_OF, asAddress(tokenId), asAddress(admin));
        var initialBlockNumber = getLastBlockNumber();
        var response = callContract(data, ercContractSolidityAddress);
        var initialBalance = response.getResultAsNumber();

        waitForNextBlock();
        if (tokenName.toLowerCase().contains("fungible")) {
            tokenClient.burnFungible(tokenId, 5L);
        } else {
            tokenClient.burnNonFungible(tokenId, 3L);
        }
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(initialBlockNumber, data, ercContractSolidityAddress);
        var balanceOfHistorical = historicalResponse.getResultAsNumber();
        assertEquals(initialBalance, balanceOfHistorical);
    }

    @Then("I verify that historical data for {string} is returned via balanceOf when doing wipe")
    public void getHistoricalDataForBalanceOfWhenWiping(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var data = encodeData(ERC, BALANCE_OF, asAddress(tokenId), asAddress(receiverAccountId));
        var initialBlockNumber = getLastBlockNumber();
        var response = callContract(data, ercContractSolidityAddress);
        var initialBalance = response.getResultAsNumber();

        waitForNextBlock();
        if (tokenName.toLowerCase().contains("fungible")) {
            tokenClient.wipeFungible(tokenId, 1L, receiverAccountId);
        } else {
            tokenClient.wipeNonFungible(tokenId, 2L, receiverAccountId);
        }
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(initialBlockNumber, data, ercContractSolidityAddress);
        var balanceOfHistorical = historicalResponse.getResultAsNumber();
        assertEquals(initialBalance, balanceOfHistorical);
    }

    @Then("I verify historical data for {string} is returned for allowance")
    public void getHistoricalDataForAllowance(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(
                ESTIMATE_PRECOMPILE, ALLOWANCE, asAddress(tokenId), asAddress(admin), asAddress(receiverAccountId));
        var response = callContract(data, estimatePrecompileContractSolidityAddress);
        var initialAllowance = response.getResultAsNumber();
        waitForNextBlock();
        networkTransactionResponse = accountClient.approveToken(tokenId, receiverAccountId.getAccountId(), 100L);
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        var historicalResponse = callContract(initialBlockNumber, data, estimatePrecompileContractSolidityAddress);
        var historicalAllowance = historicalResponse.getResultAsNumber();
        assertEquals(initialAllowance, historicalAllowance);
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
        waitForNextBlock();
        networkTransactionResponse = accountClient.approveNft(nftId, secondReceiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(initialBlockNumber, data, estimatePrecompileContractSolidityAddress);
        var historicalApprovedAddress = historicalResponse.getResultAsAddress();
        assertEquals(initialApprovedAddress, historicalApprovedAddress);
    }

    @Then("I verify historical data for {string} is returned for ERC allowance")
    public void getHistoricalDataForERCAllowance(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(ERC, ERC_ALLOWANCE, asAddress(tokenId), asAddress(admin), asAddress(receiverAccountId));
        var response = callContract(data, ercContractSolidityAddress);
        var initialAllowance = response.getResultAsNumber();
        waitForNextBlock();
        networkTransactionResponse = accountClient.approveToken(tokenId, receiverAccountId.getAccountId(), 150L);
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        var historicalResponse = callContract(initialBlockNumber, data, ercContractSolidityAddress);
        var historicalAllowance = historicalResponse.getResultAsNumber();
        assertEquals(initialAllowance, historicalAllowance);
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
        waitForNextBlock();
        networkTransactionResponse = accountClient.approveNft(nftId, secondReceiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(initialBlockNumber, data, ercContractSolidityAddress);
        var historicalApprovedAddress = historicalResponse.getResultAsAddress();
        assertEquals(initialApprovedAddress, historicalApprovedAddress);
    }

    @Then("I verify historical data for {string} is returned for allowance by direct call")
    public void getHistoricalDataForAllowanceDirectCall(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var data = encodeData(ALLOWANCE_DIRECT, asAddress(admin), asAddress(receiverAccountId));
        var initialBlockNumber = getLastBlockNumber();
        var response = callContract(data, tokenId.toSolidityAddress());
        var initialAllowance = response.getResultAsNumber();

        waitForNextBlock();
        networkTransactionResponse = accountClient.approveToken(tokenId, receiverAccountId.getAccountId(), 200L);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(initialBlockNumber, data, tokenId.toSolidityAddress());
        var historicalAllowance = historicalResponse.getResultAsNumber();
        assertEquals(initialAllowance, historicalAllowance);
    }

    @Then("I verify historical data for {string} is returned for getApproved direct call")
    public void getHistoricalDataForGetApprovedDirectCall(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var nftId = new NftId(tokenId, 1L);
        networkTransactionResponse = accountClient.approveNft(nftId, receiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(GET_APPROVED_DIRECT, new BigInteger("1"));
        var response = callContract(data, tokenId.toSolidityAddress());
        var initialApprovedAddress = response.getResultAsAddress();
        waitForNextBlock();
        networkTransactionResponse = accountClient.approveNft(nftId, secondReceiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(initialBlockNumber, data, tokenId.toSolidityAddress());
        var historicalApprovedAddress = historicalResponse.getResultAsAddress();
        assertEquals(initialApprovedAddress, historicalApprovedAddress);
    }

    @Then("I verify historical data for {string} is returned for isApprovedForAll")
    public void getHistoricalDataForIsApprovedForAll(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                IS_APPROVED_FOR_ALL,
                asAddress(tokenId.toSolidityAddress()),
                asAddress(admin),
                asAddress(receiverAccountId));
        var response = callContract(data, estimatePrecompileContractSolidityAddress);
        var initialResult = response.getResultAsAddress();
        waitForNextBlock();
        networkTransactionResponse = accountClient.approveNftAllSerials(tokenId, receiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(initialBlockNumber, data, estimatePrecompileContractSolidityAddress);
        var historicalResult = historicalResponse.getResultAsAddress();
        assertEquals(initialResult, historicalResult);
    }

    @Then("I verify historical data for {string} is returned for ownerOf")
    public void getHistoricalDataForOwnerOf(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(ERC, OWNER_OF, asAddress(tokenId.toSolidityAddress()), new BigInteger("1"));
        var response = callContract(data, ercContractSolidityAddress);
        var initialOwner = response.getResultAsAddress();
        waitForNextBlock();
        networkTransactionResponse = tokenClient.transferNonFungibleToken(
                tokenId, admin, receiverAccountId.getAccountId(), List.of(1L), receiverAccountId.getPrivateKey());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(initialBlockNumber, data, ercContractSolidityAddress);
        var historicalOwner = historicalResponse.getResultAsAddress();
        assertEquals(initialOwner, historicalOwner);
    }

    @Then("I verify historical data for {string} is returned for isFrozen")
    public void getHistoricalDataForIsFrozenFungible(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var data = encodeData(
                PRECOMPILE,
                IS_FROZEN,
                asAddress(tokenId.toSolidityAddress()),
                asAddress(receiverAccountId.getAccountId().toSolidityAddress()));
        var response = callContract(data, precompileContractSolidityAddress);
        var initialBlockNumber = getLastBlockNumber();
        var initialFreezeStatus = response.getResultAsBoolean();
        waitForNextBlock();
        networkTransactionResponse = tokenClient.freeze(tokenId, receiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalFreezeStatus = callContract(initialBlockNumber, data, precompileContractSolidityAddress)
                .getResultAsBoolean();
        assertEquals(initialFreezeStatus, historicalFreezeStatus);

        // reverting to old state
        networkTransactionResponse = tokenClient.unfreeze(tokenId, receiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    @Then("I verify historical data for {string} is returned for getFungibleTokenInfo")
    public void getHistoricalDataForFungibleTokenInfo(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(PRECOMPILE, GET_FUNGIBLE_INFO, asAddress(tokenId.toSolidityAddress()));
        var response = callContract(data, precompileContractSolidityAddress);
        waitForNextBlock();
        networkTransactionResponse = tokenClient.updateToken(tokenId, admin);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(initialBlockNumber, data, precompileContractSolidityAddress);
        assertEquals(response, historicalResponse);
    }

    @Then("I verify historical data for {string} is returned for getFungibleTokenInfo when doing burn")
    public void getHistoricalDataForFungibleTokenInfoWhenMinting(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(PRECOMPILE, GET_FUNGIBLE_INFO, asAddress(tokenId.toSolidityAddress()));
        var response = callContract(data, precompileContractSolidityAddress);
        waitForNextBlock();
        networkTransactionResponse = tokenClient.burnFungible(tokenId, 10L);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(initialBlockNumber, data, precompileContractSolidityAddress);
        assertEquals(response, historicalResponse);
    }

    @Then("I verify historical data for {string} is returned for getNonFungibleInfo")
    public void getHistoricalDataForNonFungibleTokenInfo(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(PRECOMPILE, GET_NFT_INFO, asAddress(tokenId.toSolidityAddress()), 5L);
        var response = callContract(data, precompileContractSolidityAddress);
        waitForNextBlock();
        networkTransactionResponse = tokenClient.burnNonFungible(tokenId, 5);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(initialBlockNumber, data, precompileContractSolidityAddress);
        assertEquals(response, historicalResponse);
    }

    @And("I update the token and account keys for {string}")
    public void updateAccountAndTokenKeys(String tokenName)
            throws PrecheckStatusException, TimeoutException, ReceiptStatusException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var keyList = KeyList.of(admin.getPublicKey(), deployedEstimatePrecompileContract.contractId())
                .setThreshold(1);
        new AccountUpdateTransaction()
                .setAccountId(admin.getAccountId())
                .setKey(keyList)
                .freezeWith(accountClient.getClient())
                .sign(admin.getPrivateKey())
                .execute(accountClient.getClient());
        var tokenUpdate = new TokenUpdateTransaction()
                .setTokenId(tokenId)
                .setSupplyKey(keyList)
                .setAdminKey(keyList)
                .freezeWith(accountClient.getClient())
                .sign(admin.getPrivateKey())
                .execute(accountClient.getClient());
        networkTransactionResponse = new NetworkTransactionResponse(
                tokenUpdate.transactionId, tokenUpdate.getReceipt(accountClient.getClient()));
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    @Then("I verify historical data for {string} is returned for getTokenKey")
    public void getHistoricalDataForGetTokenKey(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        BigInteger[] tokenKeyValues = {
            new BigInteger("1"), new BigInteger("2"), new BigInteger("4"), new BigInteger("8"), new BigInteger("16")
        };
        var currentResponses = new HashMap<BigInteger, Object>();

        // Collect current responses
        for (BigInteger keyValue : tokenKeyValues) {
            var data = encodeData(
                    ESTIMATE_PRECOMPILE, GET_TOKEN_KEY_INFO, asAddress(tokenId.toSolidityAddress()), keyValue);
            currentResponses.put(keyValue, callContract(data, estimatePrecompileContractSolidityAddress));
        }

        waitForNextBlock();

        // Perform update
        var updateData = encodeDataToByteArray(ESTIMATE_PRECOMPILE, UPDATE_TOKEN_KEY_INFO, asAddress(tokenId));
        var result = contractClient.executeContract(
                deployedEstimatePrecompileContract.contractId(), 3000000, "updateTokenInfoExternal", updateData, null);
        networkTransactionResponse = result.networkTransactionResponse();
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        // Collect and compare historical responses
        for (BigInteger keyValue : tokenKeyValues) {
            var data = encodeData(
                    ESTIMATE_PRECOMPILE, GET_TOKEN_KEY_INFO, asAddress(tokenId.toSolidityAddress()), keyValue);
            var historicalResponse = callContract(initialBlockNumber, data, estimatePrecompileContractSolidityAddress);
            assertEquals(currentResponses.get(keyValue), historicalResponse);
        }
    }

    @Then("I verify historical data for {string} is returned for isKyc")
    public void getHistoricalDataForIsKyc(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(PRECOMPILE, IS_KYC_GRANTED, asAddress(tokenId), asAddress(receiverAccountId));
        var response = callContract(data, precompileContractSolidityAddress);
        waitForNextBlock();
        networkTransactionResponse = tokenClient.revokeKyc(tokenId, receiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(initialBlockNumber, data, precompileContractSolidityAddress);
        assertEquals(response, historicalResponse);

        // reverting the old state
        networkTransactionResponse = tokenClient.grantKyc(tokenId, receiverAccountId.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    @Then("I verify historical data for {string} is returned for isToken")
    public void getHistoricalDataForIsToken(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(ESTIMATE_PRECOMPILE, IS_TOKEN, asAddress(tokenId));
        var response = callContract(data, estimatePrecompileContractSolidityAddress);
        waitForNextBlock();
        networkTransactionResponse = tokenClient.delete(admin, tokenId);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var historicalResponse = callContract(initialBlockNumber, data, estimatePrecompileContractSolidityAddress);
        assertEquals(response, historicalResponse);

        // recreate the deleted token
        var tokenResponse = tokenClient.getToken(TokenNameEnum.valueOf(tokenName));
        if (tokenResponse.response() != null) {
            networkTransactionResponse = tokenResponse.response();
            verifyMirrorTransactionsResponse(mirrorClient, 200);
        }
    }

    @RetryAsserts
    @Then("I verify historical data for {string} is returned for getCustomFees")
    public void getHistoricalDataForCustomFees(String tokenName) throws InterruptedException {
        CustomFixedFee customFixedFee = new CustomFixedFee();
        customFixedFee.setAmount(10);
        customFixedFee.setFeeCollectorAccountId(admin.getAccountId());

        CustomFractionalFee customFractionalFee = new CustomFractionalFee();
        customFractionalFee.setFeeCollectorAccountId(admin.getAccountId());
        customFractionalFee.setNumerator(1);
        customFractionalFee.setDenominator(10);

        CustomRoyaltyFee customRoyaltyFee = new CustomRoyaltyFee();
        customRoyaltyFee.setNumerator(5);
        customRoyaltyFee.setDenominator(10);
        customRoyaltyFee.setFallbackFee(new CustomFixedFee().setHbarAmount(new Hbar(1)));
        customRoyaltyFee.setFeeCollectorAccountId(admin.getAccountId());

        List<CustomFee> listOfFees;
        if (tokenName.contains("FUNGIBLE")) {
            listOfFees = List.of(customFixedFee, customFractionalFee);
        } else {
            listOfFees = List.of(customFixedFee, customRoyaltyFee);
        }

        // deleting the created token without custom fees
        var oldTokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        networkTransactionResponse = tokenClient.delete(admin, oldTokenId);
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        // creating new token with custom fees and getting response from eth_call
        var tokenId = tokenClient
                .getToken(TokenNameEnum.valueOf(tokenName), listOfFees)
                .tokenId();
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(PRECOMPILE, GET_CUSTOM_FEES, asAddress(tokenId));
        var response = callContract(data, precompileContractSolidityAddress);

        waitForNextBlock();

        // deleting the token with the custom fees and creating it again without custom fees
        networkTransactionResponse = tokenClient.delete(admin, tokenId);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        var tokenResponse = tokenClient.getToken(TokenNameEnum.valueOf(tokenName));
        if (tokenResponse.response() != null) {
            networkTransactionResponse = tokenResponse.response();
            verifyMirrorTransactionsResponse(mirrorClient, 200);
        }

        var historicalResponse = callContract(initialBlockNumber, data, precompileContractSolidityAddress);
        assertEquals(response, historicalResponse);
    }

    @Then("I verify historical data for {string} is returned for getTokenDefaultFreezeStatus")
    public void getHistoricalDataForDefaultFreezeStatus(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(PRECOMPILE, GET_DEFAULT_FREEZE_STATUS, asAddress(tokenId));
        var response = callContract(data, precompileContractSolidityAddress);

        waitForNextBlock();

        // deleting the token and getting historical data
        var historicalResponse = deleteTokenAndGetHistoricalResponse(
                tokenId, initialBlockNumber, data, precompileContractSolidityAddress);
        assertEquals(response, historicalResponse);

        // creating the deleted token within the test
        var tokenResponse = tokenClient.getToken(TokenNameEnum.valueOf(tokenName));
        if (tokenResponse.response() != null) {
            networkTransactionResponse = tokenResponse.response();
            verifyMirrorTransactionsResponse(mirrorClient, 200);
        }
    }

    @Then("I verify historical data for {string} is returned for getTokenDefaultKYCStatus")
    public void getHistoricalDataForDefaultKYCStatus(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(PRECOMPILE, GET_DEFAULT_KYC_STATUS, asAddress(tokenId));
        var response = callContract(data, precompileContractSolidityAddress);

        waitForNextBlock();

        // deleting the token and getting historical data
        var historicalResponse = deleteTokenAndGetHistoricalResponse(
                tokenId, initialBlockNumber, data, precompileContractSolidityAddress);
        assertEquals(response, historicalResponse);

        // creating the deleted token within the test
        var tokenResponse = tokenClient.getToken(TokenNameEnum.valueOf(tokenName));
        if (tokenResponse.response() != null) {
            networkTransactionResponse = tokenResponse.response();
            verifyMirrorTransactionsResponse(mirrorClient, 200);
        }
    }

    @Then("I verify historical data for {string} is returned for getTokenType")
    public void getHistoricalDataForGetTokenType(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(PRECOMPILE, GET_TYPE, asAddress(tokenId));
        var response = callContract(data, precompileContractSolidityAddress);

        waitForNextBlock();

        // deleting the token and getting historical data
        var historicalResponse = deleteTokenAndGetHistoricalResponse(
                tokenId, initialBlockNumber, data, precompileContractSolidityAddress);
        assertEquals(response, historicalResponse);

        // creating the deleted token within the test
        var tokenResponse = tokenClient.getToken(TokenNameEnum.valueOf(tokenName));
        if (tokenResponse.response() != null) {
            networkTransactionResponse = tokenResponse.response();
            verifyMirrorTransactionsResponse(mirrorClient, 200);
        }
    }

    @Then("I verify historical data for {string} is returned for getTokenExpiryInfo")
    public void getHistoricalDataForGetTokenExpiryInfo(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        var initialBlockNumber = getLastBlockNumber();
        var data = encodeData(ESTIMATE_PRECOMPILE, GET_EXPIRY_INFO, asAddress(tokenId));
        var response = callContract(data, estimatePrecompileContractSolidityAddress);

        waitForNextBlock();

        // deleting the token and getting historical data
        var historicalResponse = deleteTokenAndGetHistoricalResponse(
                tokenId, initialBlockNumber, data, estimatePrecompileContractSolidityAddress);
        assertEquals(response, historicalResponse);

        // creating the deleted token within the test
        var tokenResponse = tokenClient.getToken(TokenNameEnum.valueOf(tokenName));
        if (tokenResponse.response() != null) {
            networkTransactionResponse = tokenResponse.response();
            verifyMirrorTransactionsResponse(mirrorClient, 200);
        }
    }

    @Then("I verify historical data for {string} in invalid block returns bad request")
    public void getHistoricalDataNonExistingToken(String tokenName) throws InterruptedException {
        var tokenId = tokenClient.getToken(TokenNameEnum.valueOf(tokenName)).tokenId();
        networkTransactionResponse = tokenClient.delete(admin, tokenId);
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        // creating the new token and getting the creation block
        var initialBlockNumber = getLastBlockNumber();
        var previousBlock = String.valueOf(Long.parseLong(initialBlockNumber) - 5);
        var tokenResponse = tokenClient.getToken(TokenNameEnum.valueOf(tokenName));
        if (tokenResponse.response() != null) {
            networkTransactionResponse = tokenResponse.response();
            verifyMirrorTransactionsResponse(mirrorClient, 200);
        }

        var data = encodeData(PRECOMPILE, GET_TOKEN_INFORMATION, asAddress(tokenResponse.tokenId()));
        assertEthCallReturnsBadRequest(previousBlock, data, estimatePrecompileContractSolidityAddress);
    }

    private String getLastBlockNumber() {
        return mirrorClient.getBlocks().getBlocks().getFirst().getNumber().toString();
    }

    private void waitForNextBlock() throws InterruptedException {
        int currentBlockNumber = Integer.parseInt(getLastBlockNumber());
        int waitTime = 250;
        int maxCycles = 8;
        int totalTimeWaited = 0;

        for (int i = 0; i < maxCycles; i++) {
            Thread.sleep(waitTime);
            totalTimeWaited += waitTime;

            int newBlockNumber = Integer.parseInt(getLastBlockNumber());
            if (newBlockNumber > currentBlockNumber) {
                break;
            }
        }
        log.info("Found new block after waiting for {} ms", totalTimeWaited);
    }

    private ContractCallResponse deleteTokenAndGetHistoricalResponse(
            TokenId tokenId, String blockNumber, String data, String solidityAddress) {
        networkTransactionResponse = tokenClient.delete(admin, tokenId);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        return callContract(blockNumber, data, solidityAddress);
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
        GET_APPROVED_DIRECT("getApproved(uint256)"),
        GET_CUSTOM_FEES("getCustomFeesForToken"),
        GET_DEFAULT_FREEZE_STATUS("getTokenDefaultFreeze"),
        GET_DEFAULT_KYC_STATUS("getTokenDefaultKyc"),
        GET_EXPIRY_INFO("getTokenExpiryInfoExternal"),
        GET_FUNGIBLE_INFO("getInformationForFungibleToken"),
        GET_NFT_INFO("getInformationForNonFungibleToken"),
        GET_TOKEN_KEY_INFO("getTokenKeyExternal"),
        GET_TYPE("getType"),
        IS_APPROVED_FOR_ALL("isApprovedForAllExternal"),
        IS_KYC_GRANTED("isKycGranted"),
        IS_TOKEN("isTokenExternal"),
        OWNER_OF("getOwnerOf"),
        IS_FROZEN("isTokenFrozen"),
        GET_COUNTER("counter"),
        GET_COUNTER2("counter2"),
        GET_SALT("salt"),
        GET_TOKEN_INFORMATION("getInformationForToken"),
        UPDATE_TOKEN_KEY_INFO("updateTokenKeysExternal");

        private final String selector;
    }
}
