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

import static com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.FUNGIBLE;
import static com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.FUNGIBLE_KYC_UNFROZEN;
import static com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.NFT;
import static com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.NFT_KYC_UNFROZEN;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ERC;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ESTIMATE_PRECOMPILE;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.PRECOMPILE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.ALLOWANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.ALLOWANCE_ERC;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.APPROVE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.APPROVE_ERC;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.APPROVE_FUNGIBLE_GET_ALLOWANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.APPROVE_FUNGIBLE_TOKEN_AND_TRANSFER;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.APPROVE_NFT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.APPROVE_NFT_GET_ALLOWANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.APPROVE_NFT_TOKEN_AND_TRANSFER_FROM;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.ASSOCIATE_TOKEN;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.ASSOCIATE_TOKENS;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.BALANCE_OF;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.BURN_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.BURN_NFT_GET_TOTAL_SUPPLY_AND_BALANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.BURN_TOKEN;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.CREATE_FUNGIBLE_TOKEN;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.CREATE_NFT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.CREATE_NFT_WITH_CUSTOM_FEES;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.CRYPTO_TRANSFER;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.CRYPTO_TRANSFER_HBARS;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.CRYPTO_TRANSFER_NFT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.DECIMALS;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.DELETE_TOKEN;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.DISSOCIATE_AND_ASSOCIATE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.DISSOCIATE_FUNGIBLE_TOKEN_AND_TRANSFER;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.DISSOCIATE_NFT_AND_TRANSFER;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.DISSOCIATE_TOKEN;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.DISSOCIATE_TOKENS;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.EXCHANGE_RATE_TINYBARS_TO_TINYCENTS;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.EXCHANGE_RATE_TINYCENTS_TO_TINYBARS;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.FREEZE_TOKEN;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.FREEZE_UNFREEZE_GET_STATUS;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_APPROVED;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_APPROVED_ERC;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_FUNGIBLE_TOKEN_INFO;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_NON_FUNGIBLE_TOKEN_INFO;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_TOKEN_DEFAULT_FREEZE_STATUS;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_TOKEN_DEFAULT_KYC_STATUS;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_TOKEN_EXPIRY_INFO;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_TOKEN_INFO;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_TOKEN_INFO_NFT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_TOKEN_KEY;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GET_TOKEN_TYPE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.GRANT_KYC;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.IS_APPROVED_FOR_ALL;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.IS_APPROVED_FOR_ALL_ERC;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.IS_FROZEN;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.IS_KYC;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.IS_TOKEN;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.MINT_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.MINT_NFT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.MINT_NFT_GET_TOTAL_SUPPLY_AND_BALANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.MINT_TOKEN;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.NAME;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.NAME_NFT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.NESTED_ASSOCIATE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.NESTED_FREEZE_UNFREEZE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.NESTED_GRANT_REVOKE_KYC;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.OWNER_OF;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.PAUSE_TOKEN;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.PAUSE_UNPAUSE_GET_STATUS;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.PAUSE_UNPAUSE_NESTED_TOKEN;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.PSEUDO_RANDOM_NUMBER;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.PSEUDO_RANDOM_SEED;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_ALLOWANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_APPROVE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_BALANCE_OF;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_DECIMALS;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_GET_OWNER_OF;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_IS_APPROVED_FOR_ALL;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_NAME;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_SET_APPROVAL_FOR_ALL;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_SYMBOL;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_TOKEN_URI;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_TRANSFER;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_TRANSFER_FROM;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REDIRECT_FOR_TOKEN_TRANSFER_FROM_NFT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.REVOKE_KYC;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.SET_APPROVAL_FOR_ALL;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.SYMBOL;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.SYMBOL_NFT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.TOKEN_URI;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.TOTAL_SUPPLY;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.TOTAL_SUPPLY_NFT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.TRANSFER_ERC;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.TRANSFER_FROM;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.TRANSFER_FROM_ERC;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.TRANSFER_FROM_NFT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.TRANSFER_NFT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.TRANSFER_NFTS;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.TRANSFER_TOKEN;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.TRANSFER_TOKENS;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.UNFREEZE_TOKEN;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.UNPAUSE_TOKEN;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.UPDATE_TOKEN_EXPIRY;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.UPDATE_TOKEN_INFO;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.UPDATE_TOKEN_KEYS;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.WIPE_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.WIPE_NFT_ACCOUNT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.WIPE_NFT_GET_TOTAL_SUPPLY_AND_BALANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.WIPE_TOKEN_ACCOUNT;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.TokenTransferListBuilder;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.accountAmount;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asAddress;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asAddressArray;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asByteArray;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asLongArray;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.nextBytes;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.nftAmount;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.to32BytesString;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.util.Strings;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.AccountUpdateTransaction;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TokenUpdateTransaction;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient.ExecuteContractResult;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum;
import com.hedera.mirror.test.e2e.acceptance.props.ContractCallRequest;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
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
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;

