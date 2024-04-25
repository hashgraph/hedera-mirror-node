/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.nextBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.mirror.rest.model.NftAllowance;
import com.hedera.mirror.rest.model.NftAllowancesResponse;
import com.hedera.mirror.rest.model.NftTransactionHistory;
import com.hedera.mirror.rest.model.NftTransactionTransfer;
import com.hedera.mirror.rest.model.TransactionByIdResponse;
import com.hedera.mirror.rest.model.TransactionDetail;
import com.hedera.mirror.rest.model.TransactionNftTransfersInner;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@CustomLog
@RequiredArgsConstructor
public class NftFeature extends AbstractFeature {

    private final TokenClient tokenClient;
    private final AccountClient accountClient;
    private final MirrorNodeClient mirrorClient;

    @Getter
    private TokenId tokenId;

    private final Map<TokenId, List<Long>> tokenSerialNumbers = new HashMap<>();

    @Given("I ensure NFT {token} has been created")
    public void createNonFungibleToken(TokenClient.TokenNameEnum tokenName) {
        final var result = tokenClient.getToken(tokenName);
        this.networkTransactionResponse = result.response();
        this.tokenId = result.tokenId();
        tokenSerialNumbers.put(tokenId, new ArrayList<>());
    }

    @Given("I associate account {account} with NFT {token}")
    public void associateToken(AccountClient.AccountNameEnum accountName, TokenClient.TokenNameEnum tokenName) {
        var accountId = accountClient.getAccount(accountName);
        tokenId = tokenClient.getToken(tokenName).tokenId();
        associateWithToken(accountId, tokenId);
    }

    @Then("the mirror node REST API should return the transaction for the NFTs")
    @RetryAsserts
    public void verifyMirrorAPIResponses() {
        verifyTransactions();
    }

    @Given("I mint a serial number from the NFT")
    public void mintNftToken() {
        networkTransactionResponse = tokenClient.mint(tokenId, nextBytes(4));
        assertNotNull(networkTransactionResponse.getTransactionId());
        TransactionReceipt receipt = networkTransactionResponse.getReceipt();
        assertNotNull(receipt);
        assertThat(receipt.serials.size()).isOne();
        long serialNumber = receipt.serials.get(0);
        assertThat(serialNumber).isPositive();
        tokenSerialNumbers.get(tokenId).add(serialNumber);
    }

