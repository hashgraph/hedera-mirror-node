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

import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.to32BytesString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient;
import com.hedera.mirror.test.e2e.acceptance.client.FileClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import com.hedera.mirror.test.e2e.acceptance.props.ContractCallRequest;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class CallFeature extends AbstractFeature {

    private static final String HEX_REGEX = "^[0-9a-fA-F]+$";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private static DeployedContract deployedContract;
    private final ContractClient contractClient;
    private final FileClient fileClient;
    private final AccountClient accountClient;
    private final MirrorNodeClient mirrorClient;
    private final TokenClient tokenClient;
    private CompiledSolidityArtifact ercArtifacts;
    private CompiledSolidityArtifact precompileArtifacts;
    private CompiledSolidityArtifact estimateArtifacts;
    private String ercContractAddress;
    private String precompileContractAddress;
    private String estimateContractAddress;
    private ExpandedAccountId receiverAccountId;

    @Value("classpath:solidity/artifacts/contracts/ERCTestContract.sol/ERCTestContract.json")
    private Resource ercTestContract;

    @Value("classpath:solidity/artifacts/contracts/PrecompileTestContract.sol/PrecompileTestContract.json")
    private Resource precompileTestContract;

    @Value("classpath:solidity/artifacts/contracts/EstimateGasContract.sol/EstimateGasContract.json")
    private Resource estimateGasTestContract;

    public static String[] splitAddresses(String result) {
        // remove the '0x' prefix
        String strippedResult = result.substring(2);

        // split into two addresses
        String address1 = strippedResult.substring(0, 64);
        String address2 = strippedResult.substring(64);

        // remove leading zeros and add '0x' prefix back
        address1 = new BigInteger(address1, 16).toString(16);
        address2 = new BigInteger(address2, 16).toString(16);

        return new String[] {address1, address2};
    }

    @RetryAsserts
    @Then("the mirror node REST API should return status {int} for the estimate contract transaction")
    public void verifyMirrorAPIResponses(int status) {
        verifyMirrorTransactionsResponse(mirrorClient, status);
    }

    @Given("I successfully create ERC contract")
    public void createNewERCtestContract() throws IOException {
        try (var in = ercTestContract.getInputStream()) {
            ercArtifacts = MAPPER.readValue(in, CompiledSolidityArtifact.class);
        }
        deployedContract = createContract(ercArtifacts, 0);
        ercContractAddress = deployedContract.contractId().toSolidityAddress();
    }

    @Given("I successfully create Precompile contract")
    public void createNewPrecompileTestContract() throws IOException {
        try (var in = precompileTestContract.getInputStream()) {
            precompileArtifacts = MAPPER.readValue(in, CompiledSolidityArtifact.class);
        }
        deployedContract = createContract(precompileArtifacts, 0);
        precompileContractAddress = deployedContract.contractId().toSolidityAddress();
    }

    @Given("I successfully create EstimateGas contract")
    public void createNewEstimateTestContract() throws IOException {
        try (var in = estimateGasTestContract.getInputStream()) {
            estimateArtifacts = MAPPER.readValue(in, CompiledSolidityArtifact.class);
        }
        deployedContract = createContract(estimateArtifacts, 1000000);
        estimateContractAddress = deployedContract.contractId().toSolidityAddress();
        receiverAccountId = accountClient.getAccount(AccountNameEnum.BOB);
    }

    // ETHCALL-017
    @RetryAsserts
    @Then("I call function with IERC721Metadata token {string} name")
    public void ierc721MetadataTokenName(String tokenName) {
        var tokenNameEnum = TokenClient.TokenNameEnum.valueOf(tokenName);
        var tokenId = tokenClient.getToken(tokenNameEnum);
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.IERC721_TOKEN_NAME_SELECTOR.getSelector()
                        + to32BytesString(tokenId.toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(ercContractAddress)
                .estimate(false)
                .build();

        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertThat(response.getResultAsText()).isEqualTo(tokenNameEnum.getSymbol() + "_name");
    }

    // ETHCALL-018
    @RetryAsserts
    @Then("I call function with IERC721Metadata token {string} symbol")
    public void ierc721MetadataTokenSymbol(String tokenName) {
        var tokenNameEnum = TokenClient.TokenNameEnum.valueOf(tokenName);
        var tokenId = tokenClient.getToken(tokenNameEnum);
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.IERC721_TOKEN_SYMBOL_SELECTOR.getSelector()
                        + to32BytesString(tokenId.toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(ercContractAddress)
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertThat(response.getResultAsText()).isEqualTo(tokenNameEnum.getSymbol());
    }

    // ETHCALL-019
    @RetryAsserts
    @Then("I call function with IERC721Metadata token {string} totalSupply")
    public void ierc721MetadataTokenTotalSupply(String tokenName) {
        var tokenId = tokenClient.getToken(TokenClient.TokenNameEnum.valueOf(tokenName));
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.IERC721_TOKEN_TOTAL_SUPPLY_SELECTOR.getSelector()
                        + to32BytesString(tokenId.toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(ercContractAddress)
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertThat(response.getResultAsNumber()).isZero();
    }

    // ETHCALL-020
    @RetryAsserts
    @Then("I call function with IERC721 token {string} balanceOf owner")
    public void ierc721MetadataTokenBalanceOf(String tokenName) {
        var tokenId = tokenClient.getToken(TokenClient.TokenNameEnum.valueOf(tokenName));
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.IERC721_TOKEN_BALANCE_OF_SELECTOR.getSelector()
                        + to32BytesString(tokenId.toSolidityAddress())
                        + to32BytesString(contractClient.getClientAddress()))
                .from(contractClient.getClientAddress())
                .to(ercContractAddress)
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertThat(response.getResultAsNumber()).isZero();
    }

    // ETHCALL-025
    @RetryAsserts
    @Then("I call function with HederaTokenService isToken token {string}")
    public void htsIsToken(String tokenName) {
        var tokenId = tokenClient.getToken(TokenClient.TokenNameEnum.valueOf(tokenName));
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.HTS_IS_TOKEN_SELECTOR.getSelector()
                        + to32BytesString(tokenId.toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(precompileContractAddress)
                .estimate(false)
                .build();

        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertThat(response.getResultAsBoolean()).isTrue();
    }

    // ETHCALL-026
    @RetryAsserts
    @Then("I call function with HederaTokenService isFrozen token {string}, account")
    public void htsIsFrozen(String tokenName) {
        var tokenId = tokenClient.getToken(TokenClient.TokenNameEnum.valueOf(tokenName));
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.HTS_IS_FROZEN_SELECTOR.getSelector()
                        + to32BytesString(tokenId.toSolidityAddress())
                        + to32BytesString(contractClient.getClientAddress()))
                .from(contractClient.getClientAddress())
                .to(precompileContractAddress)
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertThat(response.getResultAsBoolean()).isFalse();
    }

    // ETHCALL-027
    @RetryAsserts
    @Then("I call function with HederaTokenService isKyc token {string}, account")
    public void htsIsKyc(String tokenName) {
        var tokenId = tokenClient.getToken(TokenClient.TokenNameEnum.valueOf(tokenName));
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.HTS_IS_KYC_SELECTOR.getSelector()
                        + to32BytesString(tokenId.toSolidityAddress())
                        + to32BytesString(contractClient.getClientAddress()))
                .from(contractClient.getClientAddress())
                .to(precompileContractAddress)
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertThat(response.getResultAsBoolean()).isFalse();
    }

    // ETHCALL-028
    @RetryAsserts
    @Then("I call function with HederaTokenService getTokenDefaultFreezeStatus token {string}")
    public void htsGetTokenDefaultFreezeStatus(String tokenName) {
        var tokenId = tokenClient.getToken(TokenClient.TokenNameEnum.valueOf(tokenName));
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.HTS_GET_DEFAULT_FREEZE_STATUS_SELECTOR.getSelector()
                        + to32BytesString(tokenId.toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(precompileContractAddress)
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertThat(response.getResultAsBoolean()).isFalse();
    }

    // ETHCALL-029
    @RetryAsserts
    @Then("I call function with HederaTokenService getTokenDefaultKycStatus token {string}")
    public void htsGetTokenDefaultKycStatus(String tokenName) {
        var tokenId = tokenClient.getToken(TokenClient.TokenNameEnum.valueOf(tokenName));
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.HTS_GET_TOKEN_DEFAULT_KYC_STATUS_SELECTOR.getSelector()
                        + to32BytesString(tokenId.toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(precompileContractAddress)
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertThat(response.getResultAsBoolean()).isFalse();
    }

    @Then("I call function with update and I expect return of the updated value")
    public void ethCallUpdateFunction() {
        var updateValue = "5";
        var updateCall = ContractCallRequest.builder()
                .data(ContractMethods.UPDATE_COUNTER_SELECTOR.getSelector() + to32BytesString(updateValue))
                .from(contractClient.getClientAddress())
                .to(estimateContractAddress)
                .estimate(false)
                .build();
        ContractCallResponse updateCallResponse = mirrorClient.contractsCall(updateCall);

        assertEquals(String.valueOf(updateCallResponse.getResultAsNumber()), updateValue);
    }

    @Then("I call function that makes N times state update")
    public void ethCallStateUpdateNTimesFunction() {
        String updateValue = to32BytesString("10");
        var updateStateCall = ContractCallRequest.builder()
                .data(ContractMethods.STATE_UPDATE_N_TIMES_SELECTOR.getSelector() + updateValue)
                .from(contractClient.getClientAddress())
                .to(estimateContractAddress)
                .estimate(false)
                .build();

        ContractCallResponse updateStateCallResponse = mirrorClient.contractsCall(updateStateCall);

        assertEquals(String.valueOf(updateStateCallResponse.getResultAsNumber()), "15");
    }

    @Then("I call function with nested deploy using create function")
    public void ethCallNestedDeployViaCreateFunction() {
        var deployCall = ContractCallRequest.builder()
                .data(ContractMethods.DEPLOY_NESTED_CONTRACT_CONTRACT_VIA_CREATE_SELECTOR.getSelector())
                .from(contractClient.getClientAddress())
                .to(estimateContractAddress)
                .estimate(false)
                .build();
        ContractCallResponse deployCallResponse = mirrorClient.contractsCall(deployCall);
        String[] addresses = splitAddresses(deployCallResponse.getResult());

        validateAddresses(addresses);
    }

    @Then("I call function with nested deploy using create2 function")
    public void ethCallNestedDeployViaCreate2Function() {
        var deployCall = ContractCallRequest.builder()
                .data(ContractMethods.DEPLOY_NESTED_CONTRACT_CONTRACT_VIA_CREATE2_SELECTOR.getSelector())
                .from(contractClient.getClientAddress())
                .to(estimateContractAddress)
                .estimate(false)
                .build();
        ContractCallResponse deployCallResponse = mirrorClient.contractsCall(deployCall);

        String[] addresses = splitAddresses(deployCallResponse.getResult());

        validateAddresses(addresses);
    }

    @Then("I call function with transfer that returns the balance")
    public void ethCallReentrancyCallFunction() {
        // representing the decimal number of 10000
        var transferValue = "2710";
        var transferCall = ContractCallRequest.builder()
                .data(ContractMethods.TRANSFER_SELECTOR.getSelector()
                        + to32BytesString(receiverAccountId.getAccountId().toSolidityAddress())
                        + to32BytesString(transferValue))
                .from(contractClient.getClientAddress())
                .to(estimateContractAddress)
                .estimate(false)
                .build();
        ContractCallResponse transferCallResponse = mirrorClient.contractsCall(transferCall);
        String[] balances = splitAddresses(transferCallResponse.getResult());

        // verify initial balance
        assertEquals(Integer.parseInt(balances[0], 16), 1000000);
        // verify balance after transfer of 10,000
        assertEquals(Integer.parseInt(balances[1], 16), 990000);
    }

    protected DeployedContract createContract(CompiledSolidityArtifact compiledSolidityArtifact, int initialBalance) {
        var fileId = persistContractBytes(compiledSolidityArtifact.getBytecode().replaceFirst("0x", ""));
        networkTransactionResponse = contractClient.createContract(
                fileId,
                contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .getMaxContractFunctionGas(),
                initialBalance == 0 ? null : Hbar.fromTinybars(initialBalance),
                null);
        var contractId = verifyCreateContractNetworkResponse();
        return new DeployedContract(fileId, contractId, compiledSolidityArtifact);
    }

    private ContractId verifyCreateContractNetworkResponse() {
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        var contractId = networkTransactionResponse.getReceipt().contractId;
        assertNotNull(contractId);
        return contractId;
    }

    private FileId persistContractBytes(String contractContents) {
        // rely on SDK chunking feature to upload larger files
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

    private void validateAddresses(String[] addresses) {
        assertNotEquals(addresses[0], addresses[1]);
        assertTrue(addresses[0].matches(HEX_REGEX));
        assertTrue(addresses[1].matches(HEX_REGEX));
    }

    @Getter
    @RequiredArgsConstructor
    private enum ContractMethods {
        IERC721_TOKEN_NAME_SELECTOR("b1ec803c"),
        IERC721_TOKEN_SYMBOL_SELECTOR("f6b486b7"),
        IERC721_TOKEN_TOTAL_SUPPLY_SELECTOR("3cd9a3ab"),
        IERC721_TOKEN_BALANCE_OF_SELECTOR("063c7dcf"),
        HTS_IS_TOKEN_SELECTOR("bff9834f"),
        HTS_IS_FROZEN_SELECTOR("565ca6fa"),
        HTS_IS_KYC_SELECTOR("bc2fb00e"),
        HTS_GET_DEFAULT_FREEZE_STATUS_SELECTOR("319a8723"),
        HTS_GET_TOKEN_DEFAULT_KYC_STATUS_SELECTOR("fd4d1c26"),
        UPDATE_COUNTER_SELECTOR("c648049d"),
        STATE_UPDATE_N_TIMES_SELECTOR("5256b99d"),
        DEPLOY_NESTED_CONTRACT_CONTRACT_VIA_CREATE_SELECTOR("cdb9c283"),
        DEPLOY_NESTED_CONTRACT_CONTRACT_VIA_CREATE2_SELECTOR("ef043d57"),
        TRANSFER_SELECTOR("39a92ada");
        private final String selector;
    }

    private record DeployedContract(
            FileId fileId, ContractId contractId, CompiledSolidityArtifact compiledSolidityArtifact) {}
}
