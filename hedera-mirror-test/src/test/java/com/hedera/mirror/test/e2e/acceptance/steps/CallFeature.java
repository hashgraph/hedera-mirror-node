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

import static com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.FUNGIBLE_FOR_ETH_CALL;
import static com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.FUNGIBLE_KYC_UNFROZEN_FOR_ETH_CALL;
import static com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.NFT_FOR_ETH_CALL;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ERC;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ESTIMATE_GAS;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.PRECOMPILE;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.ADDRESS_BALANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.APPROVE_FUNGIBLE_TOKEN_AND_TRANSFER;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.APPROVE_NFT_TOKEN_AND_TRANSFER;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.APPROVE_TOKEN_GET_ALLOWANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.BURN_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.DEPLOY_NESTED_CONTRACT_CONTRACT_VIA_CREATE2_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.DEPLOY_NESTED_CONTRACT_CONTRACT_VIA_CREATE_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.DISSOCIATE_TOKEN_FAIL_TRANSFER;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.FREEZE_UNFREEZE_GET_STATUS;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.GRANT_KYC_REVOKE_KYC;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.HTS_GET_DEFAULT_FREEZE_STATUS_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.HTS_GET_TOKEN_DEFAULT_KYC_STATUS_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.HTS_IS_FROZEN_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.HTS_IS_KYC_GRANTED_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.HTS_IS_TOKEN_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.IERC721_TOKEN_BALANCE_OF_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.IERC721_TOKEN_NAME_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.IERC721_TOKEN_SYMBOL_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.IERC721_TOKEN_TOTAL_SUPPLY_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.MINT_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.PAUSE_UNPAUSE_GET_STATUS;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.REENTRANCY_CALL_WITH_GAS;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.STATE_UPDATE_N_TIMES_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.UPDATE_COUNTER_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.WIPE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asAddress;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asByteArray;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asLongArray;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.nextBytes;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.to32BytesString;
import static org.aspectj.runtime.internal.Conversions.intValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@CustomLog
@RequiredArgsConstructor
public class CallFeature extends AbstractFeature {

    private static final String HEX_REGEX = "^[0-9a-fA-F]+$";
    private static DeployedContract deployedPrecompileContract;
    private DeployedContract deployedErcTestContract;
    private DeployedContract deployedEstimatePrecompileContract;
    private final AccountClient accountClient;
    private final MirrorNodeClient mirrorClient;
    private final TokenClient tokenClient;
    private String ercContractAddress;
    private String precompileContractAddress;
    private String estimateContractAddress;
    private ExpandedAccountId receiverAccountId;
    private ExpandedAccountId secondReceiverAccount;
    private ExpandedAccountId thirdReceiver;
    private String secondReceiverAlias;
    private TokenId fungibleTokenId;
    private TokenId nonFungibleTokenId;
    private TokenId fungibleKycUnfrozenTokenId;
    private ExpandedAccountId admin;

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
    @Then("the mirror node should return status {int} for the HAPI transaction")
    public void ethCallVerifyMirrorAPIResponses(int status) {
        verifyMirrorTransactionsResponse(mirrorClient, status);
    }

    @Given("I successfully create ERC contract")
    public void createNewERCtestContract() throws IOException {
        deployedErcTestContract = getContract(ERC);
        ercContractAddress = deployedErcTestContract.contractId().toSolidityAddress();
    }

    @Given("I successfully create Precompile contract")
    public void createNewPrecompileTestContract() throws IOException {
        deployedPrecompileContract = getContract(PRECOMPILE);
        precompileContractAddress = deployedPrecompileContract.contractId().toSolidityAddress();
    }

    @Given("I successfully create EstimateGas contract")
    public void createNewEstimateTestContract() throws IOException {
        deployedEstimatePrecompileContract = getContract(ESTIMATE_GAS);
        estimateContractAddress =
                deployedEstimatePrecompileContract.contractId().toSolidityAddress();
        admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        receiverAccountId = accountClient.getAccount(AccountNameEnum.ALICE);
        secondReceiverAccount = accountClient.getAccount(AccountNameEnum.BOB);
        thirdReceiver = accountClient.getAccount(AccountNameEnum.DAVE);
        secondReceiverAlias =
                secondReceiverAccount.getPublicKey().toEvmAddress().toString();
        fungibleTokenId = tokenClient.getToken(FUNGIBLE_FOR_ETH_CALL).tokenId();
        fungibleKycUnfrozenTokenId =
                tokenClient.getToken(FUNGIBLE_KYC_UNFROZEN_FOR_ETH_CALL).tokenId();
        nonFungibleTokenId = tokenClient.getToken(NFT_FOR_ETH_CALL).tokenId();
    }