    @Given("I approve {account} to all serials of the NFT")
    public void setNonFungibleTokenAllowance(AccountClient.AccountNameEnum accountName) {
        var spenderAccountId = accountClient.getAccount(accountName).getAccountId();
        networkTransactionResponse = accountClient.approveNftAllSerials(tokenId, spenderAccountId);
        associateWithToken(accountClient.getAccount(accountName), tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then(
            "the mirror node REST API should confirm the approved allowance of NFT {token} and {account} when owner is {string}")
    @RetryAsserts
    public void verifyMirrorAPIApprovedTokenAllowanceResponse(
            TokenClient.TokenNameEnum tokenName, AccountClient.AccountNameEnum accountName, String owner) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var spenderId = accountClient.getAccount(accountName);
        var isOwner = owner.equalsIgnoreCase("true");
        verifyMirrorAPIApprovedNftAllowanceResponse(tokenId, spenderId, true, isOwner);
    }

    @Given("{account} transfers serial number {int} to {account}")
    public void transferNftsToRecipient(
            AccountClient.AccountNameEnum spenderName,
            int serialNumberIndex,
            AccountClient.AccountNameEnum accountName) {
        // spender needs to transfer so they need to sign the transaction.
        ExpandedAccountId payer = accountClient.getAccount(spenderName);
        transferNfts(tokenId, tokenSerialNumbers.get(tokenId).get(serialNumberIndex), payer, accountName);
    }

    @Then(
            "the mirror node REST API should confirm the approved transfer of serial number {int} and confirm the new owner is {account}")
    @RetryAsserts
    public void verifyMirrorNftTransfer(int serialNumberIndex, AccountClient.AccountNameEnum accountName) {
        Long serialNumber = tokenSerialNumbers.get(tokenId).get(serialNumberIndex);
        var accountId = accountClient.getAccount(accountName);
        verifyTransactions();
        verifyNftTransfers(tokenId, serialNumber, accountId);
        verifyNftTransactions(tokenId, serialNumber);
    }

    @Given("I delete the allowance on NFT {token} for {account}")
    public void deleteNonFungibleTokenAllowance(
            TokenClient.TokenNameEnum tokenName, AccountClient.AccountNameEnum spenderAccountName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var owner = accountClient.getClient().getOperatorAccountId();
        var spenderAccountId = accountClient.getAccount(spenderAccountName);
        networkTransactionResponse = accountClient.deleteNftAllowance(tokenId, owner, spenderAccountId.getAccountId());
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then(
            "the mirror node REST API should confirm the approved allowance for NFT {token} and {account} is no longer available")
    @RetryAsserts
    public void verifyTokenAllowanceDelete(
            TokenClient.TokenNameEnum tokenName, AccountClient.AccountNameEnum spenderName) {
        verifyMirrorTransactionsResponse(mirrorClient, HttpStatus.OK.value());

        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var spender = accountClient.getAccount(spenderName);

        // Once we change this API to not return approved_for_all=false this test will change to check for an empty list
        verifyMirrorAPIApprovedNftAllowanceResponse(tokenId, spender, false, true);
    }

    private void associateWithToken(ExpandedAccountId accountId, TokenId tokenId) {
        networkTransactionResponse = tokenClient.associate(accountId, tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private int getIndexOrDefault(Integer index) {
        return index != null ? index : 0;
    }

    private void transferNfts(
            TokenId tokenId, long serialNumber, ExpandedAccountId sender, AccountClient.AccountNameEnum accountName) {
        var receiver = accountClient.getAccount(accountName).getAccountId();
        // Operator will pay for this transfer
        ExpandedAccountId payer = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        networkTransactionResponse = tokenClient.transferNonFungibleToken(
                tokenId, payer, receiver, List.of(serialNumber), sender.getPrivateKey());
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void verifyMirrorAPIApprovedNftAllowanceResponse(
            TokenId tokenId, ExpandedAccountId spenderId, boolean approvedForAll, boolean isOwner) {
        verifyMirrorTransactionsResponse(mirrorClient, HttpStatus.OK.value());

        NftAllowancesResponse mirrorNftAllowanceResponse;

        var owner = accountClient.getClient().getOperatorAccountId().toString();
        var spender = spenderId.getAccountId().toString();
        var token = tokenId.toString();

        if (isOwner) {
            mirrorNftAllowanceResponse = mirrorClient.getAccountNftAllowanceBySpender(owner, token, spender);
        } else {
            mirrorNftAllowanceResponse = mirrorClient.getAccountNftAllowanceByOwner(spender, token, owner);
        }

        // verify valid set of allowance
        assertThat(mirrorNftAllowanceResponse.getAllowances())
                .isNotEmpty()
                .first()
                .isNotNull()
                .returns(approvedForAll, NftAllowance::getApprovedForAll)
                .returns(owner, NftAllowance::getOwner)
                .returns(spender, NftAllowance::getSpender)
                .returns(token, NftAllowance::getTokenId)
                .extracting(NftAllowance::getTimestamp)
                .isNotNull()
                .satisfies(t -> assertThat(t.getFrom()).isNotBlank())
                .satisfies(t -> assertThat(t.getTo()).isBlank());
    }

    private NftTransactionTransfer verifyNftTransactions(TokenId tokenId, Long serialNumber) {
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        NftTransactionHistory mirrorTransactionsResponse = mirrorClient.getNftTransactions(tokenId, serialNumber);

        List<NftTransactionTransfer> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        NftTransactionTransfer mirrorTransaction = transactions.get(0);
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);

        return mirrorTransaction;
    }

    private void verifyNftTransfers(TokenId tokenId, Long serialNumber, ExpandedAccountId accountId) {
        var transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        var mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        List<TransactionDetail> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        TransactionDetail mirrorTransaction = transactions.get(0);
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());

        assertThat(mirrorTransaction.getNftTransfers())
                .filteredOn(transfer -> tokenId.toString().equals(transfer.getTokenId()))
                .map(TransactionNftTransfersInner::getSerialNumber)
                .containsExactly(serialNumber);

        var nftInfo = mirrorClient.getNftInfo(tokenId.toString(), serialNumber);
        assertThat(nftInfo.getAccountId()).isEqualTo(accountId.toString());
    }

    private TransactionDetail verifyTransactions() {

        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        TransactionByIdResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        List<TransactionDetail> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        TransactionDetail mirrorTransaction = transactions.get(0);
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());
        assertThat(mirrorTransaction.getResult()).isEqualTo("SUCCESS");
        return mirrorTransaction;
    }
}
