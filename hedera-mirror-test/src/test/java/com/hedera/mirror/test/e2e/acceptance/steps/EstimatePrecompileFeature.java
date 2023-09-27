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

import static com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.FUNGIBLE;
import static com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.FUNGIBLE_KYC_UNFROZEN;
import static com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.NFT;
import static com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.NFT_KYC_UNFROZEN;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.TokenTransferListBuilder;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.accountAmount;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asAddress;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asAddressArray;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asByteArray;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asLongArray;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.getAbiFunctionAsJsonString;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.nftAmount;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.to32BytesString;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.util.Strings;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.props.ContractCallRequest;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.beans.factory.annotation.Autowired;

@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class EstimatePrecompileFeature extends AbstractEstimateFeature {
    private static final String HEX_DIGITS = "0123456789abcdef";
    private static final Tuple[] EMPTY_TUPLE_ARRAY = new Tuple[] {};

    private static final String RANDOM_ADDRESS = to32BytesString(RandomStringUtils.random(40, HEX_DIGITS));
    private static final long firstNftSerialNumber = 1;
    private final TokenClient tokenClient;
    private final AccountClient accountClient;
    private TokenId fungibleKycUnfrozenTokenId;
    private TokenId nonFungibleKycUnfrozenTokenId;

    private TokenId fungibleTokenId;
    private TokenId nonFungibleTokenId;
    private DeployedContract deployedEstimatePrecompileContract;
    private DeployedContract deployedErcTestContract;
    private DeployedContract deployedPrecompileContract;
    private ExpandedAccountId receiverAccount;
    private String receiverAccountAlias;
    private ExpandedAccountId secondReceiverAccount;
    private ExpandedAccountId admin;
    private String estimatePrecompileContractSolidityAddress;
    private String ercTestContractSolidityAddress;
    private String precompileTestContractSolidityAddress;

    @Given("I create estimate precompile contract with {int} balance")
    public void createNewEstimateContract(int supply) throws IOException {
        deployedEstimatePrecompileContract = createContract(estimatePrecompileTestContract, supply);
        estimatePrecompileContractSolidityAddress =
                deployedEstimatePrecompileContract.contractId().toSolidityAddress();
        admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        receiverAccount = accountClient.getAccount(AccountClient.AccountNameEnum.BOB);
        secondReceiverAccount = accountClient.getAccount(AccountNameEnum.DAVE);
        receiverAccountAlias = receiverAccount.getPublicKey().toEvmAddress().toString();
    }

    @Given("I create erc test contract with {int} balance")
    public void createNewERCContract(int supply) throws IOException {
        deployedErcTestContract = createContract(ercTestContract, supply);
        ercTestContractSolidityAddress = deployedErcTestContract.contractId().toSolidityAddress();
    }

    @Given("I get exchange rates")
    public void getExchangeRate() {
        exchangeRates = mirrorClient.getExchangeRates();
    }

    @Given("I successfully create Precompile contract with {int} balance")
    public void createNewPrecompileTestContract(int supply) throws IOException {
        deployedPrecompileContract = createContract(precompileTestContract, supply);
        precompileTestContractSolidityAddress =
                deployedPrecompileContract.contractId().toSolidityAddress();
    }

    @Given("I successfully create fungible tokens")
    public void createFungibleToken() {
        fungibleKycUnfrozenTokenId = tokenClient.getToken(FUNGIBLE_KYC_UNFROZEN).tokenId();
        fungibleTokenId = tokenClient.getToken(FUNGIBLE).tokenId();
    }

    @Given("I successfully create non fungible tokens")
    public void createNonFungibleToken() {
        nonFungibleKycUnfrozenTokenId = tokenClient.getToken(NFT_KYC_UNFROZEN).tokenId();
        nonFungibleTokenId = tokenClient.getToken(NFT).tokenId();
    }

    @Given("I mint and verify a new nft")
    public void mintNft() {
        tokenClient.mint(nonFungibleTokenId, RandomUtils.nextBytes(4));
        networkTransactionResponse = tokenClient.mint(nonFungibleKycUnfrozenTokenId, RandomUtils.nextBytes(4));
    }

    @Then("the mirror node REST API should return status {int} for the HAPI transaction")
    public void verifyMirrorAPIResponses(int status) {
        verifyMirrorTransactionsResponse(mirrorClient, status);
    }

    @And("I set lower deviation at {int}% and upper deviation at {int}%")
    public void setDeviations(int lower, int upper) {
        lowerDeviation = lower;
        upperDeviation = upper;
    }

    @Then("I call estimateGas with associate function for fungible token")
    public void associateFunctionEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.ASSOCIATE_TOKEN)
                .encodeCallWithArgs(asAddress(receiverAccountAlias), asAddress(fungibleTokenId.toSolidityAddress()));
        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.ASSOCIATE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with associate function for NFT")
    public void associateFunctionNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.ASSOCIATE_TOKEN)
                .encodeCallWithArgs(asAddress(receiverAccountAlias), asAddress(nonFungibleTokenId.toSolidityAddress()));
        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.ASSOCIATE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with dissociate token function without association for fungible token")
    public void dissociateFunctionEstimateGasNegative() {
        // attempt to call dissociate function without having association
        // expecting status 400/revert
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.DISSOCIATE_TOKEN)
                .encodeCallWithArgs(asAddress(receiverAccountAlias), asAddress(fungibleTokenId.toSolidityAddress()));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with dissociate token function without association for NFT")
    public void dissociateFunctionNFTEstimateGasNegative() {
        // attempt to call dissociate function without having association
        // expecting status 400/revert
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.DISSOCIATE_TOKEN)
                .encodeCallWithArgs(asAddress(receiverAccountAlias), asAddress(nonFungibleTokenId.toSolidityAddress()));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with nested associate function that executes it twice for fungible token")
    public void nestedAssociateFunctionEstimateGas() {
        // attempt to call associate function twice
        // expecting a revert
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.NESTED_ASSOCIATE)
                .encodeCallWithArgs(asAddress(receiverAccountAlias), asAddress(fungibleTokenId.toSolidityAddress()));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with nested associate function that executes it twice for NFT")
    public void nestedAssociateFunctionNFTEstimateGas() {
        // attempt to call associate function twice
        // expecting a revert
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.NESTED_ASSOCIATE)
                .encodeCallWithArgs(asAddress(receiverAccountAlias), asAddress(nonFungibleTokenId.toSolidityAddress()));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @And("I associate the receiver account with the fungible token")
    public void associateReceiverWithFungibleEstimateGas() {
        // associating the token with the token address
        networkTransactionResponse = tokenClient.associate(receiverAccount, fungibleTokenId);
    }

    @Then("I call estimateGas with dissociate token function for fungible token")
    public void dissociateFunctionEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.DISSOCIATE_TOKEN)
                .encodeCallWithArgs(asAddress(receiverAccountAlias), asAddress(fungibleTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.DISSOCIATE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @And("I associate the receiver account with the NFT")
    public void associateReceiverWithNonFungibleEstimateGas() {
        // associating the NFT with the address
        networkTransactionResponse = tokenClient.associate(receiverAccount, nonFungibleTokenId);
    }

    @Then("I call estimateGas with dissociate token function for NFT")
    public void dissociateFunctionNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.DISSOCIATE_TOKEN)
                .encodeCallWithArgs(asAddress(receiverAccountAlias), asAddress(nonFungibleTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.DISSOCIATE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with dissociate and associate nested function for fungible token")
    public void dissociateAndAssociatedEstimateGas() {
        // token is already associated
        // attempting to execute nested dissociate and associate function
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.DISSOCIATE_AND_ASSOCIATE)
                .encodeCallWithArgs(asAddress(receiverAccountAlias), asAddress(fungibleTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.DISSOCIATE_AND_ASSOCIATE.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with dissociate and associate nested function for NFT")
    public void dissociateAndAssociatedNFTEstimateGas() {
        // token is already associated
        // attempting to execute nested dissociate and associate function
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.DISSOCIATE_AND_ASSOCIATE)
                .encodeCallWithArgs(asAddress(receiverAccountAlias), asAddress(nonFungibleTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.DISSOCIATE_AND_ASSOCIATE.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with approve function without association")
    public void approveWithoutAssociationEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.APPROVE)
                .encodeCallWithArgs(
                        asAddress(fungibleTokenId.toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        new BigInteger("10"));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with setApprovalForAll function without association")
    public void setApprovalForAllWithoutAssociationEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.SET_APPROVAL_FOR_ALL)
                .encodeCallWithArgs(
                        asAddress(nonFungibleTokenId.toSolidityAddress()), asAddress(receiverAccountAlias), true);

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with approveNFT function without association")
    public void approveNonFungibleWithoutAssociationEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.APPROVE_NFT)
                .encodeCallWithArgs(
                        asAddress(nonFungibleTokenId.toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        new BigInteger("1"));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I associate contracts with the tokens and approve all nft serials")
    public void associateTokensWithContract() throws InvalidProtocolBufferException {
        // In order to execute Approve, approveNFT, ercApprove we need to associate the contract with the token
        tokenClient.associate(deployedErcTestContract.contractId(), fungibleTokenId);
        tokenClient.associate(deployedEstimatePrecompileContract.contractId(), fungibleTokenId);
        tokenClient.associate(deployedEstimatePrecompileContract.contractId(), nonFungibleKycUnfrozenTokenId);
        tokenClient.associate(deployedPrecompileContract.contractId(), fungibleTokenId);
        tokenClient.associate(deployedPrecompileContract.contractId(), nonFungibleKycUnfrozenTokenId);
        // approve is also needed for the approveNFT function
        accountClient.approveNftAllSerials(nonFungibleKycUnfrozenTokenId, deployedPrecompileContract.contractId());
        networkTransactionResponse = accountClient.approveNftAllSerials(
                nonFungibleKycUnfrozenTokenId, deployedEstimatePrecompileContract.contractId());
    }

    @Then("I call estimateGas with approve function")
    public void approveEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.APPROVE)
                .encodeCallWithArgs(
                        asAddress(fungibleTokenId.toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        new BigInteger("10"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.APPROVE.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with approveNFT function")
    public void approveNftEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.APPROVE_NFT)
                .encodeCallWithArgs(
                        asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        new BigInteger("1"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.APPROVE_NFT.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with ERC approve function")
    public void ercApproveEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(ContractMethods.APPROVE_ERC)
                .encodeCallWithArgs(
                        asAddress(fungibleTokenId.toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        new BigInteger("10"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.APPROVE_ERC.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with setApprovalForAll function")
    public void setApprovalForAllEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.SET_APPROVAL_FOR_ALL)
                .encodeCallWithArgs(
                        asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        true);

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.SET_APPROVAL_FOR_ALL.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with transferFrom function without approval")
    public void transferFromEstimateGasWithoutApproval() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.TRANSFER_FROM)
                .encodeCallWithArgs(
                        asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        new BigInteger("5"));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with ERC transferFrom function without approval")
    public void ercTransferFromEstimateGasWithoutApproval() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(ContractMethods.TRANSFER_FROM_ERC)
                .encodeCallWithArgs(
                        asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        new BigInteger("10"));

        assertContractCallReturnsBadRequest(encodedFunctionCall, ercTestContractSolidityAddress);
    }

    @And("I approve receiver account to use fungible token")
    public void approveFungibleWithReceiver() {
        networkTransactionResponse = accountClient.approveToken(fungibleTokenId, receiverAccount.getAccountId(), 10);
    }

    @Then("I call estimateGas with transferFrom function")
    public void transferFromEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.TRANSFER_FROM)
                .encodeCallWithArgs(
                        asAddress(fungibleTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        new BigInteger("5"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.TRANSFER_FROM.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with ERC transferFrom function")
    public void ercTransferFromEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(ContractMethods.TRANSFER_FROM_ERC)
                .encodeCallWithArgs(
                        asAddress(fungibleTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        new BigInteger("5"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.TRANSFER_FROM_ERC.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with transferFrom function with more than the approved allowance")
    public void transferFromExceedAllowanceEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.TRANSFER_FROM)
                .encodeCallWithArgs(
                        asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        new BigInteger("500"));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with ERC transferFrom function with more than the approved allowance")
    public void ercTransferFromExceedsAllowanceEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(ContractMethods.TRANSFER_FROM_ERC)
                .encodeCallWithArgs(
                        asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        new BigInteger("500"));

        assertContractCallReturnsBadRequest(encodedFunctionCall, ercTestContractSolidityAddress);
    }

    @And("I approve receiver account to use the NFT with id 1")
    public void approveNonFungibleWithReceiver() {
        NftId id = new NftId(nonFungibleTokenId, firstNftSerialNumber);
        networkTransactionResponse = accountClient.approveNft(id, receiverAccount.getAccountId());
    }

    @Then("I call estimateGas with transferFromNFT function")
    public void transferFromNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.TRANSFER_FROM_NFT)
                .encodeCallWithArgs(
                        asAddress(nonFungibleTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        new BigInteger("1"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.TRANSFER_FROM_NFT.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with transferFromNFT with invalid serial number")
    public void transferFromNFTInvalidSerialEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.TRANSFER_FROM_NFT)
                .encodeCallWithArgs(
                        asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        new BigInteger("50"));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with transferToken function")
    public void transferTokenEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.TRANSFER_TOKEN)
                .encodeCallWithArgs(
                        asAddress(fungibleTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        5L);

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.TRANSFER_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with transferNFT function")
    public void transferNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.TRANSFER_NFT)
                .encodeCallWithArgs(
                        asAddress(nonFungibleTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        1L);

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.TRANSFER_NFT.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @And("I approve receiver account to use fungible token and transfer fungible token to the erc contract")
    public void approveAndTransferFungibleToken() {
        accountClient.approveToken(fungibleTokenId, receiverAccount.getAccountId(), 50L);
        networkTransactionResponse = tokenClient.transferFungibleToken(
                fungibleTokenId,
                admin,
                AccountId.fromString(deployedErcTestContract.contractId().toString()),
                null,
                10);
    }

    @Then("I call estimateGas with ERC transfer function")
    public void ercTransferEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(ContractMethods.TRANSFER_ERC)
                .encodeCallWithArgs(
                        asAddress(fungibleTokenId.toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        new BigInteger("5"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.TRANSFER_ERC.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with associateTokens function for fungible tokens")
    public void associateTokensEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.ASSOCIATE_TOKENS)
                .encodeCallWithArgs(
                        asAddress(secondReceiverAccount.getAccountId().toSolidityAddress()),
                        asAddressArray(Arrays.asList(
                                fungibleTokenId.toSolidityAddress(), fungibleKycUnfrozenTokenId.toSolidityAddress())));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.ASSOCIATE_TOKENS.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with associateTokens function for NFTs")
    public void associateNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.ASSOCIATE_TOKENS)
                .encodeCallWithArgs(
                        asAddress(secondReceiverAccount.getAccountId().toSolidityAddress()),
                        asAddressArray(Arrays.asList(
                                nonFungibleKycUnfrozenTokenId.toSolidityAddress(),
                                nonFungibleTokenId.toSolidityAddress())));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.ASSOCIATE_TOKENS.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @And("I associate the fungible_kyc_unfrozen token with the receiver account")
    public void associateFungibleKycUnfrozenTokenWithReceiverAccount() {
        networkTransactionResponse = tokenClient.associate(receiverAccount, fungibleKycUnfrozenTokenId);
    }

    @Then("I call estimateGas with dissociateTokens function for fungible tokens")
    public void dissociateTokensEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.DISSOCIATE_TOKENS)
                .encodeCallWithArgs(
                        asAddress(receiverAccountAlias),
                        asAddressArray(Arrays.asList(
                                fungibleTokenId.toSolidityAddress(), fungibleKycUnfrozenTokenId.toSolidityAddress())));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.DISSOCIATE_TOKENS.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @And("I associate the nft_kyc_unfrozen with the receiver account")
    public void associateNonFungibleKycUnfrozenTokenWithReceiverAccount() {
        networkTransactionResponse = tokenClient.associate(receiverAccount, nonFungibleKycUnfrozenTokenId);
    }

    @Then("I call estimateGas with dissociateTokens function for NFTs")
    public void dissociateNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.DISSOCIATE_TOKENS)
                .encodeCallWithArgs(
                        asAddress(receiverAccountAlias),
                        asAddressArray(Arrays.asList(
                                nonFungibleKycUnfrozenTokenId.toSolidityAddress(),
                                nonFungibleTokenId.toSolidityAddress())));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.DISSOCIATE_TOKENS.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @And("I associate and approve the second receiver to use the fungible_kyc_unfrozen token")
    public void associateAndApproveFungibleKycUnfrozenTokenWithReceiverAccount() {
        tokenClient.associate(secondReceiverAccount, fungibleTokenId);
        networkTransactionResponse =
                accountClient.approveToken(fungibleTokenId, secondReceiverAccount.getAccountId(), 10);
    }

    @Then("I call estimateGas with transferTokens function")
    public void transferTokensEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.TRANSFER_TOKENS)
                .encodeCallWithArgs(
                        asAddress(fungibleTokenId.toSolidityAddress()),
                        asAddressArray(Arrays.asList(
                                admin.getAccountId().toSolidityAddress(),
                                receiverAccountAlias,
                                secondReceiverAccount.getAccountId().toSolidityAddress())),
                        new long[] {-6L, 3L, 3L});

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.TRANSFER_TOKENS.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @And("I mint a new NFT and approve second receiver account to all serial numbers")
    public void mintAndApproveAllSerialsToSecondReceiver() {
        tokenClient.mint(nonFungibleTokenId, RandomUtils.nextBytes(4));
        accountClient.approveNftAllSerials(nonFungibleTokenId, receiverAccount.getAccountId());
        networkTransactionResponse =
                accountClient.approveNftAllSerials(nonFungibleTokenId, secondReceiverAccount.getAccountId());
    }

    @Then("I call estimateGas with transferNFTs function")
    public void transferNFTsEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.TRANSFER_NFTS)
                .encodeCallWithArgs(
                        asAddress(nonFungibleTokenId.toSolidityAddress()),
                        asAddressArray(Arrays.asList(admin.getAccountId().toSolidityAddress())),
                        asAddressArray(Arrays.asList(
                                receiverAccountAlias,
                                secondReceiverAccount.getAccountId().toSolidityAddress())),
                        new long[] {1, 2});

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.TRANSFER_NFTS.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with cryptoTransfer function for hbars")
    public void cryptoTransferHbarEstimateGas() {
        var senderTransfer = accountAmount(admin.getAccountId().toSolidityAddress(), -10L, false);
        var receiverTransfer = accountAmount(receiverAccountAlias, 10L, false);
        var args = Tuple.of((Object) new Tuple[] {senderTransfer, receiverTransfer});
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.CRYPTO_TRANSFER_HBARS)
                .encodeCallWithArgs(args, EMPTY_TUPLE_ARRAY);
        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.CRYPTO_TRANSFER_HBARS.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    private TokenTransferListBuilder tokenTransferList() {
        return new TokenTransferListBuilder();
    }

    @Then("I call estimateGas with cryptoTransfer function for nft")
    public void cryptoTransferNFTEstimateGas() {
        var tokenTransferList = (Object) new Tuple[] {
            tokenTransferList()
                    .forToken(nonFungibleTokenId.toSolidityAddress())
                    .withNftTransfers(
                            nftAmount(admin.getAccountId().toSolidityAddress(), receiverAccountAlias, 1L, false))
                    .build()
        };
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.CRYPTO_TRANSFER_NFT)
                .encodeCallWithArgs(Tuple.of((Object) EMPTY_TUPLE_ARRAY), tokenTransferList);
        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.CRYPTO_TRANSFER_NFT.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with cryptoTransfer function for fungible tokens")
    public void cryptoTransferFungibleEstimateGas() {
        var tokenTransferList = (Object) new Tuple[] {
            tokenTransferList()
                    .forToken(fungibleTokenId.toSolidityAddress())
                    .withAccountAmounts(
                            accountAmount(admin.getAccountId().toSolidityAddress(), -3L, false),
                            accountAmount(secondReceiverAccount.getAccountId().toSolidityAddress(), 3L, false))
                    .build()
        };
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.CRYPTO_TRANSFER)
                .encodeCallWithArgs(Tuple.of((Object) EMPTY_TUPLE_ARRAY), tokenTransferList);
        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.CRYPTO_TRANSFER.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with mintToken function for fungible token")
    public void mintFungibleTokenEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.MINT_TOKEN)
                .encodeCallWithArgs(
                        asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()), 1L, asByteArray(new ArrayList<>()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.MINT_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with mintToken function for NFT")
    public void mintNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.MINT_NFT)
                .encodeCallWithArgs(
                        asAddress(nonFungibleTokenId.toSolidityAddress()), 0L, asByteArray(Arrays.asList("0x02")));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.MINT_NFT.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with burnToken function for fungible token")
    public void burnFungibleTokenEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.BURN_TOKEN)
                .encodeCallWithArgs(
                        asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()), 1L, asLongArray(new ArrayList<>()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.BURN_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with burnToken function for NFT")
    public void burnNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.BURN_TOKEN)
                .encodeCallWithArgs(
                        asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()), 0L, asLongArray(List.of(1L)));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.BURN_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with CreateFungibleToken function")
    public void createFungibleTokenEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.CREATE_FUNGIBLE_TOKEN)
                .encodeCallWithArgs(asAddress(admin.getAccountId().toSolidityAddress()));

        Consumer<Boolean> estimateFunction = current -> validateGasEstimationForCreateToken(
                Strings.encode(encodedFunctionCall),
                ContractMethods.CREATE_FUNGIBLE_TOKEN.getActualGas(),
                calculateCreateTokenFee(1, current));
        executeAndRetryWithNextExchangeRates(estimateFunction);
    }

    @Then("I call estimateGas with CreateNFT function")
    public void createNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.CREATE_NFT)
                .encodeCallWithArgs(asAddress(admin.getAccountId().toSolidityAddress()));

        Consumer<Boolean> estimateFunction = current -> validateGasEstimationForCreateToken(
                Strings.encode(encodedFunctionCall),
                ContractMethods.CREATE_FUNGIBLE_TOKEN.getActualGas(),
                calculateCreateTokenFee(1, current));
        executeAndRetryWithNextExchangeRates(estimateFunction);
    }

    @Then("I call estimateGas with CreateFungibleToken function with custom fees")
    public void createFungibleTokenWithCustomFeesEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                        ContractMethods.CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES)
                .encodeCallWithArgs(
                        asAddress(admin.getAccountId().toSolidityAddress()),
                        asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()));

        Consumer<Boolean> estimateFunction = current -> validateGasEstimationForCreateToken(
                Strings.encode(encodedFunctionCall),
                ContractMethods.CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES.getActualGas(),
                calculateCreateTokenFee(2, current));
        executeAndRetryWithNextExchangeRates(estimateFunction);
    }

    @Then("I call estimateGas with CreateNFT function with custom fees")
    public void createNFTWithCustomFeesEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.CREATE_NFT_WITH_CUSTOM_FEES)
                .encodeCallWithArgs(
                        asAddress(admin.getAccountId().toSolidityAddress()),
                        asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()));

        Consumer<Boolean> estimateFunction = current -> validateGasEstimationForCreateToken(
                Strings.encode(encodedFunctionCall),
                ContractMethods.CREATE_NFT_WITH_CUSTOM_FEES.getActualGas(),
                calculateCreateTokenFee(2, current));
        executeAndRetryWithNextExchangeRates(estimateFunction);
    }

    @And("I approve and transfer fungible tokens to receiver account")
    public void approveAndTransferFungibleTokensToReceiverAccount() {
        accountClient.approveToken(fungibleTokenId, receiverAccount.getAccountId(), 100L);
        networkTransactionResponse = tokenClient.transferFungibleToken(
                fungibleTokenId, admin, receiverAccount.getAccountId(), receiverAccount.getPrivateKey(), 10L);
    }

    @Then("I call estimateGas with WipeTokenAccount function")
    public void wipeTokenAccountEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.WIPE_TOKEN_ACCOUNT)
                .encodeCallWithArgs(
                        asAddress(fungibleTokenId.toSolidityAddress()), asAddress(receiverAccountAlias), 1L);

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.WIPE_TOKEN_ACCOUNT.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with WipeTokenAccount function with invalid amount")
    public void wipeTokenAccountInvalidAmountEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.WIPE_TOKEN_ACCOUNT)
                .encodeCallWithArgs(
                        asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        100000000000000000L);

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @And("I transfer NFT to receiver account")
    public void transferNonFungibleToReceiverAccount() {
        networkTransactionResponse = tokenClient.transferNonFungibleToken(
                nonFungibleTokenId,
                admin,
                receiverAccount.getAccountId(),
                Collections.singletonList(firstNftSerialNumber),
                receiverAccount.getPrivateKey());
    }

    @Then("I call estimateGas with WipeNFTAccount function")
    public void wipeNFTAccountEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.WIPE_NFT_ACCOUNT)
                .encodeCallWithArgs(
                        asAddress(nonFungibleTokenId.toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        asLongArray(List.of(1L)));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.WIPE_NFT_ACCOUNT.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with WipeNFTAccount function with invalid serial number")
    public void wipeNFTAccountInvalidSerialNumberEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.WIPE_NFT_ACCOUNT)
                .encodeCallWithArgs(
                        asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        asLongArray(List.of(66L)));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with GrantKYC function for fungible token")
    public void grantKYCFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.GRANT_KYC)
                .encodeCallWithArgs(
                        asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()), asAddress(receiverAccountAlias));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.GRANT_KYC.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with GrantKYC function for NFT")
    public void grantKYCNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.GRANT_KYC)
                .encodeCallWithArgs(
                        asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()), asAddress(receiverAccountAlias));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.GRANT_KYC.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with RevokeTokenKYC function for fungible token")
    public void revokeTokenKYCEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.REVOKE_KYC)
                .encodeCallWithArgs(
                        asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()), asAddress(receiverAccountAlias));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.REVOKE_KYC.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with RevokeTokenKYC function for NFT")
    public void revokeTokenKYCNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.REVOKE_KYC)
                .encodeCallWithArgs(
                        asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()), asAddress(receiverAccountAlias));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.REVOKE_KYC.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with Grant and Revoke KYC nested function")
    public void nestedGrantRevokeKYCEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.NESTED_GRANT_REVOKE_KYC)
                .encodeCallWithArgs(
                        asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()), asAddress(receiverAccountAlias));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.NESTED_GRANT_REVOKE_KYC.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with Freeze function for fungible token")
    public void freezeFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.FREEZE_TOKEN)
                .encodeCallWithArgs(
                        asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()), asAddress(receiverAccountAlias));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.FREEZE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with Freeze function for NFT")
    public void freezeNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.FREEZE_TOKEN)
                .encodeCallWithArgs(
                        asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()), asAddress(receiverAccountAlias));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.FREEZE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with Unfreeze function for fungible token")
    public void unfreezeFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.UNFREEZE_TOKEN)
                .encodeCallWithArgs(
                        asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()), asAddress(receiverAccountAlias));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.UNFREEZE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with Unfreeze function for NFT")
    public void unfreezeNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.UNFREEZE_TOKEN)
                .encodeCallWithArgs(
                        asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()), asAddress(receiverAccountAlias));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.UNFREEZE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with nested Freeze and Unfreeze function for fungible token")
    public void nestedFreezeAndUnfreezeEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.NESTED_FREEZE_UNFREEZE)
                .encodeCallWithArgs(
                        asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()), asAddress(receiverAccountAlias));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.NESTED_FREEZE_UNFREEZE.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with nested Freeze and Unfreeze function for NFT")
    public void nestedFreezeAndUnfreezeNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.NESTED_FREEZE_UNFREEZE)
                .encodeCallWithArgs(
                        asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()), asAddress(receiverAccountAlias));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.NESTED_FREEZE_UNFREEZE.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with delete function for Fungible token")
    public void deleteFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.DELETE_TOKEN)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.DELETE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with delete function for NFT")
    public void deleteNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.DELETE_TOKEN)
                .encodeCallWithArgs(asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.DELETE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with delete function for invalid token address")
    public void deleteTokenRandomAddressEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.DELETE_TOKEN)
                .encodeCallWithArgs(asAddress(RANDOM_ADDRESS));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with pause function for fungible token")
    public void pauseFungibleTokenPositiveEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.PAUSE_TOKEN)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.PAUSE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with pause function for NFT")
    public void pauseNFTPositiveEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.PAUSE_TOKEN)
                .encodeCallWithArgs(asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.PAUSE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with unpause function for fungible token")
    public void unpauseFungibleTokenPositiveEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.UNPAUSE_TOKEN)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.UNPAUSE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with unpause function for NFT")
    public void unpauseNFTPositiveEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.UNPAUSE_TOKEN)
                .encodeCallWithArgs(asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.UNPAUSE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas for nested pause and unpause function")
    public void pauseUnpauseFungibleTokenNestedCallEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.PAUSE_UNPAUSE_NESTED_TOKEN)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.PAUSE_UNPAUSE_NESTED_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas for nested pause, unpause NFT function")
    public void pauseUnpauseNFTNestedCallEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.PAUSE_UNPAUSE_NESTED_TOKEN)
                .encodeCallWithArgs(asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.PAUSE_UNPAUSE_NESTED_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with updateTokenExpiryInfo function")
    public void updateTokenExpiryInfoEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.UPDATE_TOKEN_EXPIRY)
                .encodeCallWithArgs(
                        asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.UPDATE_TOKEN_EXPIRY.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with updateTokenInfo function")
    public void updateTokenInfoEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.UPDATE_TOKEN_INFO)
                .encodeCallWithArgs(
                        asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.UPDATE_TOKEN_INFO.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with updateTokenKeys function")
    public void updateTokenKeysEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.UPDATE_TOKEN_KEYS)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.UPDATE_TOKEN_KEYS.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenExpiryInfo function")
    public void getTokenExpiryInfoEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.GET_TOKEN_EXPIRY_INFO)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_EXPIRY_INFO.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with isToken function")
    public void isTokenEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.IS_TOKEN)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.IS_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenKey function for supply")
    public void getTokenKeySupplyEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.GET_TOKEN_KEY)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()), new BigInteger("16"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_KEY.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenKey function for KYC")
    public void getTokenKeyKYCEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.GET_TOKEN_KEY)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()), new BigInteger("2"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_KEY.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenKey function for freeze")
    public void getTokenKeyFreezeEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.GET_TOKEN_KEY)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()), new BigInteger("4"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_KEY.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenKey function for admin")
    public void getTokenKeyAdminEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.GET_TOKEN_KEY)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()), new BigInteger("1"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_KEY.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenKey function for wipe")
    public void getTokenKeyWipeEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.GET_TOKEN_KEY)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()), new BigInteger("8"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_KEY.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenKey function for fee")
    public void getTokenKeyFeeEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.GET_TOKEN_KEY)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()), new BigInteger("32"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_KEY.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenKey function for pause")
    public void getTokenKeyPauseEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.GET_TOKEN_KEY)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()), new BigInteger("64"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_KEY.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with allowance function for fungible token")
    public void allowanceFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.ALLOWANCE)
                .encodeCallWithArgs(
                        asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()),
                        asAddress(receiverAccountAlias));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.ALLOWANCE.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with allowance function for NFT")
    public void allowanceNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.ALLOWANCE)
                .encodeCallWithArgs(
                        asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()),
                        asAddress(receiverAccountAlias));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.ALLOWANCE.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with ERC allowance function for fungible token")
    public void ercAllowanceFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(ContractMethods.ALLOWANCE_ERC)
                .encodeCallWithArgs(
                        asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()),
                        asAddress(receiverAccountAlias));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.ALLOWANCE_ERC.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getApproved function for NFT")
    public void getApprovedNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.GET_APPROVED)
                .encodeCallWithArgs(asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()), new BigInteger("1"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.GET_APPROVED.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with ERC getApproved function for NFT")
    public void ercGetApprovedNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(ContractMethods.GET_APPROVED_ERC)
                .encodeCallWithArgs(asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()), new BigInteger("1"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.GET_APPROVED_ERC.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with isApprovedForAll function")
    public void isApprovedForAllEstimateGas() {
        // reminder: check with setApprovalForAll test-> there we have the contract associated so the test can work
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.IS_APPROVED_FOR_ALL)
                .encodeCallWithArgs(
                        asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()),
                        asAddress(receiverAccountAlias));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.IS_APPROVED_FOR_ALL.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with ERC isApprovedForAll function")
    public void ercIsApprovedForAllEstimateGas() {
        // reminder: check with setApprovalForAll test-> there we have the contract associated so the test can work
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(ContractMethods.IS_APPROVED_FOR_ALL_ERC)
                .encodeCallWithArgs(
                        asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()),
                        asAddress(receiverAccountAlias));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.IS_APPROVED_FOR_ALL_ERC.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with name function for fungible token")
    public void nameEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(ContractMethods.NAME)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.NAME.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with name function for NFT")
    public void nameNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(ContractMethods.NAME_NFT)
                .encodeCallWithArgs(asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.NAME_NFT.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with symbol function for fungible token")
    public void symbolEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(ContractMethods.SYMBOL)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.SYMBOL.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with symbol function for NFT")
    public void symbolNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(ContractMethods.SYMBOL_NFT)
                .encodeCallWithArgs(asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.SYMBOL_NFT.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with decimals function for fungible token")
    public void decimalsEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(ContractMethods.DECIMALS)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.DECIMALS.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with totalSupply function for fungible token")
    public void totalSupplyEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(ContractMethods.TOTAL_SUPPLY)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.TOTAL_SUPPLY.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with totalSupply function for NFT")
    public void totalSupplyNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(ContractMethods.TOTAL_SUPPLY_NFT)
                .encodeCallWithArgs(asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.TOTAL_SUPPLY_NFT.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with balanceOf function for fungible token")
    public void balanceOfEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(ContractMethods.BALANCE_OF)
                .encodeCallWithArgs(
                        asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.BALANCE_OF.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with balanceOf function for NFT")
    public void balanceOfNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(ContractMethods.BALANCE_OF_NFT)
                .encodeCallWithArgs(
                        asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.BALANCE_OF_NFT.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with ownerOf function for NFT")
    public void ownerOfEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(ContractMethods.OWNER_OF)
                .encodeCallWithArgs(asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()), new BigInteger("1"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.OWNER_OF.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with tokenURI function for NFT")
    public void tokenURIEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(ContractMethods.TOKEN_URI)
                .encodeCallWithArgs(asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()), new BigInteger("1"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.TOKEN_URI.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getFungibleTokenInfo function")
    public void getFungibleTokenInfoEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(ContractMethods.GET_FUNGIBLE_TOKEN_INFO)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.GET_FUNGIBLE_TOKEN_INFO.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getNonFungibleTokenInfo function")
    public void getNonFungibleTokenInfoEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(ContractMethods.GET_NON_FUNGIBLE_TOKEN_INFO)
                .encodeCallWithArgs(asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()), 1L);

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.GET_NON_FUNGIBLE_TOKEN_INFO.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenInfo function for fungible")
    public void getTokenInfoEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(ContractMethods.GET_TOKEN_INFO)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_INFO.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenInfo function for NFT")
    public void getTokenInfoNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(ContractMethods.GET_TOKEN_INFO_NFT)
                .encodeCallWithArgs(asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_INFO_NFT.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenDefaultFreezeStatus function for fungible token")
    public void getTokenDefaultFreezeStatusFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(
                        ContractMethods.GET_TOKEN_DEFAULT_FREEZE_STATUS)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_DEFAULT_FREEZE_STATUS.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenDefaultFreezeStatus function for NFT")
    public void getTokenDefaultFreezeStatusNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(
                        ContractMethods.GET_TOKEN_DEFAULT_FREEZE_STATUS)
                .encodeCallWithArgs(asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_DEFAULT_FREEZE_STATUS.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenDefaultKycStatus function for fungible token")
    public void getTokenDefaultKycStatusFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(ContractMethods.GET_TOKEN_DEFAULT_KYC_STATUS)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_DEFAULT_KYC_STATUS.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenDefaultKycStatus function for NFT")
    public void getTokenDefaultKycStatusNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(ContractMethods.GET_TOKEN_DEFAULT_KYC_STATUS)
                .encodeCallWithArgs(asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_DEFAULT_KYC_STATUS.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with isKyc function for fungible token")
    public void isKycFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(ContractMethods.IS_KYC)
                .encodeCallWithArgs(
                        asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()), asAddress(receiverAccountAlias));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.IS_KYC.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with isKyc function for NFT")
    public void isKycNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(ContractMethods.IS_KYC)
                .encodeCallWithArgs(
                        asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()), asAddress(receiverAccountAlias));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.IS_KYC.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with isFrozen function for fungible token")
    public void isFrozenFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(ContractMethods.IS_FROZEN)
                .encodeCallWithArgs(
                        asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()), asAddress(receiverAccountAlias));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.IS_FROZEN.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with isFrozen function for NFT")
    public void isFrozenNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(ContractMethods.IS_FROZEN)
                .encodeCallWithArgs(
                        asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()), asAddress(receiverAccountAlias));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.IS_FROZEN.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenType function for fungible token")
    public void getTokenTypeFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(ContractMethods.GET_TOKEN_TYPE)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_TYPE.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenType function for NFT")
    public void getTokenTypeNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(ContractMethods.GET_TOKEN_TYPE)
                .encodeCallWithArgs(asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_TYPE.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect balanceOf function")
    public void redirectBalanceOfEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(
                        ContractMethods.REDIRECT_FOR_TOKEN_BALANCE_OF)
                .encodeCallWithArgs(
                        asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.REDIRECT_FOR_TOKEN_BALANCE_OF.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect name function")
    public void redirectNameEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(ContractMethods.REDIRECT_FOR_TOKEN_NAME)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.REDIRECT_FOR_TOKEN_NAME.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect symbol function")
    public void redirectSymbolEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(ContractMethods.REDIRECT_FOR_TOKEN_SYMBOL)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.REDIRECT_FOR_TOKEN_SYMBOL.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect name function for NFT")
    public void redirectNameNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(ContractMethods.REDIRECT_FOR_TOKEN_NAME)
                .encodeCallWithArgs(asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.REDIRECT_FOR_TOKEN_NAME.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect symbol function for NFT")
    public void redirectSymbolNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(ContractMethods.REDIRECT_FOR_TOKEN_SYMBOL)
                .encodeCallWithArgs(asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.REDIRECT_FOR_TOKEN_SYMBOL.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect decimals function")
    public void redirectDecimalsEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(ContractMethods.REDIRECT_FOR_TOKEN_DECIMALS)
                .encodeCallWithArgs(asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.REDIRECT_FOR_TOKEN_DECIMALS.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect allowance function")
    public void redirectAllowanceEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(ContractMethods.REDIRECT_FOR_TOKEN_ALLOWANCE)
                .encodeCallWithArgs(
                        asAddress(fungibleKycUnfrozenTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()),
                        asAddress(receiverAccountAlias));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.REDIRECT_FOR_TOKEN_ALLOWANCE.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect getOwnerOf function")
    public void redirectGetOwnerOfEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(
                        ContractMethods.REDIRECT_FOR_TOKEN_GET_OWNER_OF)
                .encodeCallWithArgs(asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()), new BigInteger("1"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.REDIRECT_FOR_TOKEN_GET_OWNER_OF.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect tokenURI function")
    public void redirectTokenURIEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(ContractMethods.REDIRECT_FOR_TOKEN_TOKEN_URI)
                .encodeCallWithArgs(asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()), new BigInteger("1"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.REDIRECT_FOR_TOKEN_TOKEN_URI.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect isApprovedForAll function")
    public void redirectIsApprovedForAllEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(
                        ContractMethods.REDIRECT_FOR_TOKEN_IS_APPROVED_FOR_ALL)
                .encodeCallWithArgs(
                        asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()),
                        asAddress(receiverAccountAlias));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.REDIRECT_FOR_TOKEN_IS_APPROVED_FOR_ALL.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @And("I transfer fungible token to the precompile contract")
    public void transferFungibleToPrecompileContract() {
        networkTransactionResponse = tokenClient.transferFungibleToken(
                fungibleTokenId,
                admin,
                AccountId.fromString(deployedPrecompileContract.contractId().toString()),
                receiverAccount.getPrivateKey(),
                10);
    }

    @Then("I call estimateGas with redirect transfer function")
    public void redirectTransferEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(ContractMethods.REDIRECT_FOR_TOKEN_TRANSFER)
                .encodeCallWithArgs(
                        asAddress(fungibleTokenId.toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        new BigInteger("5"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.REDIRECT_FOR_TOKEN_TRANSFER.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect transferFrom function")
    public void redirectTransferFromEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(
                        ContractMethods.REDIRECT_FOR_TOKEN_TRANSFER_FROM)
                .encodeCallWithArgs(
                        asAddress(fungibleTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        new BigInteger("5"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.REDIRECT_FOR_TOKEN_TRANSFER_FROM.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect approve function")
    public void redirectApproveEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(ContractMethods.REDIRECT_FOR_TOKEN_APPROVE)
                .encodeCallWithArgs(
                        asAddress(fungibleTokenId.toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        new BigInteger("10"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.REDIRECT_FOR_TOKEN_APPROVE.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect transferFrom NFT function")
    public void redirectTransferFromNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(
                        ContractMethods.REDIRECT_FOR_TOKEN_TRANSFER_FROM_NFT)
                .encodeCallWithArgs(
                        asAddress(nonFungibleTokenId.toSolidityAddress()),
                        asAddress(admin.getAccountId().toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        new BigInteger("2"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.REDIRECT_FOR_TOKEN_TRANSFER_FROM_NFT.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect setApprovalForAll function")
    public void redirectSetApprovalForAllEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(
                        ContractMethods.REDIRECT_FOR_TOKEN_SET_APPROVAL_FOR_ALL)
                .encodeCallWithArgs(
                        asAddress(nonFungibleKycUnfrozenTokenId.toSolidityAddress()),
                        asAddress(receiverAccountAlias),
                        true);

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.REDIRECT_FOR_TOKEN_SET_APPROVAL_FOR_ALL.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with pseudo random seed")
    public void pseudoRandomSeedEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.PSEUDO_RANDOM_SEED)
                .encodeCallWithArgs();

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.PSEUDO_RANDOM_SEED.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with pseudo random number")
    public void pseudoRandomNumberEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.PSEUDO_RANDOM_NUMBER)
                .encodeCallWithArgs(500L, 1000L);

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.PSEUDO_RANDOM_NUMBER.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with exchange rate tinycents to tinybars")
    public void exchangeRateTinyCentsToTinyBarsEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                        ContractMethods.EXCHANGE_RATE_TINYCENTS_TO_TINYBARS)
                .encodeCallWithArgs(new BigInteger("100"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.EXCHANGE_RATE_TINYCENTS_TO_TINYBARS.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with exchange rate tinybars to tinycents")
    public void exchangeRateTinyBarsToTinyCentsEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                        ContractMethods.EXCHANGE_RATE_TINYBARS_TO_TINYCENTS)
                .encodeCallWithArgs(new BigInteger("100"));

        validateGasEstimation(
                Strings.encode(encodedFunctionCall),
                ContractMethods.EXCHANGE_RATE_TINYBARS_TO_TINYCENTS.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    private void validateGasEstimationForCreateToken(String selector, int actualGasUsed, long value) {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(selector)
                .to(estimatePrecompileContractSolidityAddress)
                .from(contractClient.getClientAddress())
                .estimate(true)
                .value(value)
                .build();
        ContractCallResponse msgSenderResponse = mirrorClient.contractsCall(contractCallRequestBody);
        int estimatedGas = msgSenderResponse.getResultAsNumber().intValue();
        assertTrue(isWithinDeviation(actualGasUsed, estimatedGas, lowerDeviation, upperDeviation));
    }

    /**
     * Executes estimate gas for token create with current exchange rates and if this fails reties with next exchange rates.
     * The consumer accepts boolean value indicating if we should use current or next exchange rate.
     * true = current, false = next
     * This is done in order to prevent edge cases like:
     * System.currentTimeMillis() returns timestamp that is within the current exchange rate limit, but after few ms
     * the next exchange rate takes place. After some ms when we call the create token with the outdated rates the test fails.
     * We cannot ensure consistent timing between the call getting the exchange rates and the create token call.
     */
    private void executeAndRetryWithNextExchangeRates(Consumer<Boolean> validationFunction) {
        try {
            validationFunction.accept(true);
            return;
        } catch (AssertionError e) {
            log.warn("Assertion failed for estimateGas with current exchange rates. Trying with next exchange rates.");
        } catch (Exception e) {
            log.warn(
                    "Exception occurred for estimateGas with current exchange rates. Trying with next exchange rates.");
        }
        validationFunction.accept(false);
    }

    public Function getFunctionFromEstimateArtifact(ContractMethods contractMethod) {
        String json;
        try (var in = estimatePrecompileTestContract.getInputStream()) {
            json = getAbiFunctionAsJsonString(readCompiledArtifact(in), contractMethod.getFunctionName());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Function.fromJson(json);
    }

    public Function getFunctionFromErcArtifact(ContractMethods contractMethod) {
        String json;
        try (var in = ercTestContract.getInputStream()) {
            json = getAbiFunctionAsJsonString(readCompiledArtifact(in), contractMethod.getFunctionName());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Function.fromJson(json);
    }

    public Function getFunctionFromPrecompileArtifact(ContractMethods contractMethod) {
        String json;
        try (var in = precompileTestContract.getInputStream()) {
            json = getAbiFunctionAsJsonString(readCompiledArtifact(in), contractMethod.getFunctionName());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Function.fromJson(json);
    }

    @Getter
    @RequiredArgsConstructor
    private enum ContractMethods {
        ALLOWANCE("allowanceExternal", 25399),
        ALLOWANCE_ERC("allowance", 27481),
        APPROVE("approveExternal", 729571),
        APPROVE_NFT("approveNFTExternal", 729569),
        APPROVE_ERC("approve", 731632),
        ASSOCIATE_TOKEN("associateTokenExternal", 729374),
        ASSOCIATE_TOKENS("associateTokensExternal", 730641),
        BALANCE_OF("balanceOf", 27270),
        BALANCE_OF_NFT("balanceOfIERC721", 27228),
        BURN_TOKEN("burnTokenExternal", 40247),
        CREATE_FUNGIBLE_TOKEN("createFungibleTokenPublic", 192752),
        CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES("createFungibleTokenWithCustomFeesPublic", 176628),
        CREATE_NFT("createNonFungibleTokenPublic", 192472),
        CREATE_NFT_WITH_CUSTOM_FEES("createNonFungibleTokenWithCustomFeesPublic", 195579),
        CRYPTO_TRANSFER("cryptoTransferExternal", 44438),
        CRYPTO_TRANSFER_HBARS("cryptoTransferExternal", 29698),
        CRYPTO_TRANSFER_NFT("cryptoTransferExternal", 57934),
        DECIMALS("decimals", 27143),
        DELETE_TOKEN("deleteTokenExternal", 39095),
        DISSOCIATE_AND_ASSOCIATE("dissociateAndAssociateTokenExternal", 1434814),
        DISSOCIATE_TOKEN("dissociateTokenExternal", 729428),
        DISSOCIATE_TOKENS("dissociateTokensExternal", 730641),
        EXCHANGE_RATE_TINYCENTS_TO_TINYBARS("tinycentsToTinybars", 24833),
        EXCHANGE_RATE_TINYBARS_TO_TINYCENTS("tinybarsToTinycents", 24811),
        FREEZE_TOKEN("freezeTokenExternal", 39339),
        GET_APPROVED("getApprovedExternal", 25192),
        GET_APPROVED_ERC("getApproved", 27393),
        GET_FUNGIBLE_TOKEN_INFO("getInformationForFungibleToken", 58190),
        GET_NON_FUNGIBLE_TOKEN_INFO("getInformationForNonFungibleToken", 59159),
        GET_TOKEN_DEFAULT_FREEZE_STATUS("getTokenDefaultFreeze", 25191),
        GET_TOKEN_DEFAULT_KYC_STATUS("getTokenDefaultKyc", 25267),
        GET_TOKEN_EXPIRY_INFO("getTokenExpiryInfoExternal", 25617),
        GET_TOKEN_INFO("getInformationForToken", 57612),
        GET_TOKEN_INFO_NFT("getInformationForToken", 56523),
        GET_TOKEN_KEY("getTokenKeyExternal", 27024),
        GET_TOKEN_TYPE("getType", 25223),
        GRANT_KYC("grantTokenKycExternal", 39311),
        IS_APPROVED_FOR_ALL("isApprovedForAllExternal", 25483),
        IS_APPROVED_FOR_ALL_ERC("isApprovedForAll", 27520),
        IS_TOKEN("isTokenExternal", 25100),
        IS_FROZEN("isTokenFrozen", 25473),
        IS_KYC("isKycGranted", 25417),
        MINT_TOKEN("mintTokenExternal", 40700),
        MINT_NFT("mintTokenExternal", 309748),
        NAME("name", 27976),
        NAME_NFT("nameIERC721", 27837),
        NESTED_ASSOCIATE("nestedAssociateTokenExternal", 0),
        NESTED_FREEZE_UNFREEZE("nestedFreezeUnfreezeTokenExternal", 54548),
        NESTED_GRANT_REVOKE_KYC("nestedGrantAndRevokeTokenKYCExternal", 54516),
        OWNER_OF("getOwnerOf", 27271),
        PSEUDO_RANDOM_SEED("getPseudorandomSeed", 36270),
        PSEUDO_RANDOM_NUMBER("getPseudorandomNumber", 36729),
        REVOKE_KYC("revokeTokenKycExternal", 39324),
        REDIRECT_FOR_TOKEN_ALLOWANCE("allowanceRedirect", 33182),
        REDIRECT_FOR_TOKEN_APPROVE("approveRedirect", 737257),
        REDIRECT_FOR_TOKEN_BALANCE_OF("balanceOfRedirect", 32806),
        REDIRECT_FOR_TOKEN_DECIMALS("decimalsRedirect", 32411),
        REDIRECT_FOR_TOKEN_GET_APPROVED("getApprovedRedirect", 23422),
        REDIRECT_FOR_TOKEN_GET_OWNER_OF("getOwnerOfRedirect", 32728),
        REDIRECT_FOR_TOKEN_IS_APPROVED_FOR_ALL("isApprovedForAllRedirect", 33204),
        REDIRECT_FOR_TOKEN_NAME("nameNFTRedirect", 33615),
        REDIRECT_FOR_TOKEN_SYMBOL("symbolNFTRedirect", 33681),
        REDIRECT_FOR_TOKEN_NAME_NFT("nameRedirect", 23422),
        REDIRECT_FOR_TOKEN_SYMBOL_NFT("symbolRedirect", 23422),
        REDIRECT_FOR_TOKEN_SET_APPROVAL_FOR_ALL("setApprovalForAllRedirect", 737243),
        REDIRECT_FOR_TOKEN_TOTAL_SUPPLY("totalSupplyRedirect", 23422),
        REDIRECT_FOR_TOKEN_TOKEN_URI("tokenURIRedirect", 33997),
        REDIRECT_FOR_TOKEN_TRANSFER("transferRedirect", 47048),
        REDIRECT_FOR_TOKEN_TRANSFER_FROM("transferFromRedirect", 47350),
        REDIRECT_FOR_TOKEN_TRANSFER_FROM_NFT("transferFromNFTRedirect", 61457),
        SET_APPROVAL_FOR_ALL("setApprovalForAllExternal", 729608),
        SYMBOL("symbol", 27815),
        SYMBOL_NFT("symbolIERC721", 27814),
        TOTAL_SUPPLY("totalSupply", 27100),
        TOTAL_SUPPLY_NFT("totalSupplyIERC721", 27078),
        TOKEN_URI("tokenURI", 27856),
        TRANSFER_ERC("transfer", 41414),
        TRANSFER_FROM("transferFromExternal", 40822),
        TRANSFER_FROM_ERC("transferFrom", 39511),
        TRANSFER_FROM_NFT("transferFromNFTExternal", 54938),
        TRANSFER_NFT("transferNFTExternal", 53751),
        TRANSFER_NFTS("transferNFTsExternal", 57015),
        TRANSFER_TOKEN("transferTokenExternal", 39666),
        TRANSFER_TOKENS("transferTokensExternal", 42480),
        UNFREEZE_TOKEN("unfreezeTokenExternal", 39323),
        WIPE_TOKEN_ACCOUNT("wipeTokenAccountExternal", 39496),
        WIPE_NFT_ACCOUNT("wipeTokenAccountNFTExternal", 40394),
        PAUSE_TOKEN("pauseTokenExternal", 39112),
        PAUSE_UNPAUSE_NESTED_TOKEN("nestedPauseUnpauseTokenExternal", 54237),
        UNPAUSE_TOKEN("unpauseTokenExternal", 39112),
        UPDATE_TOKEN_EXPIRY("updateTokenExpiryInfoExternal", 39699),
        UPDATE_TOKEN_INFO("updateTokenInfoExternal", 74920),
        UPDATE_TOKEN_KEYS("updateTokenKeysExternal", 60427);

        private final String functionName;
        private final int actualGas;
    }
}