    @Given("I mint a NFT")
    public void mintNft() {
        networkTransactionResponse = tokenClient.mint(nonFungibleTokenId, nextBytes(4));
    }

    @And("I approve and transfer FUNGIBLE token to receiver account")
    public void approveAndTransferFungibleTokenToReceiver() {
        accountClient.approveToken(fungibleTokenId, receiverAccountId.getAccountId(), 1L);
        networkTransactionResponse = tokenClient.transferFungibleToken(
                fungibleTokenId,
                tokenClient.getSdkClient().getExpandedOperatorAccountId(),
                receiverAccountId.getAccountId(),
                receiverAccountId.getPrivateKey(),
                1L);
    }

    @And("I transfer FUNGIBLE token to the precompile contract")
    public void approveAndTransferFungibleTokenToPrecompile() {
        networkTransactionResponse = tokenClient.transferFungibleToken(
                fungibleTokenId,
                admin,
                AccountId.fromString(deployedPrecompileContract.contractId().toString()),
                receiverAccountId.getPrivateKey(),
                10L);
    }

    @And("I approve and transfer NFT token to receiver account")
    public void approveAndTransferNftTokenToReceiver() {
        var ntfId = new NftId(nonFungibleTokenId, 1L);
        accountClient.approveNft(ntfId, receiverAccountId.getAccountId());
        networkTransactionResponse = tokenClient.transferNonFungibleToken(
                nonFungibleTokenId,
                tokenClient.getSdkClient().getExpandedOperatorAccountId(),
                receiverAccountId.getAccountId(),
                List.of(1L),
                receiverAccountId.getPrivateKey());
    }

    @And("I associate FUNGIBLE token to receiver account")
    public void associateFungibleTokenToReceiver() {
        tokenClient.associate(receiverAccountId, fungibleTokenId);
    }

    @And("I associate NFT token to receiver account")
    public void associateNftTokenToReceiver() {
        networkTransactionResponse = tokenClient.associate(receiverAccountId, nonFungibleTokenId);
    }

    @And("I associate precompile contract with the tokens")
    public void associatePrecompileWithTokens() throws InvalidProtocolBufferException {
        // In order to execute Approve, approveNFT, ercApprove we need to associate the contract with the token
        tokenClient.associate(deployedPrecompileContract.contractId(), fungibleTokenId);
        networkTransactionResponse = tokenClient.associate(deployedPrecompileContract.contractId(), nonFungibleTokenId);
    }

    @And("I associate FUNGIBLE_KYC_UNFROZEN token to receiver account")
    public void associateReceiverWithFungibleKyc() throws InvalidProtocolBufferException {
        networkTransactionResponse = tokenClient.associate(secondReceiverAccount, fungibleKycUnfrozenTokenId);
    }

    @And("I approve and transfer NFT token to the precompile contract")
    public void approveAndTransferNftToPrecompileContract() throws InvalidProtocolBufferException {
        accountClient.approveNftAllSerials(nonFungibleTokenId, deployedPrecompileContract.contractId());
        networkTransactionResponse = tokenClient.transferNonFungibleToken(
                nonFungibleTokenId,
                receiverAccountId,
                AccountId.fromString(precompileContractAddress),
                List.of(1L),
                null);
    }

    // ETHCALL-017
    @RetryAsserts
    @Then("I call function with IERC721Metadata token {string} name")
    public void ierc721MetadataTokenName(String tokenName) {
        var tokenNameEnum = TokenClient.TokenNameEnum.valueOf(tokenName);
        var tokenId = tokenClient.getToken(tokenNameEnum).tokenId();

        var data = encodeData(ERC, IERC721_TOKEN_NAME_SELECTOR, asAddress(tokenId));
        var response = callContract(data, ercContractAddress);

        assertThat(response.getResultAsText()).isEqualTo(tokenNameEnum.getSymbol() + "_name");
    }

