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
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.hedera.hashgraph.sdk.*;
import com.hedera.hashgraph.sdk.proto.TokenFreezeStatus;
import com.hedera.hashgraph.sdk.proto.TokenKycStatus;
import com.hedera.mirror.test.e2e.acceptance.client.*;
import com.hedera.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import com.hedera.mirror.test.e2e.acceptance.props.ContractCallRequest;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class CallFeature extends AbstractFeature {

    private static final int INITIAL_SUPPLY = 1_000_000;
    private static final int MAX_SUPPLY = 1;
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private static DeployedContract deployedContract;
    private final ContractClient contractClient;
    private final FileClient fileClient;
    private final MirrorNodeClient mirrorClient;
    private final TokenClient tokenClient;
    private final List<TokenId> tokenIds = new ArrayList<>();
    private final String fungibleTokenName = "fungible";
    private final String nonFungibleTokenName = "non_fungible_name";
    private CompiledSolidityArtifact ercArtifacts;
    private CompiledSolidityArtifact precompileArtifacts;
    private String ercContractAddress;
    private String precompileContractAddress;
    @Value("classpath:solidity/artifacts/contracts/ERCTestContract.sol/ERCTestContract.json")
    private Resource ercTestContract;

    @Value("classpath:solidity/artifacts/contracts/PrecompileTestContract.sol/PrecompileTestContract.json")
    private Resource precompileTestContract;

    @Given("I successfully create ERC contract")
    public void createNewERCtestContract() throws IOException {
        try (var in = ercTestContract.getInputStream()) {
            ercArtifacts = MAPPER.readValue(in, CompiledSolidityArtifact.class);
            createContract(ercArtifacts);
        }
        deployedContract = createContract(ercArtifacts);
        ercContractAddress = deployedContract.contractId().toSolidityAddress();
    }

    @Given("I successfully create Precompile contract")
    public void createNewPrecompileTestContract() throws IOException {
        try (var in = precompileTestContract.getInputStream()) {
            precompileArtifacts = MAPPER.readValue(in, CompiledSolidityArtifact.class);
            createContract(precompileArtifacts);
        }
        deployedContract = createContract(precompileArtifacts);
        precompileContractAddress = deployedContract.contractId().toSolidityAddress();
    }

    @Before
    public void createNewFungibleToken() {
        createNewToken(
                fungibleTokenName,
                TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE,
                TokenType.FUNGIBLE_COMMON,
                TokenSupplyType.INFINITE,
                Collections.emptyList());
    }

    @Before
    public void createNewNonFungibleToken() {
        createNewToken(
                nonFungibleTokenName,
                TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE,
                TokenType.NON_FUNGIBLE_UNIQUE,
                TokenSupplyType.INFINITE,
                Collections.emptyList());
    }

    //ETHCALL-017
    @RetryAsserts
    @Then("I call function with IERC721Metadata token name")
    public void IERC721MetadataTokenName() {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.IERC721_TOKEN_NAME_SELECTOR.getSelector() + to32BytesString(
                        tokenIds.get(1).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(ercContractAddress)
                .estimate(false)
                .build();

        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertThat(response.getResultAsText()).isEqualTo(nonFungibleTokenName + "_name");
    }

    //ETHCALL-018
    @RetryAsserts
    @Then("I call function with IERC721Metadata token symbol")
    public void IERC721MetadataTokenSymbol() {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.IERC721_TOKEN_SYMBOL_SELECTOR.getSelector() + to32BytesString(
                        tokenIds.get(1).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(ercContractAddress)
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertThat(response.getResultAsText()).isEqualTo(nonFungibleTokenName);
    }

    //ETHCALL-019
    @RetryAsserts
    @Then("I call function with IERC721Metadata token totalSupply")
    public void IERC721MetadataTokenTotalSupply() {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.IERC721_TOKEN_TOTAL_SUPPLY_SELECTOR.getSelector() + to32BytesString(
                        tokenIds.get(1).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(ercContractAddress)
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertThat(response.getResultAsNumber()).isZero();
    }

    //ETHCALL-020
    @RetryAsserts
    @Then("I call function with IERC721 token balanceOf owner")
    public void IERC721MetadataTokenBalanceOf() {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.IERC721_TOKEN_BALANCE_OF_SELECTOR.getSelector()
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString(contractClient.getClientAddress()))
                .from(contractClient.getClientAddress())
                .to(ercContractAddress)
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertThat(response.getResultAsNumber()).isZero();
    }

    //ETHCALL-025
    @RetryAsserts
    @Then("I call function with HederaTokenService isToken token")
    public void HTSIsToken() {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.HTS_IS_TOKEN_SELECTOR.getSelector()
                        + to32BytesString(tokenIds.get(0).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(precompileContractAddress)
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertThat(response.getResultAsBoolean()).isTrue();
    }

    //ETHCALL-026
    @RetryAsserts
    @Then("I call function with HederaTokenService isFrozen token, account")
    public void HTSIsFrozen() {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.HTS_IS_FROZEN_SELECTOR.getSelector()
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(contractClient.getClientAddress()))
                .from(contractClient.getClientAddress())
                .to(precompileContractAddress)
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertThat(response.getResultAsBoolean()).isFalse();
    }

    //ETHCALL-027
    @RetryAsserts
    @Then("I call function with HederaTokenService isKyc token, account")
    public void HTSIsKyc() {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.HTS_IS_KYC_SELECTOR.getSelector()
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(contractClient.getClientAddress()))
                .from(contractClient.getClientAddress())
                .to(precompileContractAddress)
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertThat(response.getResultAsBoolean()).isFalse();
    }

    //ETHCALL-028
    @RetryAsserts
    @Then("I call function with HederaTokenService getTokenDefaultFreezeStatus token")
    public void HTSgetTokenDefaultFreezeStatus() {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.HTS_GET_DEFAULT_FREEZE_STATUS_SELECTOR.getSelector()
                        + to32BytesString(tokenIds.get(0).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(precompileContractAddress)
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertThat(response.getResultAsBoolean()).isFalse();
    }

    //ETHCALL-029
    @RetryAsserts
    @Then("I call function with HederaTokenService getTokenDefaultKycStatus token")
    public void HTSgetTokenDefaultKycStatus() {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.HTS_GET_TOKEN_DEFAULT_KYC_STATUS_SELECTOR.getSelector()
                        + to32BytesString(tokenIds.get(0).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(precompileContractAddress)
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertThat(response.getResultAsBoolean()).isFalse();
    }

    private DeployedContract createContract(CompiledSolidityArtifact compiledSolidityArtifact) {
        var fileId = persistContractBytes(compiledSolidityArtifact.getBytecode().replaceFirst("0x", ""));
        networkTransactionResponse = contractClient.createContract(fileId,
                contractClient.getSdkClient().getAcceptanceTestProperties().getFeatureProperties()
                        .getMaxContractFunctionGas(),
                null,
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
        networkTransactionResponse = fileClient.createFile(new byte[]{});
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        var fileId = networkTransactionResponse.getReceipt().fileId;
        assertNotNull(fileId);

        networkTransactionResponse = fileClient.appendFile(fileId, contractContents.getBytes(StandardCharsets.UTF_8));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        return fileId;
    }

    private TokenId createNewToken(
            String symbol,
            int freezeStatus,
            int kycStatus,
            TokenType tokenType,
            TokenSupplyType tokenSupplyType,
            List<CustomFee> customFees) {
        ExpandedAccountId admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        networkTransactionResponse = tokenClient.createToken(
                admin,
                symbol,
                freezeStatus,
                kycStatus,
                admin,
                INITIAL_SUPPLY,
                tokenSupplyType,
                MAX_SUPPLY,
                tokenType,
                customFees);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        TokenId tokenId = networkTransactionResponse.getReceipt().tokenId;
        assertNotNull(tokenId);
        tokenIds.add(tokenId);

        return tokenId;
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
        HTS_GET_TOKEN_DEFAULT_KYC_STATUS_SELECTOR("fd4d1c26");
        private final String selector;
    }

    private record DeployedContract(FileId fileId, ContractId contractId,
                                    CompiledSolidityArtifact compiledSolidityArtifact) {
    }
}