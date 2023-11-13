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
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.PRECOMPILE;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.ADDRESS_BALANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.DEPLOY_NESTED_CONTRACT_CONTRACT_VIA_CREATE2_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.DEPLOY_NESTED_CONTRACT_CONTRACT_VIA_CREATE_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.HTS_GET_DEFAULT_FREEZE_STATUS_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.HTS_GET_TOKEN_DEFAULT_KYC_STATUS_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.HTS_IS_FROZEN_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.HTS_IS_KYC_GRANTED_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.HTS_IS_TOKEN_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.IERC721_TOKEN_BALANCE_OF_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.IERC721_TOKEN_NAME_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.IERC721_TOKEN_SYMBOL_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.IERC721_TOKEN_TOTAL_SUPPLY_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.REENTRANCY_CALL_WITH_GAS;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.STATE_UPDATE_N_TIMES_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.CallFeature.ContractMethods.UPDATE_COUNTER_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorAccountBalance;
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
public class CallFeature extends AbstractFeature {

    private static final String HEX_REGEX = "^[0-9a-fA-F]+$";
    private final AccountClient accountClient;
    private final MirrorNodeClient mirrorClient;
    private final TokenClient tokenClient;
    private String ercContractAddress;
    private String precompileContractAddress;
    private String estimateContractAddress;
    private ExpandedAccountId receiverAccountId;

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

    @Given("I successfully create ERC contract")
    public void createNewERCtestContract() throws IOException {
        var deployedContract = getContract(ERC);
        ercContractAddress = deployedContract.contractId().toSolidityAddress();
    }

    @Given("I successfully create Precompile contract")
    public void createNewPrecompileTestContract() throws IOException {
        var deployedContract = getContract(PRECOMPILE);
        precompileContractAddress = deployedContract.contractId().toSolidityAddress();
    }