    // ETHCALL-018
    @RetryAsserts
    @Then("I call function with IERC721Metadata token {string} symbol")
    public void ierc721MetadataTokenSymbol(String tokenName) {
        var tokenNameEnum = TokenClient.TokenNameEnum.valueOf(tokenName);
        var tokenId = tokenClient.getToken(tokenNameEnum).tokenId();

        var data = encodeData(ERC, IERC721_TOKEN_SYMBOL_SELECTOR, asAddress(tokenId));
        var response = callContract(data, ercContractAddress);

        assertThat(response.getResultAsText()).isEqualTo(tokenNameEnum.getSymbol());
    }

    // ETHCALL-019
    @RetryAsserts
    @Then("I call function with IERC721Metadata token NFT totalSupply")
    public void ierc721MetadataTokenTotalSupply() {
        var totalSupplyOfNft =
                mirrorClient.getTokenInfo(nonFungibleTokenId.toString()).getTotalSupply();

        var data = encodeData(ERC, IERC721_TOKEN_TOTAL_SUPPLY_SELECTOR, asAddress(nonFungibleTokenId));
        var response = callContract(data, ercContractAddress);

        assertThat(response.getResultAsNumber()).isEqualTo(totalSupplyOfNft);
    }

    // ETHCALL-020
    @RetryAsserts
    @Then("I call function with IERC721 token NFT balanceOf owner")
    public void ierc721MetadatagetBalanceOfTokenTokenBalanceOf() {
        var balanceOfNft = getBalanceOfToken(nonFungibleTokenId, admin.getAccountId());
        var data = encodeData(
                ERC, IERC721_TOKEN_BALANCE_OF_SELECTOR, asAddress(nonFungibleTokenId), asAddress(contractClient));
        var response = callContract(data, ercContractAddress);

        assertThat(response.getResultAsNumber()).isEqualTo(balanceOfNft);
    }

    @RetryAsserts
    @Given("I verify the precompile contract bytecode is deployed")
    public void contractDeployed() {
        var response = mirrorClient.getContractInfo(precompileContractAddress);
        assertThat(response.getBytecode()).isNotBlank();
        assertThat(response.getRuntimeBytecode()).isNotBlank();
    }

    // ETHCALL-025
    @RetryAsserts
    @Then("I call function with HederaTokenService isToken token FUNGIBLE")
    public void htsIsToken() {
        var data = encodeData(PRECOMPILE, HTS_IS_TOKEN_SELECTOR, asAddress(fungibleTokenId));
        var response = callContract(data, precompileContractAddress);

        assertThat(response.getResultAsBoolean()).isTrue();
    }

    // ETHCALL-026
    @RetryAsserts
    @Then("I call function with HederaTokenService isFrozen token FUNGIBLE, account")
    public void htsIsFrozen() {
        var data =
                encodeData(PRECOMPILE, HTS_IS_FROZEN_SELECTOR, asAddress(fungibleTokenId), asAddress(contractClient));
        var response = callContract(data, precompileContractAddress);

        assertThat(response.getResultAsBoolean()).isFalse();
    }

    // ETHCALL-027
    @RetryAsserts
    @Then("I call function with HederaTokenService isKyc token FUNGIBLE, account")
    public void htsIsKyc() {
        var data = encodeData(
                PRECOMPILE, HTS_IS_KYC_GRANTED_SELECTOR, asAddress(fungibleTokenId), asAddress(contractClient));
        var response = callContract(data, precompileContractAddress);

        assertThat(response.getResultAsBoolean()).isTrue();
    }

    // ETHCALL-028
    @RetryAsserts
    @Then("I call function with HederaTokenService getTokenDefaultFreezeStatus token FUNGIBLE")
    public void htsGetTokenDefaultFreezeStatus() {
        var data = encodeData(PRECOMPILE, HTS_GET_DEFAULT_FREEZE_STATUS_SELECTOR, asAddress(fungibleTokenId));
        var response = callContract(data, precompileContractAddress);

        assertThat(response.getResultAsBoolean()).isFalse();
    }