@CustomLog
@RequiredArgsConstructor
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

    @Given("I create estimate precompile contract with 0 balance")
    public void createNewEstimateContract() throws IOException {
        deployedEstimatePrecompileContract = getContract(ESTIMATE_PRECOMPILE);
        estimatePrecompileContractSolidityAddress =
                deployedEstimatePrecompileContract.contractId().toSolidityAddress();
        admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        receiverAccount = accountClient.getAccount(AccountClient.AccountNameEnum.BOB);
        secondReceiverAccount = accountClient.getAccount(AccountNameEnum.DAVE);
        receiverAccountAlias = receiverAccount.getPublicKey().toEvmAddress().toString();
    }

    @Given("I create erc test contract with 0 balance")
    public void createNewERCContract() throws IOException {
        deployedErcTestContract = getContract(ERC);
        ercTestContractSolidityAddress = deployedErcTestContract.contractId().toSolidityAddress();
    }

    @Given("I get exchange rates")
    public void getExchangeRate() {
        exchangeRates = mirrorClient.getExchangeRates();
    }

    @Given("I successfully create Precompile contract with 0 balance")
    public void createNewPrecompileTestContract() throws IOException {
        deployedPrecompileContract = getContract(PRECOMPILE);
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
        tokenClient.mint(nonFungibleTokenId, nextBytes(4));
        networkTransactionResponse = tokenClient.mint(nonFungibleKycUnfrozenTokenId, nextBytes(4));
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
        var data = encodeData(
                ESTIMATE_PRECOMPILE, ASSOCIATE_TOKEN, asAddress(receiverAccountAlias), asAddress(fungibleTokenId));
        validateGasEstimation(data, ASSOCIATE_TOKEN, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with associate function for NFT")
    public void associateFunctionNFTEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, ASSOCIATE_TOKEN, asAddress(receiverAccountAlias), asAddress(nonFungibleTokenId));
        validateGasEstimation(data, ASSOCIATE_TOKEN, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with dissociate token function without association for fungible token")
    public void dissociateFunctionEstimateGasNegative() {
        // attempt to call dissociate function without having association
        // expecting status 400/revert
        var data = encodeData(
                ESTIMATE_PRECOMPILE, DISSOCIATE_TOKEN, asAddress(receiverAccountAlias), asAddress(fungibleTokenId));

        assertContractCallReturnsBadRequest(data, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with dissociate token function without association for NFT")
    public void dissociateFunctionNFTEstimateGasNegative() {
        // attempt to call dissociate function without having association
        // expecting status 400/revert
        var data = encodeData(
                ESTIMATE_PRECOMPILE, DISSOCIATE_TOKEN, asAddress(receiverAccountAlias), asAddress(nonFungibleTokenId));

        assertContractCallReturnsBadRequest(data, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with nested associate function that executes it twice for fungible token")
    public void nestedAssociateFunctionEstimateGas() {
        // attempt to call associate function twice
        // expecting a revert
        var data = encodeData(
                ESTIMATE_PRECOMPILE, NESTED_ASSOCIATE, asAddress(receiverAccountAlias), asAddress(fungibleTokenId));

        assertContractCallReturnsBadRequest(data, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with nested associate function that executes it twice for NFT")
    public void nestedAssociateFunctionNFTEstimateGas() {
        // attempt to call associate function twice
        // expecting a revert
        var data = encodeData(
                ESTIMATE_PRECOMPILE, NESTED_ASSOCIATE, asAddress(receiverAccountAlias), asAddress(nonFungibleTokenId));

        assertContractCallReturnsBadRequest(data, estimatePrecompileContractSolidityAddress);
    }

    @And("I associate the receiver account with the fungible token")
    public void associateReceiverWithFungibleEstimateGas() {
        // associating the token with the token address
        networkTransactionResponse = tokenClient.associate(receiverAccount, fungibleTokenId);
    }

    @Then("I call estimateGas with dissociate token function for fungible token")
    public void dissociateFunctionEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, DISSOCIATE_TOKEN, asAddress(receiverAccountAlias), asAddress(fungibleTokenId));

        validateGasEstimation(data, DISSOCIATE_TOKEN, estimatePrecompileContractSolidityAddress);
    }

    @And("I associate the receiver account with the NFT")
    public void associateReceiverWithNonFungibleEstimateGas() {
        // associating the NFT with the address
        networkTransactionResponse = tokenClient.associate(receiverAccount, nonFungibleTokenId);
    }

    @Then("I call estimateGas with dissociate token function for NFT")
    public void dissociateFunctionNFTEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, DISSOCIATE_TOKEN, asAddress(receiverAccountAlias), asAddress(nonFungibleTokenId));

        validateGasEstimation(data, DISSOCIATE_TOKEN, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with dissociate and associate nested function for fungible token")
    public void dissociateAndAssociatedEstimateGas() {
        // token is already associated
        // attempting to execute nested dissociate and associate function
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                DISSOCIATE_AND_ASSOCIATE,
                asAddress(receiverAccountAlias),
                asAddress(fungibleTokenId));

        validateGasEstimation(data, DISSOCIATE_AND_ASSOCIATE, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with dissociate and associate nested function for NFT")
    public void dissociateAndAssociatedNFTEstimateGas() {
        // token is already associated
        // attempting to execute nested dissociate and associate function
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                DISSOCIATE_AND_ASSOCIATE,
                asAddress(receiverAccountAlias),
                asAddress(nonFungibleTokenId));

        validateGasEstimation(data, DISSOCIATE_AND_ASSOCIATE, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with approve function without association")
    public void approveWithoutAssociationEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                APPROVE,
                asAddress(fungibleTokenId),
                asAddress(receiverAccountAlias),
                new BigInteger("10"));

        assertContractCallReturnsBadRequest(data, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with setApprovalForAll function without association")
    public void setApprovalForAllWithoutAssociationEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                SET_APPROVAL_FOR_ALL,
                asAddress(nonFungibleTokenId),
                asAddress(receiverAccountAlias),
                true);

        assertContractCallReturnsBadRequest(data, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with approveNFT function without association")
    public void approveNonFungibleWithoutAssociationEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                APPROVE_NFT,
                asAddress(nonFungibleTokenId),
                asAddress(receiverAccountAlias),
                new BigInteger("1"));

        assertContractCallReturnsBadRequest(data, estimatePrecompileContractSolidityAddress);
    }

    @Then("I associate contracts with the tokens and approve all nft serials")
    public void associateTokensWithContract() throws InvalidProtocolBufferException {
        // In order to execute Approve, approveNFT, ercApprove we need to associate the contract with the token
        tokenClient.associate(deployedErcTestContract.contractId(), fungibleTokenId);
        tokenClient.associate(deployedEstimatePrecompileContract.contractId(), fungibleTokenId);
        tokenClient.associate(deployedEstimatePrecompileContract.contractId(), nonFungibleKycUnfrozenTokenId);
        tokenClient.associate(deployedPrecompileContract.contractId(), fungibleTokenId);
        tokenClient.associate(deployedPrecompileContract.contractId(), nonFungibleTokenId);
        tokenClient.associate(deployedPrecompileContract.contractId(), nonFungibleKycUnfrozenTokenId);

        // approve is also needed for the approveNFT function
        accountClient.approveNftAllSerials(nonFungibleKycUnfrozenTokenId, deployedPrecompileContract.contractId());
        networkTransactionResponse = accountClient.approveNftAllSerials(
                nonFungibleKycUnfrozenTokenId, deployedEstimatePrecompileContract.contractId());
    }

    @Then("I call estimateGas with ERC approve function")
    public void ercApproveEstimateGas() {
        var data = encodeData(
                ERC, APPROVE_ERC, asAddress(fungibleTokenId), asAddress(receiverAccountAlias), new BigInteger("10"));

        validateGasEstimation(data, APPROVE_ERC, ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with setApprovalForAll function")
    public void setApprovalForAllEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                SET_APPROVAL_FOR_ALL,
                asAddress(nonFungibleKycUnfrozenTokenId),
                asAddress(receiverAccountAlias),
                true);

        validateGasEstimation(data, SET_APPROVAL_FOR_ALL, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with transferFrom function without approval")
    public void transferFromEstimateGasWithoutApproval() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                TRANSFER_FROM,
                asAddress(fungibleKycUnfrozenTokenId),
                asAddress(admin),
                asAddress(receiverAccountAlias),
                new BigInteger("5"));

        assertContractCallReturnsBadRequest(data, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with ERC transferFrom function without approval")
    public void ercTransferFromEstimateGasWithoutApproval() {
        var data = encodeData(
                ERC,
                TRANSFER_FROM_ERC,
                asAddress(fungibleTokenId),
                asAddress(admin),
                asAddress(receiverAccountAlias),
                new BigInteger("10"));

        assertContractCallReturnsBadRequest(data, ercTestContractSolidityAddress);
    }

    @And("I approve the contract to use fungible token")
    public void approveFungibleWithReceiver() {
        final var ercTestContractId = AccountId.fromSolidityAddress(ercTestContractSolidityAddress);
        networkTransactionResponse = accountClient.approveToken(fungibleTokenId, ercTestContractId, 10);
        final var precompileTestContractId = AccountId.fromSolidityAddress(precompileTestContractSolidityAddress);
        networkTransactionResponse = accountClient.approveToken(fungibleTokenId, precompileTestContractId, 10);
    }

    @Then("I call estimateGas with ERC transferFrom function")
    public void ercTransferFromEstimateGas() {
        var data = encodeData(
                ERC,
                TRANSFER_FROM_ERC,
                asAddress(fungibleTokenId),
                asAddress(admin),
                asAddress(receiverAccountAlias),
                new BigInteger("5"));

        validateGasEstimation(data, TRANSFER_FROM_ERC, ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with transferFrom function with more than the approved allowance")
    public void transferFromExceedAllowanceEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                TRANSFER_FROM,
                asAddress(fungibleKycUnfrozenTokenId),
                asAddress(admin),
                asAddress(receiverAccountAlias),
                new BigInteger("500"));

        assertContractCallReturnsBadRequest(data, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with ERC transferFrom function with more than the approved allowance")
    public void ercTransferFromExceedsAllowanceEstimateGas() {
        var data = encodeData(
                ERC,
                TRANSFER_FROM_ERC,
                asAddress(fungibleKycUnfrozenTokenId),
                asAddress(admin),
                asAddress(receiverAccountAlias),
                new BigInteger("500"));

        assertContractCallReturnsBadRequest(data, ercTestContractSolidityAddress);
    }

    @And("I approve receiver account to use the NFT with id 1")
    public void approveNonFungibleWithReceiver() {
        NftId id = new NftId(nonFungibleTokenId, firstNftSerialNumber);
        networkTransactionResponse = accountClient.approveNft(id, receiverAccount.getAccountId());
    }

    @Then("I call estimateGas with transferFromNFT with invalid serial number")
    public void transferFromNFTInvalidSerialEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                TRANSFER_FROM_NFT,
                asAddress(nonFungibleKycUnfrozenTokenId),
                asAddress(admin),
                asAddress(receiverAccountAlias),
                new BigInteger("50"));

        assertContractCallReturnsBadRequest(data, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with transferNFT function")
    public void transferNFTEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                TRANSFER_NFT,
                asAddress(nonFungibleTokenId),
                asAddress(admin),
                asAddress(receiverAccountAlias),
                1L);

        validateGasEstimation(data, TRANSFER_NFT, estimatePrecompileContractSolidityAddress);
    }

    @And("I approve the receiver account to use fungible token and transfer fungible token to the erc contract")
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
        var data = encodeData(
                ERC, TRANSFER_ERC, asAddress(fungibleTokenId), asAddress(receiverAccountAlias), new BigInteger("5"));

        validateGasEstimation(data, TRANSFER_ERC, ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with associateTokens function for fungible tokens")
    public void associateTokensEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                ASSOCIATE_TOKENS,
                asAddress(secondReceiverAccount),
                asAddressArray(Arrays.asList(
                        fungibleTokenId.toSolidityAddress(), fungibleKycUnfrozenTokenId.toSolidityAddress())));

        validateGasEstimation(data, ASSOCIATE_TOKENS, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with associateTokens function for NFTs")
    public void associateNFTEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                ASSOCIATE_TOKENS,
                asAddress(secondReceiverAccount),
                asAddressArray(Arrays.asList(
                        nonFungibleKycUnfrozenTokenId.toSolidityAddress(), nonFungibleTokenId.toSolidityAddress())));

        validateGasEstimation(data, ASSOCIATE_TOKENS, estimatePrecompileContractSolidityAddress);
    }

    @And("I associate the fungible_kyc_unfrozen token with the receiver account")
    public void associateFungibleKycUnfrozenTokenWithReceiverAccount() {
        networkTransactionResponse = tokenClient.associate(receiverAccount, fungibleKycUnfrozenTokenId);
    }

    @Then("I call estimateGas with dissociateTokens function for fungible tokens")
    public void dissociateTokensEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                DISSOCIATE_TOKENS,
                asAddress(receiverAccountAlias),
                asAddressArray(Arrays.asList(
                        fungibleTokenId.toSolidityAddress(), fungibleKycUnfrozenTokenId.toSolidityAddress())));

        validateGasEstimation(data, DISSOCIATE_TOKENS, estimatePrecompileContractSolidityAddress);
    }

    @And("I associate the nft_kyc_unfrozen with the receiver account")
    public void associateNonFungibleKycUnfrozenTokenWithReceiverAccount() {
        networkTransactionResponse = tokenClient.associate(receiverAccount, nonFungibleKycUnfrozenTokenId);
    }

    @Then("I call estimateGas with dissociateTokens function for NFTs")
    public void dissociateNFTEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                DISSOCIATE_TOKENS,
                asAddress(receiverAccountAlias),
                asAddressArray(Arrays.asList(
                        nonFungibleKycUnfrozenTokenId.toSolidityAddress(), nonFungibleTokenId.toSolidityAddress())));

        validateGasEstimation(data, DISSOCIATE_TOKENS, estimatePrecompileContractSolidityAddress);
    }

    @And("I associate and approve the second receiver to use the fungible_kyc_unfrozen token")
    public void associateAndApproveFungibleKycUnfrozenTokenWithReceiverAccount() {
        tokenClient.associate(secondReceiverAccount, fungibleTokenId);
        networkTransactionResponse =
                accountClient.approveToken(fungibleTokenId, secondReceiverAccount.getAccountId(), 10);
    }

    @Then("I call estimateGas with transferTokens function")
    public void transferTokensEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                TRANSFER_TOKENS,
                asAddress(fungibleTokenId),
                asAddressArray(Arrays.asList(
                        admin.getAccountId().toSolidityAddress(),
                        receiverAccountAlias,
                        secondReceiverAccount.getAccountId().toSolidityAddress())),
                new long[] {-6L, 3L, 3L});

        validateGasEstimation(data, TRANSFER_TOKENS, estimatePrecompileContractSolidityAddress);
    }

    @And("I mint a new NFT and approve second receiver account to all serial numbers")
    public void mintAndApproveAllSerialsToSecondReceiver() {
        tokenClient.mint(nonFungibleTokenId, nextBytes(4));
        accountClient.approveNftAllSerials(nonFungibleTokenId, receiverAccount.getAccountId());
        networkTransactionResponse =
                accountClient.approveNftAllSerials(nonFungibleTokenId, secondReceiverAccount.getAccountId());
    }

    @Then("I call estimateGas with transferNFTs function")
    public void transferNFTsEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                TRANSFER_NFTS,
                asAddress(nonFungibleTokenId),
                asAddressArray(List.of(admin.getAccountId().toSolidityAddress())),
                asAddressArray(Arrays.asList(
                        receiverAccountAlias,
                        secondReceiverAccount.getAccountId().toSolidityAddress())),
                new long[] {1, 2});

        validateGasEstimation(data, TRANSFER_NFTS, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with cryptoTransfer function for hbars")
    public void cryptoTransferHbarEstimateGas() {
        var senderTransfer = accountAmount(admin.getAccountId().toSolidityAddress(), -10L, false);
        var receiverTransfer = accountAmount(receiverAccountAlias, 10L, false);
        var args = Tuple.of((Object) new Tuple[] {senderTransfer, receiverTransfer});
        var data = encodeData(ESTIMATE_PRECOMPILE, CRYPTO_TRANSFER_HBARS, args, EMPTY_TUPLE_ARRAY);
        validateGasEstimation(data, CRYPTO_TRANSFER_HBARS, estimatePrecompileContractSolidityAddress);
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
        var data = encodeData(
                ESTIMATE_PRECOMPILE, CRYPTO_TRANSFER_NFT, Tuple.of((Object) EMPTY_TUPLE_ARRAY), tokenTransferList);
        validateGasEstimation(data, CRYPTO_TRANSFER_NFT, estimatePrecompileContractSolidityAddress);
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
        var data = encodeData(
                ESTIMATE_PRECOMPILE, CRYPTO_TRANSFER, Tuple.of((Object) EMPTY_TUPLE_ARRAY), tokenTransferList);
        validateGasEstimation(data, CRYPTO_TRANSFER, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with burnToken function for fungible token")
    public void burnFungibleTokenEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                BURN_TOKEN,
                asAddress(fungibleKycUnfrozenTokenId),
                1L,
                asLongArray(new ArrayList<>()));

        validateGasEstimation(data, BURN_TOKEN, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with burnToken function for NFT")
    public void burnNFTEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                BURN_TOKEN,
                asAddress(nonFungibleKycUnfrozenTokenId),
                0L,
                asLongArray(List.of(1L)));

        validateGasEstimation(data, BURN_TOKEN, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with CreateFungibleToken function")
    public void createFungibleTokenEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, CREATE_FUNGIBLE_TOKEN, asAddress(admin));

        Consumer<Boolean> estimateFunction = current -> validateGasEstimationForCreateToken(
                data, CREATE_FUNGIBLE_TOKEN.getActualGas(), calculateCreateTokenFee(1, current));
        executeAndRetryWithNextExchangeRates(estimateFunction);
    }

    @Then("I call estimateGas with CreateNFT function")
    public void createNFTEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, CREATE_NFT, asAddress(admin));

        Consumer<Boolean> estimateFunction = current -> validateGasEstimationForCreateToken(
                data, CREATE_FUNGIBLE_TOKEN.getActualGas(), calculateCreateTokenFee(1, current));
        executeAndRetryWithNextExchangeRates(estimateFunction);
    }

    @Then("I call estimateGas with CreateFungibleToken function with custom fees")
    public void createFungibleTokenWithCustomFeesEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES,
                asAddress(admin),
                asAddress(fungibleKycUnfrozenTokenId));

        Consumer<Boolean> estimateFunction = current -> validateGasEstimationForCreateToken(
                data, CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES.getActualGas(), calculateCreateTokenFee(2, current));
        executeAndRetryWithNextExchangeRates(estimateFunction);
    }

    @Then("I call estimateGas with CreateNFT function with custom fees")
    public void createNFTWithCustomFeesEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                CREATE_NFT_WITH_CUSTOM_FEES,
                asAddress(admin),
                asAddress(nonFungibleKycUnfrozenTokenId));

        Consumer<Boolean> estimateFunction = current -> validateGasEstimationForCreateToken(
                data, CREATE_NFT_WITH_CUSTOM_FEES.getActualGas(), calculateCreateTokenFee(2, current));
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
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                WIPE_TOKEN_ACCOUNT,
                asAddress(fungibleTokenId),
                asAddress(receiverAccountAlias),
                1L);

        validateGasEstimation(data, WIPE_TOKEN_ACCOUNT, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with WipeTokenAccount function with invalid amount")
    public void wipeTokenAccountInvalidAmountEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                WIPE_TOKEN_ACCOUNT,
                asAddress(fungibleKycUnfrozenTokenId),
                asAddress(receiverAccountAlias),
                100000000000000000L);

        assertContractCallReturnsBadRequest(data, estimatePrecompileContractSolidityAddress);
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
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                WIPE_NFT_ACCOUNT,
                asAddress(nonFungibleTokenId),
                asAddress(receiverAccountAlias),
                asLongArray(List.of(1L)));

        validateGasEstimation(data, WIPE_NFT_ACCOUNT, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with WipeNFTAccount function with invalid serial number")
    public void wipeNFTAccountInvalidSerialNumberEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                WIPE_NFT_ACCOUNT,
                asAddress(nonFungibleKycUnfrozenTokenId),
                asAddress(receiverAccountAlias),
                asLongArray(List.of(66L)));

        assertContractCallReturnsBadRequest(data, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with GrantKYC function for fungible token")
    public void grantKYCFungibleEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, GRANT_KYC, asAddress(fungibleKycUnfrozenTokenId), asAddress(receiverAccountAlias));

        validateGasEstimation(data, GRANT_KYC, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with GrantKYC function for NFT")
    public void grantKYCNonFungibleEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                GRANT_KYC,
                asAddress(nonFungibleKycUnfrozenTokenId),
                asAddress(receiverAccountAlias));

        validateGasEstimation(data, GRANT_KYC, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with RevokeTokenKYC function for fungible token")
    public void revokeTokenKYCEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                REVOKE_KYC,
                asAddress(fungibleKycUnfrozenTokenId),
                asAddress(receiverAccountAlias));

        validateGasEstimation(data, REVOKE_KYC, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with RevokeTokenKYC function for NFT")
    public void revokeTokenKYCNonFungibleEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                REVOKE_KYC,
                asAddress(nonFungibleKycUnfrozenTokenId),
                asAddress(receiverAccountAlias));

        validateGasEstimation(data, REVOKE_KYC, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with Grant and Revoke KYC nested function")
    public void nestedGrantRevokeKYCEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                NESTED_GRANT_REVOKE_KYC,
                asAddress(fungibleKycUnfrozenTokenId),
                asAddress(receiverAccountAlias));

        validateGasEstimation(data, NESTED_GRANT_REVOKE_KYC, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with Freeze function for fungible token")
    public void freezeFungibleEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                FREEZE_TOKEN,
                asAddress(fungibleKycUnfrozenTokenId),
                asAddress(receiverAccountAlias));

        validateGasEstimation(data, FREEZE_TOKEN, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with Freeze function for NFT")
    public void freezeNonFungibleEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                FREEZE_TOKEN,
                asAddress(nonFungibleKycUnfrozenTokenId),
                asAddress(receiverAccountAlias));

        validateGasEstimation(data, FREEZE_TOKEN, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with Unfreeze function for fungible token")
    public void unfreezeFungibleEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                UNFREEZE_TOKEN,
                asAddress(fungibleKycUnfrozenTokenId),
                asAddress(receiverAccountAlias));

        validateGasEstimation(data, UNFREEZE_TOKEN, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with Unfreeze function for NFT")
    public void unfreezeNonFungibleEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                UNFREEZE_TOKEN,
                asAddress(nonFungibleKycUnfrozenTokenId),
                asAddress(receiverAccountAlias));

        validateGasEstimation(data, UNFREEZE_TOKEN, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with nested Freeze and Unfreeze function for fungible token")
    public void nestedFreezeAndUnfreezeEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                NESTED_FREEZE_UNFREEZE,
                asAddress(fungibleKycUnfrozenTokenId),
                asAddress(receiverAccountAlias));

        validateGasEstimation(data, NESTED_FREEZE_UNFREEZE, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with nested Freeze and Unfreeze function for NFT")
    public void nestedFreezeAndUnfreezeNFTEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                NESTED_FREEZE_UNFREEZE,
                asAddress(nonFungibleKycUnfrozenTokenId),
                asAddress(receiverAccountAlias));

        validateGasEstimation(data, NESTED_FREEZE_UNFREEZE, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with delete function for Fungible token")
    public void deleteFungibleEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, DELETE_TOKEN, asAddress(fungibleKycUnfrozenTokenId));

        validateGasEstimation(data, DELETE_TOKEN, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with delete function for NFT")
    public void deleteNFTEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, DELETE_TOKEN, asAddress(nonFungibleKycUnfrozenTokenId));

        validateGasEstimation(data, DELETE_TOKEN, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with delete function for invalid token address")
    public void deleteTokenRandomAddressEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, DELETE_TOKEN, asAddress(RANDOM_ADDRESS));

        assertContractCallReturnsBadRequest(data, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with pause function for fungible token")
    public void pauseFungibleTokenPositiveEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, PAUSE_TOKEN, asAddress(fungibleKycUnfrozenTokenId));

        validateGasEstimation(data, PAUSE_TOKEN, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with pause function for NFT")
    public void pauseNFTPositiveEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, PAUSE_TOKEN, asAddress(nonFungibleKycUnfrozenTokenId));

        validateGasEstimation(data, PAUSE_TOKEN, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with unpause function for fungible token")
    public void unpauseFungibleTokenPositiveEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, UNPAUSE_TOKEN, asAddress(fungibleKycUnfrozenTokenId));

        validateGasEstimation(data, UNPAUSE_TOKEN, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with unpause function for NFT")
    public void unpauseNFTPositiveEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, UNPAUSE_TOKEN, asAddress(nonFungibleKycUnfrozenTokenId));

        validateGasEstimation(data, UNPAUSE_TOKEN, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas for nested pause and unpause function")
    public void pauseUnpauseFungibleTokenNestedCallEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, PAUSE_UNPAUSE_NESTED_TOKEN, asAddress(fungibleKycUnfrozenTokenId));

        validateGasEstimation(data, PAUSE_UNPAUSE_NESTED_TOKEN, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas for nested pause, unpause NFT function")
    public void pauseUnpauseNFTNestedCallEstimateGas() {
        var data =
                encodeData(ESTIMATE_PRECOMPILE, PAUSE_UNPAUSE_NESTED_TOKEN, asAddress(nonFungibleKycUnfrozenTokenId));

        validateGasEstimation(data, PAUSE_UNPAUSE_NESTED_TOKEN, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with updateTokenExpiryInfo function")
    public void updateTokenExpiryInfoEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, UPDATE_TOKEN_EXPIRY, asAddress(fungibleKycUnfrozenTokenId), asAddress(admin));

        validateGasEstimation(data, UPDATE_TOKEN_EXPIRY, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with updateTokenInfo function")
    public void updateTokenInfoEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, UPDATE_TOKEN_INFO, asAddress(fungibleKycUnfrozenTokenId), asAddress(admin));

        validateGasEstimation(data, UPDATE_TOKEN_INFO, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with updateTokenKeys function")
    public void updateTokenKeysEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, UPDATE_TOKEN_KEYS, asAddress(fungibleKycUnfrozenTokenId));

        validateGasEstimation(data, UPDATE_TOKEN_KEYS, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenExpiryInfo function")
    public void getTokenExpiryInfoEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, GET_TOKEN_EXPIRY_INFO, asAddress(fungibleKycUnfrozenTokenId));

        validateGasEstimation(data, GET_TOKEN_EXPIRY_INFO, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with isToken function")
    public void isTokenEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, IS_TOKEN, asAddress(fungibleKycUnfrozenTokenId));

        validateGasEstimation(data, IS_TOKEN, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenKey function for supply")
    public void getTokenKeySupplyEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, GET_TOKEN_KEY, asAddress(fungibleKycUnfrozenTokenId), new BigInteger("16"));

        validateGasEstimation(data, GET_TOKEN_KEY, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenKey function for KYC")
    public void getTokenKeyKYCEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, GET_TOKEN_KEY, asAddress(fungibleKycUnfrozenTokenId), new BigInteger("2"));

        validateGasEstimation(data, GET_TOKEN_KEY, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenKey function for freeze")
    public void getTokenKeyFreezeEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, GET_TOKEN_KEY, asAddress(fungibleKycUnfrozenTokenId), new BigInteger("4"));

        validateGasEstimation(data, GET_TOKEN_KEY, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenKey function for admin")
    public void getTokenKeyAdminEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, GET_TOKEN_KEY, asAddress(fungibleKycUnfrozenTokenId), new BigInteger("1"));

        validateGasEstimation(data, GET_TOKEN_KEY, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenKey function for wipe")
    public void getTokenKeyWipeEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, GET_TOKEN_KEY, asAddress(fungibleKycUnfrozenTokenId), new BigInteger("8"));

        validateGasEstimation(data, GET_TOKEN_KEY, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenKey function for fee")
    public void getTokenKeyFeeEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, GET_TOKEN_KEY, asAddress(fungibleKycUnfrozenTokenId), new BigInteger("32"));

        validateGasEstimation(data, GET_TOKEN_KEY, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenKey function for pause")
    public void getTokenKeyPauseEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, GET_TOKEN_KEY, asAddress(fungibleKycUnfrozenTokenId), new BigInteger("64"));

        validateGasEstimation(data, GET_TOKEN_KEY, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with ERC allowance function for fungible token")
    public void ercAllowanceFungibleEstimateGas() {
        var data = encodeData(
                ERC,
                ALLOWANCE_ERC,
                asAddress(fungibleKycUnfrozenTokenId),
                asAddress(admin),
                asAddress(receiverAccountAlias));

        validateGasEstimation(data, ALLOWANCE_ERC, ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getApproved function for NFT")
    public void getApprovedNonFungibleEstimateGas() {
        var data = encodeData(
                ESTIMATE_PRECOMPILE, GET_APPROVED, asAddress(nonFungibleKycUnfrozenTokenId), new BigInteger("1"));

        validateGasEstimation(data, GET_APPROVED, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with ERC getApproved function for NFT")
    public void ercGetApprovedNonFungibleEstimateGas() {
        var data = encodeData(ERC, GET_APPROVED_ERC, asAddress(nonFungibleKycUnfrozenTokenId), new BigInteger("1"));

        validateGasEstimation(data, GET_APPROVED_ERC, ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with isApprovedForAll function")
    public void isApprovedForAllEstimateGas() {
        // reminder: check with setApprovalForAll test-> there we have the contract associated so the test can work
        var data = encodeData(
                ESTIMATE_PRECOMPILE,
                IS_APPROVED_FOR_ALL,
                asAddress(nonFungibleKycUnfrozenTokenId),
                asAddress(admin),
                asAddress(receiverAccountAlias));

        validateGasEstimation(data, IS_APPROVED_FOR_ALL, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with ERC isApprovedForAll function")
    public void ercIsApprovedForAllEstimateGas() {
        // reminder: check with setApprovalForAll test-> there we have the contract associated so the test can work
        var data = encodeData(
                ERC,
                IS_APPROVED_FOR_ALL_ERC,
                asAddress(nonFungibleKycUnfrozenTokenId),
                asAddress(admin),
                asAddress(receiverAccountAlias));

        validateGasEstimation(data, IS_APPROVED_FOR_ALL_ERC, ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with name function for fungible token")
    public void nameEstimateGas() {
        var data = encodeData(ERC, NAME, asAddress(fungibleKycUnfrozenTokenId));

        validateGasEstimation(data, NAME, ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with name function for NFT")
    public void nameNonFungibleEstimateGas() {
        var data = encodeData(ERC, NAME_NFT, asAddress(nonFungibleKycUnfrozenTokenId));

        validateGasEstimation(data, NAME_NFT, ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with symbol function for fungible token")
    public void symbolEstimateGas() {
        var data = encodeData(ERC, SYMBOL, asAddress(fungibleKycUnfrozenTokenId));

        validateGasEstimation(data, SYMBOL, ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with symbol function for NFT")
    public void symbolNonFungibleEstimateGas() {
        var data = encodeData(ERC, SYMBOL_NFT, asAddress(nonFungibleKycUnfrozenTokenId));

        validateGasEstimation(data, SYMBOL_NFT, ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with decimals function for fungible token")
    public void decimalsEstimateGas() {
        var data = encodeData(ERC, DECIMALS, asAddress(fungibleKycUnfrozenTokenId));

        validateGasEstimation(data, DECIMALS, ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with totalSupply function for fungible token")
    public void totalSupplyEstimateGas() {
        var data = encodeData(ERC, TOTAL_SUPPLY, asAddress(fungibleKycUnfrozenTokenId));

        validateGasEstimation(data, TOTAL_SUPPLY, ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with totalSupply function for NFT")
    public void totalSupplyNonFungibleEstimateGas() {
        var data = encodeData(ERC, TOTAL_SUPPLY_NFT, asAddress(nonFungibleKycUnfrozenTokenId));

        validateGasEstimation(data, TOTAL_SUPPLY_NFT, ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with ownerOf function for NFT")
    public void ownerOfEstimateGas() {
        var data = encodeData(ERC, OWNER_OF, asAddress(nonFungibleKycUnfrozenTokenId), new BigInteger("1"));

        validateGasEstimation(data, OWNER_OF, ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with tokenURI function for NFT")
    public void tokenURIEstimateGas() {
        var data = encodeData(ERC, TOKEN_URI, asAddress(nonFungibleKycUnfrozenTokenId), new BigInteger("1"));

        validateGasEstimation(data, TOKEN_URI, ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getFungibleTokenInfo function")
    public void getFungibleTokenInfoEstimateGas() {
        var data = encodeData(PRECOMPILE, GET_FUNGIBLE_TOKEN_INFO, asAddress(fungibleKycUnfrozenTokenId));

        validateGasEstimation(data, GET_FUNGIBLE_TOKEN_INFO, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getNonFungibleTokenInfo function")
    public void getNonFungibleTokenInfoEstimateGas() {
        var data = encodeData(PRECOMPILE, GET_NON_FUNGIBLE_TOKEN_INFO, asAddress(nonFungibleKycUnfrozenTokenId), 1L);

        validateGasEstimation(data, GET_NON_FUNGIBLE_TOKEN_INFO, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenInfo function for fungible")
    public void getTokenInfoEstimateGas() {
        var data = encodeData(PRECOMPILE, GET_TOKEN_INFO, asAddress(fungibleKycUnfrozenTokenId));

        validateGasEstimation(data, GET_TOKEN_INFO, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenInfo function for NFT")
    public void getTokenInfoNonFungibleEstimateGas() {
        var data = encodeData(PRECOMPILE, GET_TOKEN_INFO_NFT, asAddress(nonFungibleKycUnfrozenTokenId));

        validateGasEstimation(data, GET_TOKEN_INFO_NFT, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenDefaultFreezeStatus function for fungible token")
    public void getTokenDefaultFreezeStatusFungibleEstimateGas() {
        var data = encodeData(PRECOMPILE, GET_TOKEN_DEFAULT_FREEZE_STATUS, asAddress(fungibleKycUnfrozenTokenId));

        validateGasEstimation(data, GET_TOKEN_DEFAULT_FREEZE_STATUS, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenDefaultFreezeStatus function for NFT")
    public void getTokenDefaultFreezeStatusNonFungibleEstimateGas() {
        var data = encodeData(PRECOMPILE, GET_TOKEN_DEFAULT_FREEZE_STATUS, asAddress(nonFungibleKycUnfrozenTokenId));

        validateGasEstimation(data, GET_TOKEN_DEFAULT_FREEZE_STATUS, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenDefaultKycStatus function for fungible token")
    public void getTokenDefaultKycStatusFungibleEstimateGas() {
        var data = encodeData(PRECOMPILE, GET_TOKEN_DEFAULT_KYC_STATUS, asAddress(fungibleKycUnfrozenTokenId));

        validateGasEstimation(data, GET_TOKEN_DEFAULT_KYC_STATUS, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenDefaultKycStatus function for NFT")
    public void getTokenDefaultKycStatusNonFungibleEstimateGas() {
        var data = encodeData(PRECOMPILE, GET_TOKEN_DEFAULT_KYC_STATUS, asAddress(nonFungibleKycUnfrozenTokenId));

        validateGasEstimation(data, GET_TOKEN_DEFAULT_KYC_STATUS, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with isKyc function for fungible token")
    public void isKycFungibleEstimateGas() {
        var data =
                encodeData(PRECOMPILE, IS_KYC, asAddress(fungibleKycUnfrozenTokenId), asAddress(receiverAccountAlias));

        validateGasEstimation(data, IS_KYC, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with isKyc function for NFT")
    public void isKycNonFungibleEstimateGas() {
        var data = encodeData(
                PRECOMPILE, IS_KYC, asAddress(nonFungibleKycUnfrozenTokenId), asAddress(receiverAccountAlias));

        validateGasEstimation(data, IS_KYC, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with isFrozen function for fungible token")
    public void isFrozenFungibleEstimateGas() {
        var data = encodeData(
                PRECOMPILE, IS_FROZEN, asAddress(fungibleKycUnfrozenTokenId), asAddress(receiverAccountAlias));

        validateGasEstimation(data, IS_FROZEN, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with isFrozen function for NFT")
    public void isFrozenNonFungibleEstimateGas() {
        var data = encodeData(
                PRECOMPILE, IS_FROZEN, asAddress(nonFungibleKycUnfrozenTokenId), asAddress(receiverAccountAlias));

        validateGasEstimation(data, IS_FROZEN, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenType function for fungible token")
    public void getTokenTypeFungibleEstimateGas() {
        var data = encodeData(PRECOMPILE, GET_TOKEN_TYPE, asAddress(fungibleKycUnfrozenTokenId));

        validateGasEstimation(data, GET_TOKEN_TYPE, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenType function for NFT")
    public void getTokenTypeNonFungibleEstimateGas() {
        var data = encodeData(PRECOMPILE, GET_TOKEN_TYPE, asAddress(nonFungibleKycUnfrozenTokenId));

        validateGasEstimation(data, GET_TOKEN_TYPE, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect balanceOf function")
    public void redirectBalanceOfEstimateGas() {
        var data = encodeData(
                PRECOMPILE, REDIRECT_FOR_TOKEN_BALANCE_OF, asAddress(fungibleKycUnfrozenTokenId), asAddress(admin));

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_BALANCE_OF, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect name function")
    public void redirectNameEstimateGas() {
        var data = encodeData(PRECOMPILE, REDIRECT_FOR_TOKEN_NAME, asAddress(fungibleKycUnfrozenTokenId));

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_NAME, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect symbol function")
    public void redirectSymbolEstimateGas() {
        var data = encodeData(PRECOMPILE, REDIRECT_FOR_TOKEN_SYMBOL, asAddress(fungibleKycUnfrozenTokenId));

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_SYMBOL, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect name function for NFT")
    public void redirectNameNonFungibleEstimateGas() {
        var data = encodeData(PRECOMPILE, REDIRECT_FOR_TOKEN_NAME, asAddress(nonFungibleKycUnfrozenTokenId));

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_NAME, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect symbol function for NFT")
    public void redirectSymbolNonFungibleEstimateGas() {
        var data = encodeData(PRECOMPILE, REDIRECT_FOR_TOKEN_SYMBOL, asAddress(nonFungibleKycUnfrozenTokenId));

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_SYMBOL, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect decimals function")
    public void redirectDecimalsEstimateGas() {
        var data = encodeData(PRECOMPILE, REDIRECT_FOR_TOKEN_DECIMALS, asAddress(fungibleKycUnfrozenTokenId));

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_DECIMALS, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect allowance function")
    public void redirectAllowanceEstimateGas() {
        var data = encodeData(
                PRECOMPILE,
                REDIRECT_FOR_TOKEN_ALLOWANCE,
                asAddress(fungibleKycUnfrozenTokenId),
                asAddress(admin),
                asAddress(receiverAccountAlias));

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_ALLOWANCE, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect getOwnerOf function")
    public void redirectGetOwnerOfEstimateGas() {
        var data = encodeData(
                PRECOMPILE,
                REDIRECT_FOR_TOKEN_GET_OWNER_OF,
                asAddress(nonFungibleKycUnfrozenTokenId),
                new BigInteger("1"));

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_GET_OWNER_OF, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect tokenURI function")
    public void redirectTokenURIEstimateGas() {
        var data = encodeData(
                PRECOMPILE,
                REDIRECT_FOR_TOKEN_TOKEN_URI,
                asAddress(nonFungibleKycUnfrozenTokenId),
                new BigInteger("1"));

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_TOKEN_URI, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect isApprovedForAll function")
    public void redirectIsApprovedForAllEstimateGas() {
        var data = encodeData(
                PRECOMPILE,
                REDIRECT_FOR_TOKEN_IS_APPROVED_FOR_ALL,
                asAddress(nonFungibleKycUnfrozenTokenId),
                asAddress(admin),
                asAddress(receiverAccountAlias));

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_IS_APPROVED_FOR_ALL, precompileTestContractSolidityAddress);
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
        var data = encodeData(
                PRECOMPILE,
                REDIRECT_FOR_TOKEN_TRANSFER,
                asAddress(fungibleTokenId),
                asAddress(receiverAccountAlias),
                new BigInteger("5"));

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_TRANSFER, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect transferFrom function")
    public void redirectTransferFromEstimateGas() {
        var data = encodeData(
                PRECOMPILE,
                REDIRECT_FOR_TOKEN_TRANSFER_FROM,
                asAddress(fungibleTokenId),
                asAddress(admin),
                asAddress(receiverAccountAlias),
                new BigInteger("5"));

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_TRANSFER_FROM, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect approve function")
    public void redirectApproveEstimateGas() {
        var data = encodeData(
                PRECOMPILE,
                REDIRECT_FOR_TOKEN_APPROVE,
                asAddress(fungibleTokenId),
                asAddress(receiverAccountAlias),
                new BigInteger("10"));

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_APPROVE, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect transferFrom NFT function")
    public void redirectTransferFromNonFungibleEstimateGas() {
        var data = encodeData(
                PRECOMPILE,
                REDIRECT_FOR_TOKEN_TRANSFER_FROM_NFT,
                asAddress(nonFungibleTokenId),
                asAddress(admin),
                asAddress(receiverAccountAlias),
                new BigInteger("2"));

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_TRANSFER_FROM_NFT, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirect setApprovalForAll function")
    public void redirectSetApprovalForAllEstimateGas() {
        var data = encodeData(
                PRECOMPILE,
                REDIRECT_FOR_TOKEN_SET_APPROVAL_FOR_ALL,
                asAddress(nonFungibleKycUnfrozenTokenId),
                asAddress(receiverAccountAlias),
                true);

        validateGasEstimation(data, REDIRECT_FOR_TOKEN_SET_APPROVAL_FOR_ALL, precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with pseudo random seed")
    public void pseudoRandomSeedEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, PSEUDO_RANDOM_SEED);

        validateGasEstimation(data, PSEUDO_RANDOM_SEED, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with pseudo random number")
    public void pseudoRandomNumberEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, PSEUDO_RANDOM_NUMBER, 500L, 1000L);

        validateGasEstimation(data, PSEUDO_RANDOM_NUMBER, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with exchange rate tinycents to tinybars")
    public void exchangeRateTinyCentsToTinyBarsEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, EXCHANGE_RATE_TINYCENTS_TO_TINYBARS, new BigInteger("100"));

        validateGasEstimation(data, EXCHANGE_RATE_TINYCENTS_TO_TINYBARS, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with exchange rate tinybars to tinycents")
    public void exchangeRateTinyBarsToTinyCentsEstimateGas() {
        var data = encodeData(ESTIMATE_PRECOMPILE, EXCHANGE_RATE_TINYBARS_TO_TINYCENTS, new BigInteger("100"));

        validateGasEstimation(data, EXCHANGE_RATE_TINYBARS_TO_TINYCENTS, estimatePrecompileContractSolidityAddress);
    }

    private void executeContractTransaction(
            DeployedContract deployedContract, int gas, ContractMethods contractMethods, byte[] parameters) {

        ExecuteContractResult executeContractResult = contractClient.executeContract(
                deployedContract.contractId(), gas, contractMethods.getSelector(), parameters, null);

        networkTransactionResponse = executeContractResult.networkTransactionResponse();
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    public int validateAndReturnGas(byte[] data, ContractMethods contractMethods, String contractAddress) {
        var encodedData = Strings.encode(ByteBuffer.wrap(data));
        var response = estimateContract(encodedData, contractAddress);
        var estimateGasValue = response.getResultAsNumber().intValue();
        assertWithinDeviation(contractMethods.getActualGas(), estimateGasValue, lowerDeviation, upperDeviation);
        return estimateGasValue;
    }

    @Then("I call estimateGas with balanceOf function for {token} and verify the estimated gas against HAPI")
    public void executeBalanceOfFunctionWithLimitedGas(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var data = encodeDataToByteArray(ERC, BALANCE_OF, asAddress(tokenId), asAddress(admin));
        var estimateGasValue = validateAndReturnGas(data, BALANCE_OF, ercTestContractSolidityAddress);
        executeContractTransaction(deployedErcTestContract, estimateGasValue, BALANCE_OF, data);
    }

    @And("I update the account and token keys")
    public void updateAccountAndTokenKeys() throws PrecheckStatusException, TimeoutException, ReceiptStatusException {
        var keyList = KeyList.of(admin.getPublicKey(), deployedEstimatePrecompileContract.contractId())
                .setThreshold(1);
        new AccountUpdateTransaction()
                .setAccountId(admin.getAccountId())
                .setKey(keyList)
                .freezeWith(accountClient.getClient())
                .sign(admin.getPrivateKey())
                .execute(accountClient.getClient());
        new TokenUpdateTransaction()
                .setTokenId(fungibleTokenId)
                .setSupplyKey(keyList)
                .setAdminKey(keyList)
                .freezeWith(accountClient.getClient())
                .sign(admin.getPrivateKey())
                .execute(accountClient.getClient());
        var tokenUpdate = new TokenUpdateTransaction()
                .setTokenId(nonFungibleTokenId)
                .setSupplyKey(keyList)
                .setAdminKey(keyList)
                .freezeWith(accountClient.getClient())
                .sign(admin.getPrivateKey())
                .execute(accountClient.getClient());
        networkTransactionResponse = new NetworkTransactionResponse(
                tokenUpdate.transactionId, tokenUpdate.getReceipt(accountClient.getClient()));
    }

    @Then("I call estimateGas with transferToken function and verify the estimated gas against HAPI")
    public void executeTransferForFungibleWithGasLimit() {
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE,
                TRANSFER_TOKEN,
                asAddress(fungibleTokenId),
                asAddress(admin),
                asAddress(secondReceiverAccount),
                5L);
        var estimateGasValue = validateAndReturnGas(data, TRANSFER_TOKEN, estimatePrecompileContractSolidityAddress);
        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasValue, TRANSFER_TOKEN, data);
    }

    @And("I associate the contract with the receiver account")
    public void associateSecondReceiverWithContract() {
        networkTransactionResponse = tokenClient.associate(secondReceiverAccount, nonFungibleTokenId);
    }

    @Then("I call estimateGas with transferNFT function and verify the estimated gas against HAPI")
    public void executeTransferTokenNonFungibleWithGasLimit() {
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE,
                TRANSFER_NFT,
                asAddress(nonFungibleTokenId),
                asAddress(admin),
                asAddress(secondReceiverAccount.getAccountId().toSolidityAddress()),
                2L);
        var estimateGasValue = validateAndReturnGas(data, TRANSFER_NFT, estimatePrecompileContractSolidityAddress);
        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasValue, TRANSFER_NFT, data);
    }

    @And("I approve the receiver to use the token")
    public void approveFungibleTokensToContract() throws InvalidProtocolBufferException {
        NftId id = new NftId(nonFungibleTokenId, 3L);
        var accountId = AccountId.fromBytes(
                deployedEstimatePrecompileContract.contractId().toBytes());
        accountClient.approveNft(id, accountId);
        networkTransactionResponse = accountClient.approveToken(fungibleTokenId, accountId, 10);
    }

    @Then("I call estimateGas with allowance function for fungible token and verify the estimated gas against HAPI")
    public void executeAllowanceFungibleWithLimitedGas() {
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE,
                ALLOWANCE,
                asAddress(fungibleKycUnfrozenTokenId),
                asAddress(admin),
                asAddress(receiverAccountAlias));
        var estimateGasValue = validateAndReturnGas(data, ALLOWANCE, estimatePrecompileContractSolidityAddress);
        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasValue, ALLOWANCE, data);
    }

    @Then("I call estimateGas with allowance function for NFT and verify the estimated gas against HAPI")
    public void executeAllowanceNonFungibleWithLimitedGas() {
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE,
                ALLOWANCE,
                asAddress(nonFungibleKycUnfrozenTokenId),
                asAddress(admin),
                asAddress(receiverAccountAlias));
        var estimateGasValue = validateAndReturnGas(data, ALLOWANCE, estimatePrecompileContractSolidityAddress);
        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasValue, ALLOWANCE, data);
    }

    @Then("I call estimateGas with approve function and verify the estimated gas against HAPI")
    public void executeApproveWithLimitedGas() {
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE,
                APPROVE,
                asAddress(fungibleTokenId),
                asAddress(receiverAccountAlias),
                new BigInteger("10"));
        var estimateGasValue = validateAndReturnGas(data, APPROVE, estimatePrecompileContractSolidityAddress);
        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasValue, APPROVE, data);
    }

    @Then("I call estimateGas with approveNFT function and verify the estimated gas against HAPI")
    public void executeApproveNftWithLimitedGas() {
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE,
                APPROVE_NFT,
                asAddress(nonFungibleKycUnfrozenTokenId),
                asAddress(receiverAccountAlias),
                new BigInteger("1"));
        var estimateGasValue = validateAndReturnGas(data, APPROVE_NFT, estimatePrecompileContractSolidityAddress);
        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasValue, APPROVE_NFT, data);
    }

    @Then("I call estimateGas with transferFrom function with fungible and verify the estimated gas against HAPI")
    public void executeTransferFromWithGasLimit() {
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE,
                TRANSFER_FROM,
                asAddress(fungibleTokenId),
                asAddress(admin),
                asAddress(secondReceiverAccount.getAccountId().toSolidityAddress()),
                new BigInteger("5"));
        var estimateGasValue = validateAndReturnGas(data, TRANSFER_FROM, estimatePrecompileContractSolidityAddress);
        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasValue, TRANSFER_FROM, data);
    }

    @Then("I call estimateGas with transferFromNFT function and verify the estimated gas against HAPI")
    public void executeTransferFromNFTWithGasLimit() {
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE,
                TRANSFER_FROM_NFT,
                asAddress(nonFungibleTokenId),
                asAddress(admin),
                asAddress(secondReceiverAccount.getAccountId().toSolidityAddress()),
                new BigInteger("3"));
        var estimateGasValue = validateAndReturnGas(data, TRANSFER_FROM_NFT, estimatePrecompileContractSolidityAddress);
        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasValue, TRANSFER_FROM_NFT, data);
    }

    @Then("I call estimate gas that mints FUNGIBLE token and gets the total supply and balance")
    public void estimateGasMintFungibleTokenGetTotalSupplyAndBalanceOfTreasury() {
        var data = encodeData(
                PRECOMPILE,
                MINT_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE,
                asAddress(fungibleTokenId),
                1L,
                asByteArray(Arrays.asList("0x00")),
                asAddress(admin));

        validateGasEstimation(
                data, MINT_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE, precompileTestContractSolidityAddress);
    }

    @Then("I call estimate gas that mints NFT token and gets the total supply and balance")
    public void estimateGasMintNftTokenGetTotalSupplyAndBalanceOfTreasury() {
        var data = encodeData(
                PRECOMPILE,
                MINT_NFT_GET_TOTAL_SUPPLY_AND_BALANCE,
                asAddress(nonFungibleTokenId),
                0L,
                asByteArray(Arrays.asList("0x02")),
                asAddress(admin));

        validateGasEstimation(data, MINT_NFT_GET_TOTAL_SUPPLY_AND_BALANCE, precompileTestContractSolidityAddress);
    }

    @Then("I call estimate gas that burns FUNGIBLE token and gets the total supply and balance")
    public void estimateGasBurnFungibleTokenGetTotalSupplyAndBalanceOfTreasury() {
        var data = encodeData(
                PRECOMPILE,
                BURN_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE,
                asAddress(fungibleTokenId),
                1L,
                asLongArray(List.of()),
                asAddress(admin));

        validateGasEstimation(
                data, BURN_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE, precompileTestContractSolidityAddress);
    }

    @Then("I call estimate gas that burns NFT token and returns the total supply and balance of treasury")
    public void estimateGasBurnNftTokenGetTotalSupplyAndBalanceOfTreasury() {
        var data = encodeData(
                PRECOMPILE,
                BURN_NFT_GET_TOTAL_SUPPLY_AND_BALANCE,
                asAddress(nonFungibleTokenId),
                0L,
                asLongArray(List.of(1L)),
                asAddress(admin));

        validateGasEstimation(data, BURN_NFT_GET_TOTAL_SUPPLY_AND_BALANCE, precompileTestContractSolidityAddress);
    }

    @Then("I call estimate gas that wipes FUNGIBLE token and gets the total supply and balance")
    public void estimateGasWipeFungibleTokenGetTotalSupplyAndBalanceOfTreasury() {
        var data = encodeData(
                PRECOMPILE,
                WIPE_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE,
                asAddress(fungibleTokenId),
                1L,
                asLongArray(List.of()),
                asAddress(receiverAccountAlias));

        validateGasEstimation(
                data, WIPE_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE, precompileTestContractSolidityAddress);
    }

    @Then("I call estimate gas that wipes NFT token and gets the total supply and balance")
    public void estimateGasWipeNftTokenGetTotalSupplyAndBalanceOfTreasury() {
        var data = encodeData(
                PRECOMPILE,
                WIPE_NFT_GET_TOTAL_SUPPLY_AND_BALANCE,
                asAddress(fungibleTokenId),
                0L,
                asLongArray(List.of(1L)),
                asAddress(receiverAccountAlias));

        validateGasEstimation(data, WIPE_NFT_GET_TOTAL_SUPPLY_AND_BALANCE, precompileTestContractSolidityAddress);
    }

    @Then("I call estimate gas that pauses FUNGIBLE token, unpauses and gets the token status")
    public void estimateGasPauseFungibleTokenGetStatusUnpauseGetStatus() {
        var data = encodeData(PRECOMPILE, PAUSE_UNPAUSE_GET_STATUS, asAddress(fungibleTokenId));

        validateGasEstimation(data, PAUSE_UNPAUSE_GET_STATUS, precompileTestContractSolidityAddress);
    }

    @Then("I call estimate gas that pauses NFT token, unpauses and gets the token status")
    public void estimateGasPauseNFTTokenGetStatusUnpauseGetStatus() {
        var data = encodeData(PRECOMPILE, PAUSE_UNPAUSE_GET_STATUS, asAddress(nonFungibleTokenId));

        validateGasEstimation(data, PAUSE_UNPAUSE_GET_STATUS, precompileTestContractSolidityAddress);
    }

    @Then("I call estimate gas that freezes FUNGIBLE token, unfreezes and gets freeze status")
    public void estimateGasFreezeFungibleTokenGetFreezeStatusUnfreezeGetFreezeStatus() {
        var data = encodeData(PRECOMPILE, FREEZE_UNFREEZE_GET_STATUS, asAddress(fungibleTokenId), asAddress(admin));

        validateGasEstimation(data, FREEZE_UNFREEZE_GET_STATUS, precompileTestContractSolidityAddress);
    }

    @Then("I call estimate gas that freezes NFT token, unfreezes and gets freeze status")
    public void estimateGasFreezeNftTokenGetFreezeStatusUnfreezeGetFreezeStatus() {
        var data = encodeData(PRECOMPILE, FREEZE_UNFREEZE_GET_STATUS, asAddress(fungibleTokenId), asAddress(admin));

        validateGasEstimation(data, FREEZE_UNFREEZE_GET_STATUS, precompileTestContractSolidityAddress);
    }

    @Then("I call estimate gas that approves FUNGIBLE token and gets allowance")
    public void estimateGasApproveFungibleTokenGetAllowance() {
        var data = encodeData(
                PRECOMPILE,
                APPROVE_FUNGIBLE_GET_ALLOWANCE,
                asAddress(fungibleTokenId),
                asAddress(receiverAccountAlias),
                new BigInteger("1"),
                new BigInteger("0"));

        validateGasEstimation(data, APPROVE_FUNGIBLE_GET_ALLOWANCE, precompileTestContractSolidityAddress);
    }

    @Then("I call estimate gas that approves NFT token and gets allowance")
    public void estimateGasApproveNFTTokenGetAllowance() {
        var data = encodeData(
                PRECOMPILE,
                APPROVE_NFT_GET_ALLOWANCE,
                asAddress(nonFungibleKycUnfrozenTokenId),
                asAddress(receiverAccountAlias),
                new BigInteger("0"),
                new BigInteger("1"));

        validateGasEstimation(data, APPROVE_NFT_GET_ALLOWANCE, precompileTestContractSolidityAddress);
    }

    @Then("I call estimate gas that associates FUNGIBLE token dissociates and fails token transfer")
    public void estimateGasAssociateFungibleTokenDissociateFailTransfer() {
        var data = encodeData(
                PRECOMPILE,
                DISSOCIATE_FUNGIBLE_TOKEN_AND_TRANSFER,
                asAddress(fungibleTokenId),
                asAddress(admin),
                asAddress(receiverAccountAlias),
                new BigInteger("1"),
                new BigInteger("0"));

        validateGasEstimation(data, DISSOCIATE_FUNGIBLE_TOKEN_AND_TRANSFER, precompileTestContractSolidityAddress);
    }

    @Then("I call estimate gas that associates NFT token dissociates and fails token transfer")
    public void estimateGasAssociateNftTokenDissociateFailTransfer() {
        var data = encodeData(
                PRECOMPILE,
                DISSOCIATE_NFT_AND_TRANSFER,
                asAddress(nonFungibleTokenId),
                asAddress(admin),
                asAddress(receiverAccountAlias),
                new BigInteger("0"),
                new BigInteger("1"));

        validateGasEstimation(data, DISSOCIATE_NFT_AND_TRANSFER, precompileTestContractSolidityAddress);
    }

    @Then("I call estimate gas that approves a FUNGIBLE token and transfers it")
    public void estimateGasApproveFungibleTokenTransferFromGetAllowanceGetBalance() {
        var data = encodeData(
                PRECOMPILE,
                APPROVE_FUNGIBLE_TOKEN_AND_TRANSFER,
                asAddress(fungibleTokenId),
                asAddress(receiverAccountAlias),
                new BigInteger("1"));

        validateGasEstimation(data, APPROVE_FUNGIBLE_TOKEN_AND_TRANSFER, precompileTestContractSolidityAddress);
    }

    @Then("I call estimate gas that approves a NFT token and transfers it")
    public void approveNftTokenTransferFromGetAllowanceGetBalance() {
        var data = encodeData(
                PRECOMPILE,
                APPROVE_NFT_TOKEN_AND_TRANSFER_FROM,
                asAddress(nonFungibleTokenId),
                asAddress(secondReceiverAccount),
                new BigInteger("1"));

        validateGasEstimation(data, APPROVE_NFT_TOKEN_AND_TRANSFER_FROM, precompileTestContractSolidityAddress);
    }

    @And("I approve and transfer NFT tokens to the precompile contract")
    public void approveAndTransferNftToPrecompileContract() throws InvalidProtocolBufferException {
        accountClient.approveNftAllSerials(nonFungibleTokenId, deployedPrecompileContract.contractId());
        networkTransactionResponse = tokenClient.transferNonFungibleToken(
                nonFungibleTokenId,
                receiverAccount,
                AccountId.fromString(precompileTestContractSolidityAddress),
                List.of(1L),
                null);
    }

    @Then("I call estimateGas with mintToken function for fungible token and verify the estimated gas against HAPI")
    public void executeMintFungibleTokenWithLimitedGas() {
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE, MINT_TOKEN, asAddress(fungibleTokenId), 1L, asByteArray(new ArrayList<>()));
        var estimateGasValue = validateAndReturnGas(data, MINT_TOKEN, estimatePrecompileContractSolidityAddress);
        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasValue, MINT_TOKEN, data);
    }

    @Then("I call estimateGas with mintToken function for NFT and verify the estimated gas against HAPI")
    public void executeMintNonFungibleWithLimitedGas() {
        var data = encodeDataToByteArray(
                ESTIMATE_PRECOMPILE, MINT_NFT, asAddress(nonFungibleTokenId), 0L, asByteArray(List.of("0x02")));
        var estimateGasValue = validateAndReturnGas(data, MINT_NFT, estimatePrecompileContractSolidityAddress);
        executeContractTransaction(deployedEstimatePrecompileContract, estimateGasValue, MINT_NFT, data);
    }

    private void validateGasEstimationForCreateToken(String data, int actualGasUsed, long value) {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(data)
                .to(estimatePrecompileContractSolidityAddress)
                .from(contractClient.getClientAddress())
                .estimate(true)
                .value(value)
                .build();
        ContractCallResponse msgSenderResponse = mirrorClient.contractsCall(contractCallRequestBody);
        int estimatedGas = msgSenderResponse.getResultAsNumber().intValue();
        assertWithinDeviation(actualGasUsed, estimatedGas, lowerDeviation, upperDeviation);
    }

    /**
     * Executes estimate gas for token create with current exchange rates and if this fails reties with next exchange
     * rates. The consumer accepts boolean value indicating if we should use current or next exchange rate. true =
     * current, false = next This is done in order to prevent edge cases like: System.currentTimeMillis() returns
     * timestamp that is within the current exchange rate limit, but after few ms the next exchange rate takes place.
     * After some ms when we call the create token with the outdated rates the test fails. We cannot ensure consistent
     * timing between the call getting the exchange rates and the create token call.
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

    @Getter
    @RequiredArgsConstructor
    enum ContractMethods implements ContractMethodInterface {
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
        UPDATE_TOKEN_KEYS("updateTokenKeysExternal", 60427),
        MINT_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE("mintTokenGetTotalSupplyAndBalanceOfTreasury", 68127),
        MINT_NFT_GET_TOTAL_SUPPLY_AND_BALANCE("mintTokenGetTotalSupplyAndBalanceOfTreasury", 335855),
        BURN_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE("burnTokenGetTotalSupplyAndBalanceOfTreasury", 66908),
        BURN_NFT_GET_TOTAL_SUPPLY_AND_BALANCE("burnTokenGetTotalSupplyAndBalanceOfTreasury", 66886),
        WIPE_FUNGIBLE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE("wipeTokenGetTotalSupplyAndBalanceOfAccount", 88477),
        WIPE_NFT_GET_TOTAL_SUPPLY_AND_BALANCE("wipeTokenGetTotalSupplyAndBalanceOfAccount", 88970),
        PAUSE_UNPAUSE_GET_STATUS("pauseTokenGetPauseStatusUnpauseGetPauseStatus", 98345),
        FREEZE_UNFREEZE_GET_STATUS("freezeTokenGetFreezeStatusUnfreezeGetFreezeStatus", 57387),
        APPROVE_FUNGIBLE_GET_ALLOWANCE("approveTokenGetAllowance", 733080),
        APPROVE_NFT_GET_ALLOWANCE("approveTokenGetAllowance", 733127),
        DISSOCIATE_FUNGIBLE_TOKEN_AND_TRANSFER("associateTokenDissociateFailTransfer", 1481983),
        DISSOCIATE_NFT_AND_TRANSFER("associateTokenDissociateFailTransfer", 1481983),
        APPROVE_FUNGIBLE_TOKEN_AND_TRANSFER("approveFungibleTokenTransferFromGetAllowanceGetBalance", 752505),
        APPROVE_NFT_TOKEN_AND_TRANSFER_FROM("approveNftAndTransfer", 765896);

        private final String selector;
        private final int actualGas;
    }
}