    @Given("I successfully create EstimateGas contract")
    public void createNewEstimateTestContract() throws IOException {
        var deployedContract = getContract(ESTIMATE_GAS);
        estimateContractAddress = deployedContract.contractId().toSolidityAddress();
        receiverAccountId = accountClient.getAccount(AccountNameEnum.BOB);
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
    @Then("I call function with IERC721Metadata token {string} totalSupply")
    public void ierc721MetadataTokenTotalSupply(String tokenName) {
        var tokenId = tokenClient
                .getToken(TokenClient.TokenNameEnum.valueOf(tokenName))
                .tokenId();
        var totalSupplyOfNft = mirrorClient.getTokenInfo(tokenId.toString()).getTotalSupply();

        var data = encodeData(ERC, IERC721_TOKEN_TOTAL_SUPPLY_SELECTOR, asAddress(tokenId));
        var response = callContract(data, ercContractAddress);

        assertThat(response.getResultAsNumber()).isEqualTo(totalSupplyOfNft);
    }

    // ETHCALL-020
    @RetryAsserts
    @Then("I call function with IERC721 token {string} balanceOf owner")
    public void ierc721MetadataTokenBalanceOf(String tokenName) {
        var tokenId = tokenClient
                .getToken(TokenClient.TokenNameEnum.valueOf(tokenName))
                .tokenId();
        var allTokens = mirrorClient
                .getAccountDetailsByAccountId(AccountId.fromSolidityAddress(contractClient.getClientAddress()))
                .getBalanceInfo()
                .getTokens();
        var balanceOfNft = allTokens.stream()
                .filter(token -> tokenId.toString().equals(token.getTokenId()))
                .mapToLong(MirrorAccountBalance.Token::getBalance)
                .findFirst();

        var data = encodeData(ERC, IERC721_TOKEN_BALANCE_OF_SELECTOR, asAddress(tokenId), asAddress(contractClient));
        var response = callContract(data, ercContractAddress);

        assertThat(response.getResultAsNumber()).isEqualTo(balanceOfNft.getAsLong());
    }

    @RetryAsserts
    @Then("I verify the precompile contract bytecode is deployed")
    public void contractDeployed() {
        var response = mirrorClient.getContractInfo(precompileContractAddress);
        assertThat(response.getBytecode()).isNotBlank();
        assertThat(response.getRuntimeBytecode()).isNotBlank();
    }

    // ETHCALL-025
    @RetryAsserts
    @Then("I call function with HederaTokenService isToken token {string}")
    public void htsIsToken(String tokenName) {
        var tokenId = tokenClient
                .getToken(TokenClient.TokenNameEnum.valueOf(tokenName))
                .tokenId();

        var data = encodeData(PRECOMPILE, HTS_IS_TOKEN_SELECTOR, asAddress(tokenId));
        var response = callContract(data, precompileContractAddress);
        assertThat(response.getResultAsBoolean()).isTrue();
    }

    // ETHCALL-026
    @RetryAsserts
    @Then("I call function with HederaTokenService isFrozen token {string}, account")
    public void htsIsFrozen(String tokenName) {
        var tokenId = tokenClient
                .getToken(TokenClient.TokenNameEnum.valueOf(tokenName))
                .tokenId();

        var data = encodeData(PRECOMPILE, HTS_IS_FROZEN_SELECTOR, asAddress(tokenId), asAddress(contractClient));
        var response = callContract(data, precompileContractAddress);

        assertThat(response.getResultAsBoolean()).isFalse();
    }

    // ETHCALL-027
    @RetryAsserts
    @Then("I call function with HederaTokenService isKyc token {string}, account")
    public void htsIsKyc(String tokenName) {
        var tokenId = tokenClient
                .getToken(TokenClient.TokenNameEnum.valueOf(tokenName))
                .tokenId();

        var data = encodeData(PRECOMPILE, HTS_IS_KYC_GRANTED_SELECTOR, asAddress(tokenId), asAddress(contractClient));
        var response = callContract(data, precompileContractAddress);

        assertThat(response.getResultAsBoolean()).isTrue();
    }

    // ETHCALL-028
    @RetryAsserts
    @Then("I call function with HederaTokenService getTokenDefaultFreezeStatus token {string}")
    public void htsGetTokenDefaultFreezeStatus(String tokenName) {
        var tokenId = tokenClient
                .getToken(TokenClient.TokenNameEnum.valueOf(tokenName))
                .tokenId();

        var data = encodeData(PRECOMPILE, HTS_GET_DEFAULT_FREEZE_STATUS_SELECTOR, asAddress(tokenId));
        var response = callContract(data, precompileContractAddress);

        assertThat(response.getResultAsBoolean()).isFalse();
    }

    // ETHCALL-029
    @RetryAsserts
    @Then("I call function with HederaTokenService getTokenDefaultKycStatus token {string}")
    public void htsGetTokenDefaultKycStatus(String tokenName) {
        var tokenId = tokenClient
                .getToken(TokenClient.TokenNameEnum.valueOf(tokenName))
                .tokenId();

        var data = encodeData(PRECOMPILE, HTS_GET_TOKEN_DEFAULT_KYC_STATUS_SELECTOR, asAddress(tokenId));
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

    @Then("I successfully update the balance of an account and get the updated balance")
    public void getBalance() {
        final var receiverAddress = asAddress(receiverAccountId.getAccountId().toSolidityAddress());
        var data = encodeData(ESTIMATE_GAS, ADDRESS_BALANCE, receiverAddress);
        var initialBalance = callContract(data, estimateContractAddress).getResultAsNumber();
        networkTransactionResponse = accountClient.sendCryptoTransfer(
                receiverAccountId.getAccountId(),
                Hbar.fromTinybars(initialBalance.longValue()),
                receiverAccountId.getPrivateKey());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
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

    private void validateAddresses(String[] addresses) {
        assertNotEquals(addresses[0], addresses[1]);
        assertTrue(addresses[0].matches(HEX_REGEX));
        assertTrue(addresses[1].matches(HEX_REGEX));
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
        ADDRESS_BALANCE("addressBalance"),
        REENTRANCY_CALL_WITH_GAS("reentrancyCallWithGas");

        private final String selector;
    }
}