    // ETHCALL-029
    @RetryAsserts
    @Then("I call function with HederaTokenService getTokenDefaultKycStatus token FUNGIBLE")
    public void htsGetTokenDefaultKycStatus() {
        var data = encodeData(PRECOMPILE, HTS_GET_TOKEN_DEFAULT_KYC_STATUS_SELECTOR, asAddress(fungibleTokenId));
        var response = callContract(data, precompileContractAddress);

        assertThat(response.getResultAsBoolean()).isFalse();
    }

    @Then("I call function with update and I expect return of the updated value")
    public void ethCallUpdateFunction() {
        var updateValue = new BigInteger("5");
        var data = encodeData(ESTIMATE_GAS, UPDATE_COUNTER_SELECTOR, updateValue);
        var response = callContract(data, estimateContractAddress);

        assertEquals(response.getResultAsNumber(), updateValue);
    }

    @Then("I call function that makes N times state update")
    public void ethCallStateUpdateNTimesFunction() {
        var data = encodeData(ESTIMATE_GAS, STATE_UPDATE_N_TIMES_SELECTOR, new BigInteger("15"));
        var response = callContract(data, estimateContractAddress);

        assertEquals(String.valueOf(response.getResultAsNumber()), "14");
    }

    @Then("I call function with nested deploy using create function")
    public void ethCallNestedDeployViaCreateFunction() {
        var data = encodeData(ESTIMATE_GAS, DEPLOY_NESTED_CONTRACT_CONTRACT_VIA_CREATE_SELECTOR);
        var response = callContract(data, estimateContractAddress);
        String[] addresses = splitAddresses(response.getResult());

        validateAddresses(addresses);
    }

    @Then("I call function with nested deploy using create2 function")
    public void ethCallNestedDeployViaCreate2Function() {
        var data = encodeData(ESTIMATE_GAS, DEPLOY_NESTED_CONTRACT_CONTRACT_VIA_CREATE2_SELECTOR);
        var response = callContract(data, estimateContractAddress);

        String[] addresses = splitAddresses(response.getResult());

        validateAddresses(addresses);
    }

    @SuppressWarnings("java:S2925")
    @RetryAsserts
    @Then("I successfully update the balance of an account and get the updated balance after 2 seconds")
    public void getBalance() throws InterruptedException {
        final var receiverAddress = asAddress(receiverAccountId.getAccountId().toSolidityAddress());
        var data = encodeData(ESTIMATE_GAS, ADDRESS_BALANCE, receiverAddress);
        var initialBalance = callContract(data, estimateContractAddress).getResultAsNumber();
        networkTransactionResponse = accountClient.sendCryptoTransfer(
                receiverAccountId.getAccountId(),
                Hbar.fromTinybars(initialBalance.longValue()),
                receiverAccountId.getPrivateKey());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        // wait for token cache to expire
        Thread.sleep(2000);
        var updatedBalance = callContract(data, estimateContractAddress).getResultAsNumber();
        assertThat(initialBalance).isEqualTo(updatedBalance.divide(BigInteger.TWO));
    }

    @RetryAsserts
    @Then("I call function with transfer that returns the balance")
    public void ethCallReentrancyCallFunction() {
        var data = encodeData(
                ESTIMATE_GAS, REENTRANCY_CALL_WITH_GAS, asAddress(receiverAccountId), new BigInteger("10000"));
        var response = callContract(data, estimateContractAddress);
        String[] balances = splitAddresses(response.getResult());
        // verify initial balance
        assertEquals(Integer.parseInt(balances[0], 16), 1000000);
        // verify balance after transfer of 10,000
        assertEquals(Integer.parseInt(balances[1], 16), 990000);
    }

