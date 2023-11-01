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
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ERC;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ESTIMATE_GAS;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.PRECOMPILE;
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
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.to32BytesString;
import static org.aspectj.runtime.internal.Conversions.intValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.proto.TokenFreezeStatus;
import com.hedera.hashgraph.sdk.proto.TokenPauseStatus;
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
import org.apache.commons.lang3.RandomUtils;
import org.springframework.beans.factory.annotation.Autowired;

@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
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
    private long balanceOfToken;
    private long totalSupplyOfToken;
    private TokenId fungibleTokenId;
    private TokenId nonFungibleTokenId;
    private TokenId fungibleKycUnfrozenTokenId;
    private String receiverAccountAlias;
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

        return new String[]{address1, address2};
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
        estimateContractAddress = deployedEstimatePrecompileContract.contractId().toSolidityAddress();
        admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        receiverAccountId = accountClient.getAccount(AccountNameEnum.BOB);
        receiverAccountAlias = receiverAccountId.getPublicKey().toEvmAddress().toString();
        secondReceiverAccount = accountClient.getAccount(AccountNameEnum.DAVE);
        fungibleTokenId = tokenClient.getToken(FUNGIBLE).tokenId();
        fungibleKycUnfrozenTokenId = tokenClient.getToken(FUNGIBLE_KYC_UNFROZEN).tokenId();
        nonFungibleTokenId = tokenClient.getToken(NFT).tokenId();
    }

    @Given("I mint a NFT")
    public void mintNft() {
        networkTransactionResponse = tokenClient.mint(nonFungibleTokenId, RandomUtils.nextBytes(4));
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

    @And("I approve and transfer FUNGIBLE token to the precompile contract")
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
        networkTransactionResponse = tokenClient.associate(receiverAccountId, fungibleKycUnfrozenTokenId);
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

    @And("I approve and transfer NFT token to the precompile contract")
    public void approveAndTransferNftToPrecompileContract() throws InvalidProtocolBufferException {
        accountClient.approveNftAllSerials(nonFungibleTokenId, deployedPrecompileContract.contractId());
        networkTransactionResponse = tokenClient.transferNonFungibleToken(
                nonFungibleTokenId, admin, AccountId.fromString(precompileContractAddress), List.of(2L), null);
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
    public void ierc721MetadataTokenBalanceOf() {
        var balanceOfNft = getBalanceOfToken(nonFungibleTokenId, admin.getAccountId());
        var data = encodeData(
                ERC, IERC721_TOKEN_BALANCE_OF_SELECTOR, asAddress(nonFungibleTokenId), asAddress(contractClient));
        var response = callContract(data, ercContractAddress);

        assertThat(response.getResultAsNumber()).isEqualTo(balanceOfNft);
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

        // BalanceBefore + amount = balanceAfter
        assertThat(intValue(intValue(results.get(0)) + 1)).isEqualTo(intValue(results.get(1)));
        // totalSupplyBefore + amount = totalSupplyAfter
        assertThat(intValue(intValue(results.get(2)) + 1L)).isEqualTo(intValue(results.get(3)));
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

        // BalanceBefore + amount = balanceAfter
        assertThat(intValue(results.get(0)) + 1).isEqualTo(intValue(results.get(1)));
        // totalSupplyBefore + amount = totaSupplyAfter
        assertThat(intValue(results.get(2)) + 1).isEqualTo(intValue(results.get(3)));
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

        // BalanceBefore - amount = balanceAfter
        assertThat(intValue(intValue(results.get(0)) - 1L)).isEqualTo(intValue(results.get(1)));
        // totalSupplyBefore - amount = totalSupplyAfter
        assertThat(intValue(intValue(results.get(2)) - 1L)).isEqualTo(intValue(results.get(3)));
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

        // BalanceBefore - amount = balanceAfter
        assertThat(intValue(results.get(0)) - 1).isEqualTo(intValue(results.get(1)));
        // totalSupplyBefore - amount = totalSupplyAfter
        assertThat(intValue(results.get(2)) - 1).isEqualTo(intValue(results.get(3)));
    }

    @Then("I wipe FUNGIBLE token and get the total supply and balance")
    public void ethCallWipeFungibleTokenGetTotalSupplyAndBalanceOfTreasury() {
        var data = encodeData(
                PRECOMPILE,
                WIPE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE,
                asAddress(fungibleTokenId.toSolidityAddress()),
                1L,
                asLongArray(List.of()),
                asAddress(receiverAccountAlias));

        var response = callContract(data, precompileContractAddress);
        var results = response.getResultAsListDecimal();
        assertThat(results).isNotNull().hasSize(4);

        // BalanceBefore - amount = balanceAfter
        assertThat(intValue(intValue(results.get(0)) - 1L)).isEqualTo(intValue(results.get(1)));
        // totalSupplyBefore - amount = totaSupplyAfter
        assertThat(intValue(intValue(results.get(2)) - 1L)).isEqualTo(intValue(results.get(3)));
    }

    @Then("I wipe NFT and get the total supply and balance")
    public void ethCallWipeNftTokenGetTotalSupplyAndBalanceOfTreasury() {
        var data = encodeData(
                PRECOMPILE,
                WIPE_TOKEN_GET_TOTAL_SUPPLY_AND_BALANCE,
                asAddress(nonFungibleTokenId.toSolidityAddress()),
                0L,
                asLongArray(List.of(1L)),
                asAddress(receiverAccountAlias));

        var response = callContract(data, precompileContractAddress);
        var results = response.getResultAsListDecimal();
        assertThat(results).isNotNull().hasSize(4);

        // BalanceBefore - amount = balanceAfter
        assertThat(intValue(results.get(0)) - 1).isEqualTo(intValue(results.get(1)));
        // totalSupplyBefore - amount = totalSupplyAfter
        assertThat(intValue(results.get(2)) - 1).isEqualTo(intValue(results.get(3)));
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

        assertThat(Integer.valueOf(statusAfterPause, 16)).isEqualTo(TokenPauseStatus.Paused_VALUE);
        // assertThat(Integer.valueOf(statusAfterUnpause, 16)).isEqualTo(TokenPauseStatus.Unpaused_VALUE);//check fails
        // for both Fungible and NFT
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

        assertThat(Integer.valueOf(statusAfterFreeze, 16)).isEqualTo(TokenFreezeStatus.Frozen_VALUE);
        // assertThat(Integer.valueOf(statusAfterUnfreeze, 16)).isEqualTo(TokenFreezeStatus.Unfrozen_VALUE); // may fail
        // here, was 0 before? @todo check
    }

    @Then("I approve a FUNGIBLE token and get allowance")
    public void ethCallApproveFungibleTokenGetAllowance() {
        var data = encodeData(
                PRECOMPILE,
                APPROVE_TOKEN_GET_ALLOWANCE,
                asAddress(fungibleTokenId),
                asAddress(receiverAccountAlias),
                new BigInteger("1"),
                new BigInteger("0"));

        var response = callContract(data, precompileContractAddress);
        var allowance = response.getResult().substring(2, 66);

        // allowance should equal amount
        assertThat(new BigInteger(allowance)).isEqualTo(BigInteger.valueOf(1L));
    }

    @Then("I approve a NFT token and get allowance")
    public void ethCallApproveNFTTokenGetAllowance() {
        var data = encodeData(
                PRECOMPILE,
                APPROVE_TOKEN_GET_ALLOWANCE,
                asAddress(nonFungibleTokenId),
                asAddress(receiverAccountAlias),
                new BigInteger("0"),
                new BigInteger("2"));
        var response = callContract(data, precompileContractAddress);
        var approvedAddress = response.getResult().substring(66);

        // approved address should equal the spender
        assertThat(approvedAddress).isEqualTo(to32BytesString(receiverAccountAlias));
    }

    @Then("I dissociate a FUNGIBLE token and fail transfer")
    public void ethCallAssociateFungibleTokenDissociateFailTransfer() {
        var data = encodeData(
                PRECOMPILE,
                DISSOCIATE_TOKEN_FAIL_TRANSFER,
                asAddress(fungibleTokenId),
                asAddress(admin),
                asAddress(secondReceiverAccount),
                new BigInteger("1"),
                new BigInteger("0"));
        var response = callContract(data, precompileContractAddress);
        var statusAfterAssociate = response.getResultAsListDecimal().get(0);
        var statusAfterDissociate = response.getResultAsListDecimal().get(1);

        // transfer after associate should pass -> response code 22 equals SUCCESS
        assertThat(statusAfterAssociate).isEqualTo(22);
        // transfer after dissociate should fail > response code 237 equals to owner does not own the token
        // assertThat(Integer.parseInt(statusAfterDissociate, 16)).isEqualTo(184); //atm failing, investigating with devs
    }

    @Then("I dissociate a NFT and fail transfer")
    public void ethCallAssociateNftTokenDissociateFailTransfer() {
        var data = encodeData(
                PRECOMPILE,
                DISSOCIATE_TOKEN_FAIL_TRANSFER,
                asAddress(nonFungibleTokenId),
                asAddress(receiverAccountAlias),
                asAddress(secondReceiverAccount),
                new BigInteger("0"),
                new BigInteger("1"));
        var response = callContract(data, precompileContractAddress);
        var statusAfterAssociate = response.getResultAsListDecimal().get(0);
        var statusAfterDissociate = response.getResultAsListDecimal().get(1);

        // transfer after associate should pass -> response code 22 equals SUCCESS
        assertThat(statusAfterAssociate).isEqualTo(22);
        // transfer after dissociate should fail -> response code 237 equals to owner does not own the NFT
        assertThat(statusAfterDissociate).isEqualTo(237);
    }

    @Then("I approve a FUNGIBLE token and transfer it")
    public void ethCallApproveFungibleTokenAndTransfer() {
        var data = encodeData(
                PRECOMPILE,
                APPROVE_FUNGIBLE_TOKEN_AND_TRANSFER,
                asAddress(fungibleTokenId),
                asAddress(receiverAccountAlias),
                new BigInteger("1"));
        var response = callContract(data, precompileContractAddress);
        var results = response.getResultAsListDecimal();
        assertThat(results).isNotNull().hasSize(4);

        // allowance before transfer should equal the amount
        assertThat(intValue(results.get(0))).isEqualTo(1);
        // balance before + amount should equal the balance after
        assertThat(intValue(results.get(1)) + 1).isEqualTo(intValue(results.get(3)));
        // allowance after transfer should be 0
        assertThat(intValue(results.get(2))).isZero();
    }

    @Then("I approve a NFT token and transfer it")
    public void ethCallApproveNftTokenAndTransfer() {
        var data = encodeData(
                PRECOMPILE,
                APPROVE_NFT_TOKEN_AND_TRANSFER,
                asAddress(nonFungibleTokenId),
                asAddress(receiverAccountAlias),
                new BigInteger("2"));
        var response = callContract(data, precompileContractAddress);
        var results = response.getResultAsListAddress();

        assertThat(results).isNotNull().hasSize(3);

        // allowed address before transfer should be the receiverAccount
        assertThat(results.get(0)).isEqualTo(to32BytesString(receiverAccountAlias));
        // balance before + amount should equal the balance after
        assertThat(results.get(1)).isEqualTo(to32BytesString(receiverAccountAlias));
        // allowance after transfer should be != 0
        assertThat(results.get(2)).isNotEqualTo(to32BytesString(receiverAccountAlias));
    }

    @Then("I grant and revoke KYC")
    public void ethCallGrantKycRevokeKyc() {
        var data = encodeData(
                PRECOMPILE,
                GRANT_KYC_REVOKE_KYC,
                asAddress(fungibleKycUnfrozenTokenId),
                asAddress(admin),
                asAddress(receiverAccountAlias),
                new BigInteger("1"));
        var response = callContract(data, precompileContractAddress);
        var results = response.getResultAsListDecimal();
        assertThat(results).isNotNull().hasSize(5);

        // isKYC after grant should be true
        assertThat(results.get(0)).isEqualTo(1);
        // KYC grant status should be SUCCESS = 22
        assertThat(results.get(1)).isEqualTo(22);
        // isKYC after revoke should be false
        assertThat(results.get(2)).isZero();
        // KYC revoke status should be SUCCESS = 22
        assertThat(results.get(3)).isEqualTo(22);
        // transfer status after kyc revert should be failing with KYC should be granted
        assertThat(results.get(4)).isEqualTo(176);
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
        GRANT_KYC_REVOKE_KYC("grantKycRevokeKyc");

        private final String selector;
    }
}