    @Then("I mint FUNGIBLE token and get the total supply and balance")
    public void ethCallMintFungibleTokenGetTotalSupplyAndBalanceOfTreasury() {
        var data = encodeData(
                PRECOMPILE,
                MINT_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE,
                asAddress(fungibleTokenId),
                1L,
                asByteArray(Arrays.asList("0x00")),
                asAddress(admin));

        var response = callContract(data, precompileContractAddress);
        var results = response.getResultAsListDecimal();
        assertThat(results).isNotNull().hasSize(4);

        assertThat(intValue(results.get(0)) + 1)
                .as("BalanceBefore + amount = balanceAfter")
                .isEqualTo(intValue(results.get(1)));
        assertThat(intValue(results.get(2)) + 1L)
                .as("totalSupplyBefore + amount = totalSupplyAfter")
                .isEqualTo(intValue(results.get(3)));
    }

    @Then("I mint NFT token and get the total supply and balance")
    public void ethCallMintNftTokenGetTotalSupplyAndBalanceOfTreasury() {
        var data = encodeData(
                PRECOMPILE,
                MINT_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE,
                asAddress(nonFungibleTokenId),
                0L,
                asByteArray(Arrays.asList("0x02")),
                asAddress(tokenClient
                        .getSdkClient()
                        .getExpandedOperatorAccountId()
                        .getAccountId()
                        .toSolidityAddress()));
        var response = callContract(data, precompileContractAddress);
        var results = response.getResultAsListDecimal();
        assertThat(results).isNotNull().hasSize(4);

        assertThat(intValue(results.get(0)) + 1)
                .as("BalanceBefore + amount = balanceAfter")
                .isEqualTo(intValue(results.get(1)));
        assertThat(intValue(results.get(2)) + 1)
                .as("totalSupplyBefore + amount = totaSupplyAfter")
                .isEqualTo(intValue(results.get(3)));
    }

    @Then("I burn FUNGIBLE token and get the total supply and balance")
    public void ethCallBurnFungibleTokenGetTotalSupplyAndBalanceOfTreasury() {
        var data = encodeData(
                PRECOMPILE,
                BURN_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE,
                asAddress(fungibleTokenId.toSolidityAddress()),
                1L,
                asLongArray(List.of()),
                asAddress(admin));

        var response = callContract(data, precompileContractAddress);
        var results = response.getResultAsListDecimal();
        assertThat(results).isNotNull().hasSize(4);

        assertThat(intValue(intValue(results.get(0)) - 1L))
                .as("BalanceBefore - amount = balanceAfter")
                .isEqualTo(intValue(results.get(1)));
        assertThat(intValue(intValue(results.get(2)) - 1L))
                .as("totalSupplyBefore - amount = totalSupplyAfter")
                .isEqualTo(intValue(results.get(3)));
    }

    @Then("I burn NFT and get the total supply and balance")
    public void ethCallBurnNftTokenGetTotalSupplyAndBalanceOfTreasury() {
        var data = encodeData(
                PRECOMPILE,
                BURN_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE,
                asAddress(nonFungibleTokenId.toSolidityAddress()),
                0L,
                asLongArray(List.of(1L)),
                asAddress(admin));

        var response = callContract(data, precompileContractAddress);
        var results = response.getResultAsListDecimal();
        assertThat(results).isNotNull().hasSize(4);

        assertThat(intValue(results.get(0)) - 1)
                .as("BalanceBefore - amount = balanceAfter")
                .isEqualTo(intValue(results.get(1)));
        assertThat(intValue(results.get(2)) - 1)
                .as("totalSupplyBefore - amount = totalSupplyAfter")
                .isEqualTo(intValue(results.get(3)));
    }

    @Then("I wipe FUNGIBLE token and get the total supply and balance")
    public void ethCallWipeFungibleTokenGetTotalSupplyAndBalanceOfTreasury() {
        var data = encodeData(
                PRECOMPILE,
                WIPE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE,
                asAddress(fungibleTokenId.toSolidityAddress()),
                1L,
                asLongArray(List.of()),
                asAddress(receiverAccountId));

        var response = callContract(data, precompileContractAddress);
        var results = response.getResultAsListDecimal();
        assertThat(results).isNotNull().hasSize(4);

        assertThat(intValue(intValue(results.get(0)) - 1L))
                .as("BalanceBefore - amount = balanceAfter")
                .isEqualTo(intValue(results.get(1)));
        assertThat(intValue(intValue(results.get(2)) - 1L))
                .as("totalSupplyBefore - amount = totaSupplyAfter")
                .isEqualTo(intValue(results.get(3)));
    }

    @Then("I wipe NFT and get the total supply and balance")
    public void ethCallWipeNftTokenGetTotalSupplyAndBalanceOfTreasury() {
        var data = encodeData(
                PRECOMPILE,
                WIPE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE,
                asAddress(nonFungibleTokenId.toSolidityAddress()),
                0L,
                asLongArray(List.of(1L)),
                asAddress(receiverAccountId));

        var response = callContract(data, precompileContractAddress);
        var results = response.getResultAsListDecimal();
        assertThat(results).isNotNull().hasSize(4);

        assertThat(intValue(results.get(0)) - 1)
                .as("BalanceBefore - amount = balanceAfter")
                .isEqualTo(intValue(results.get(1)));
        assertThat(intValue(results.get(2)) - 1)
                .as("totalSupplyBefore - amount = totalSupplyAfter")
                .isEqualTo(intValue(results.get(3)));
    }

    @Then("I pause {string} token, unpause and get the status of the token")
    public void ethCallPauseTokenGetStatusUnpauseGetStatus(String tokenName) {
        var tokenId = tokenClient
                .getToken(TokenClient.TokenNameEnum.valueOf(tokenName))
                .tokenId();
        var data = encodeData(PRECOMPILE, PAUSE_UNPAUSE_GET_STATUS, asAddress(tokenId));
        var response = callContract(data, precompileContractAddress);
        var statusAfterPause = response.getResult().substring(2, 66);
        var statusAfterUnpause = response.getResult().substring(66);

        assertThat(Integer.valueOf(statusAfterPause, 16))
                .as("isPaused after pause is true")
                .isEqualTo(1);
        assertThat(Integer.valueOf(statusAfterUnpause, 16))
                .as("isPaused after unpause is false")
                .isZero();
    }

    @Then("I freeze {string} token, unfreeze and get status")
    public void ethCallFreezeFungibleGetFreezeStatusUnfreezeGetFreezeStatus(String tokenName) {
        var tokenId = tokenClient
                .getToken(TokenClient.TokenNameEnum.valueOf(tokenName))
                .tokenId();
        var data = encodeData(PRECOMPILE, FREEZE_UNFREEZE_GET_STATUS, asAddress(tokenId), asAddress(admin));
        var response = callContract(data, precompileContractAddress);
        var statusAfterFreeze = response.getResult().substring(2, 66);
        var statusAfterUnfreeze = response.getResult().substring(66);

        assertThat(Integer.valueOf(statusAfterFreeze, 16))
                .as("isFreezed after freeze is true")
                .isEqualTo(1);
        assertThat(Integer.valueOf(statusAfterUnfreeze, 16))
                .as("isFreezed after unfreeze is false")
                .isZero();
    }

    @Then("I approve a FUNGIBLE token and get allowance")
    public void ethCallApproveFungibleTokenGetAllowance() {
        var data = encodeData(
                PRECOMPILE,
                APPROVE_TOKEN_GET_ALLOWANCE,
                asAddress(fungibleTokenId),
                asAddress(secondReceiverAlias),
                new BigInteger("1"),
                new BigInteger("0"));

        var response = callContract(data, precompileContractAddress);
        var allowance = response.getResult().substring(2, 66);

        assertThat(new BigInteger(allowance))
                .as("allowance should equal amount")
                .isEqualTo(BigInteger.valueOf(1L));
    }

    @Then("I approve a NFT token and get allowance")
    public void ethCallApproveNFTTokenGetAllowance() {
        var data = encodeData(
                PRECOMPILE,
                APPROVE_TOKEN_GET_ALLOWANCE,
                asAddress(nonFungibleTokenId),
                asAddress(receiverAccountId),
                new BigInteger("0"),
                new BigInteger("1"));
        var response = callContract(data, precompileContractAddress);
        var approvedAddress = response.getResult().substring(66);

        assertThat(approvedAddress)
                .as("approved address should equal the spender")
                .isEqualTo(to32BytesString(receiverAccountId.getAccountId().toSolidityAddress()));
    }

    @Then("I dissociate a FUNGIBLE token and fail transfer")
    public void ethCallAssociateFungibleTokenDissociateFailTransfer() {
        var data = encodeData(
                PRECOMPILE,
                DISSOCIATE_TOKEN_FAIL_TRANSFER,
                asAddress(fungibleTokenId),
                asAddress(admin),
                asAddress(thirdReceiver),
                new BigInteger("1"),
                new BigInteger("0"));
        var response = callContract(data, precompileContractAddress);
        var statusAfterAssociate = response.getResultAsListDecimal().get(0);
        var statusAfterDissociate = response.getResultAsListDecimal().get(1);

        assertThat(statusAfterAssociate)
                .as("transfer after associate should pass -> response code 22 equals SUCCESS")
                .isEqualTo(22);
        assertThat(statusAfterDissociate)
                .as("transfer after dissociate should fail > response code 184 equals to owner does not own the token")
                .isEqualTo(184);
    }

    @Then("I dissociate a NFT and fail transfer")
    public void ethCallAssociateNftTokenDissociateFailTransfer() {
        var data = encodeData(
                PRECOMPILE,
                DISSOCIATE_TOKEN_FAIL_TRANSFER,
                asAddress(nonFungibleTokenId),
                asAddress(receiverAccountId),
                asAddress(secondReceiverAlias),
                new BigInteger("0"),
                new BigInteger("1"));
        var response = callContract(data, precompileContractAddress);
        var statusAfterAssociate = response.getResultAsListDecimal().get(0);
        var statusAfterDissociate = response.getResultAsListDecimal().get(1);

        assertThat(statusAfterAssociate)
                .as("transfer after associate should pass -> response code 22 equals SUCCESS")
                .isEqualTo(22);
        assertThat(statusAfterDissociate)
                .as("transfer after dissociate should fail -> response code 237 equals to owner does not own the NFT")
                .isEqualTo(184);
    }

    @Then("I approve a FUNGIBLE token and transfer it")
    public void ethCallApproveFungibleTokenAndTransfer() {
        networkTransactionResponse = tokenClient.associate(thirdReceiver, fungibleTokenId);
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        var data = encodeData(
                PRECOMPILE,
                APPROVE_FUNGIBLE_TOKEN_AND_TRANSFER,
                asAddress(fungibleTokenId),
                asAddress(thirdReceiver),
                new BigInteger("1"));
        var response = callContract(data, precompileContractAddress);
        var results = response.getResultAsListDecimal();
        assertThat(results).isNotNull().hasSize(4);

        assertThat(intValue(results.get(0)))
                .as("allowance before transfer should equal the amount")
                .isZero();
        assertThat(intValue(results.get(1)) + 1)
                .as("balance before + amount should equal the balance after")
                .isEqualTo(intValue(results.get(3)));
        assertThat(intValue(results.get(2)))
                .as("allowance after transfer should be 0")
                .isZero();
    }

    @Then("I approve a NFT token and transfer it")
    public void ethCallApproveNftTokenAndTransfer() {
        var data = encodeData(
                PRECOMPILE,
                APPROVE_NFT_TOKEN_AND_TRANSFER,
                asAddress(nonFungibleTokenId),
                asAddress(receiverAccountId),
                new BigInteger("1"));
        var response = callContract(data, precompileContractAddress);
        var results = response.getResultAsListAddress();
        assertThat(results).isNotNull().hasSize(4);

        assertThat(results.get(0))
                .as("allowed address before transfer should be the receiverAccount")
                .isEqualTo(results.get(3));
        assertThat(results.get(1))
                .as("owner after transfer should be the Precompile")
                .isEqualTo(to32BytesString(results.get(1)));
        assertThat(results.get(2))
                .as("allowance after transfer should be 0")
                .isEqualTo("0000000000000000000000000000000000000000000000000000000000000000");
    }

    @Then("I grant and revoke KYC")
    public void ethCallGrantKycRevokeKyc() {
        networkTransactionResponse = tokenClient.associate(secondReceiverAccount, fungibleKycUnfrozenTokenId);
        var data = encodeData(
                PRECOMPILE,
                GRANT_KYC_REVOKE_KYC,
                asAddress(fungibleKycUnfrozenTokenId),
                asAddress(admin),
                asAddress(secondReceiverAlias),
                new BigInteger("1"));
        var response = callContract(data, precompileContractAddress);
        var results = response.getResultAsListDecimal();
        assertThat(results).isNotNull().hasSize(5);

        assertThat(results.get(0)).as("isKYC after grant should be true").isEqualTo(1);
        assertThat(results.get(1)).as("KYC grant status should be SUCCESS = 22").isEqualTo(22);
        assertThat(results.get(2)).as("isKYC after revoke should be false").isZero();
        assertThat(results.get(3))
                .as("KYC revoke status should be SUCCESS = 22")
                .isEqualTo(22);
        assertThat(results.get(4))
                .as("transfer status after kyc revert should be failing with KYC should be granted")
                .isEqualTo(176);
    }

    private void validateAddresses(String[] addresses) {
        assertNotEquals(addresses[0], addresses[1]);
        assertTrue(addresses[0].matches(HEX_REGEX));
        assertTrue(addresses[1].matches(HEX_REGEX));
    }

    private long getBalanceOfToken(TokenId tokenId, AccountId accountId) {
        var tokenRelationships =
                mirrorClient.getTokenRelationships(accountId, tokenId).getTokens();
        assertThat(tokenRelationships).isNotNull().hasSize(1);
        return tokenRelationships.get(0).getBalance();
    }

    private long getTotalSupplyOfToken(TokenId tokenId) {
        return mirrorClient.getTokenInfo(tokenId.toString()).getTotalSupply().longValue();
    }

    @Getter
    @RequiredArgsConstructor
    enum ContractMethods implements SelectorInterface {
        IERC721_TOKEN_NAME_SELECTOR("nameIERC721"),
        IERC721_TOKEN_SYMBOL_SELECTOR("symbolIERC721"),
        IERC721_TOKEN_TOTAL_SUPPLY_SELECTOR("totalSupplyIERC721"),
        IERC721_TOKEN_BALANCE_OF_SELECTOR("balanceOfIERC721"),
        HTS_IS_TOKEN_SELECTOR("isTokenAddress"),
        HTS_IS_FROZEN_SELECTOR("isTokenFrozen"),
        HTS_IS_KYC_GRANTED_SELECTOR("isKycGranted"),
        HTS_GET_DEFAULT_FREEZE_STATUS_SELECTOR("getTokenDefaultFreeze"),
        HTS_GET_TOKEN_DEFAULT_KYC_STATUS_SELECTOR("getTokenDefaultKyc"),
        UPDATE_COUNTER_SELECTOR("updateCounter"),
        STATE_UPDATE_N_TIMES_SELECTOR("updateStateNTimes"),
        DEPLOY_NESTED_CONTRACT_CONTRACT_VIA_CREATE_SELECTOR("deployNestedContracts"),
        DEPLOY_NESTED_CONTRACT_CONTRACT_VIA_CREATE2_SELECTOR("deployNestedContracts2"),
        REENTRANCY_CALL_WITH_GAS("reentrancyCallWithGas"),
        MINT_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE("mintTokenGetTotalSupplyAndBalanceOfTreasury"),
        BURN_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE("burnTokenGetTotalSupplyAndBalanceOfTreasury"),
        WIPE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE("wipeTokenGetTotalSupplyAndBalanceOfAccount"),
        PAUSE_UNPAUSE_GET_STATUS("pauseTokenGetPauseStatusUnpauseGetPauseStatus"),
        FREEZE_UNFREEZE_GET_STATUS("freezeTokenGetFreezeStatusUnfreezeGetFreezeStatus"),
        APPROVE_TOKEN_GET_ALLOWANCE("approveTokenGetAllowance"),
        DISSOCIATE_TOKEN_FAIL_TRANSFER("associateTokenDissociateFailTransfer"),
        APPROVE_FUNGIBLE_TOKEN_AND_TRANSFER("approveFungibleTokenTransferFromGetAllowanceGetBalance"),
        APPROVE_NFT_TOKEN_AND_TRANSFER("approveNftAndTransfer"),
        GRANT_KYC_REVOKE_KYC("grantKycRevokeKyc"),
        ADDRESS_BALANCE("addressBalance");

        private final String selector;
    }
}
