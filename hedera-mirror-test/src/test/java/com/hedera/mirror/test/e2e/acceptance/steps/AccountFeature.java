/*
 * Copyright (C) 2020-2025 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.rest.model.TransactionTypes.CRYPTOCREATEACCOUNT;
import static com.hedera.mirror.rest.model.TransactionTypes.CRYPTOTRANSFER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.mirror.rest.model.CryptoAllowance;
import com.hedera.mirror.rest.model.TransactionByIdResponse;
import com.hedera.mirror.rest.model.TransactionDetail;
import com.hedera.mirror.rest.model.TransactionTransfersInner;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.Cleanable;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.OrderComparator;
import org.springframework.http.HttpStatus;

@CustomLog
@Data
@RequiredArgsConstructor
public class AccountFeature extends AbstractFeature {

    private static final AtomicReference<Runnable> CLEANUP = new AtomicReference<>();

    private final AccountClient accountClient;
    private final MirrorNodeClient mirrorClient;
    private final Collection<Cleanable> cleanables;

    private AccountId receiverAccountId;
    private ExpandedAccountId senderAccountId;
    private ExpandedAccountId spenderAccountId;
    private long startingBalance;

    @AfterAll
    public static void cleanup() {
        var cleanup = CLEANUP.get();
        if (cleanup != null) {
            cleanup.run();
        }
    }

    @Before
    public void setup() {
        // This hack allows us to invoke non-static beans in a static @AfterAll
        CLEANUP.compareAndSet(
                null, () -> cleanables.stream().sorted(OrderComparator.INSTANCE).forEach(Cleanable::clean));
    }

    @When("I create a new account with balance {long} tℏ")
    public void createNewAccount(long initialBalance) {
        senderAccountId = accountClient.createNewAccount(initialBalance);
        assertNotNull(senderAccountId);
        assertNotNull(senderAccountId.getAccountId());
    }

    @Given("I send {long} tℏ to {string}")
    public void treasuryDisbursement(long amount, String accountName) {
        senderAccountId = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));

        startingBalance = accountClient.getBalance(senderAccountId);

        networkTransactionResponse =
                accountClient.sendCryptoTransfer(senderAccountId.getAccountId(), Hbar.fromTinybars(amount), null);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I send {long} tℏ to {string} alias not present in the network")
    public void createAccountOnTransferForAlias(long amount, String keyType) {
        var recipientPrivateKey =
                "ED25519".equalsIgnoreCase(keyType) ? PrivateKey.generateED25519() : PrivateKey.generateECDSA();

        receiverAccountId = recipientPrivateKey.toAccountId(0, 0);
        networkTransactionResponse =
                accountClient.sendCryptoTransfer(receiverAccountId, Hbar.fromTinybars(amount), null);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the transfer auto creates a new account with balance of transferred amount {long} tℏ")
    public void verifyAccountCreated(long amount) {
        var accountInfo = mirrorClient.getAccountDetailsUsingAlias(receiverAccountId);
        var transactions = mirrorClient
                .getTransactions(networkTransactionResponse.getTransactionIdStringNoCheckSum())
                .getTransactions()
                .stream()
                .sorted(Comparator.comparing(TransactionDetail::getConsensusTimestamp))
                .toList();

        assertThat(accountInfo.getAccount()).isNotNull();
        assertThat(accountInfo.getBalance().getBalance()).isEqualTo(amount);
        assertThat(accountInfo.getTransactions()).hasSize(1);
        assertThat(transactions).hasSize(2);

        var createAccountTransaction = transactions.get(0);
        var transferTransaction = transactions.get(1);

        assertThat(transferTransaction)
                .usingRecursiveComparison()
                .ignoringFields("assessedCustomFees")
                .isEqualTo(accountInfo.getTransactions().get(0));

        assertThat(createAccountTransaction.getName()).isEqualTo(CRYPTOCREATEACCOUNT);
        assertThat(createAccountTransaction.getConsensusTimestamp()).isEqualTo(accountInfo.getCreatedTimestamp());
    }

    @When("I send {long} tℏ to newly created account")
    public void sendTinyHbars(long amount) {
        startingBalance = accountClient.getBalance(senderAccountId);
        networkTransactionResponse =
                accountClient.sendCryptoTransfer(senderAccountId.getAccountId(), Hbar.fromTinybars(amount), null);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @When("I send {long} tℏ to account {int}")
    public void sendTinyHbars(long amount, int accountNum) {
        senderAccountId = new ExpandedAccountId(new AccountId(accountNum));
        startingBalance = accountClient.getBalance(senderAccountId);
        networkTransactionResponse =
                accountClient.sendCryptoTransfer(senderAccountId.getAccountId(), Hbar.fromTinybars(amount), null);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @When("I send {int} ℏ to account {int}")
    public void sendHbars(int amount, int accountNum) {
        senderAccountId = new ExpandedAccountId(new AccountId(accountNum));
        startingBalance = accountClient.getBalance(senderAccountId);
        networkTransactionResponse =
                accountClient.sendCryptoTransfer(senderAccountId.getAccountId(), Hbar.from(amount), null);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I approve {string} to transfer up to {long} tℏ")
    public void approveCryptoAllowance(String spender, long amount) {
        setCryptoAllowance(spender, amount);
    }

    @When("{string} transfers {long} tℏ from the approved allowance to {string}")
    public void transferFromAllowance(String spender, long amount, String receiver) {
        spenderAccountId = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(spender));
        receiverAccountId = accountClient
                .getAccount(AccountClient.AccountNameEnum.valueOf(receiver))
                .getAccountId();
        networkTransactionResponse = accountClient.sendApprovedCryptoTransfer(
                spenderAccountId, receiverAccountId, Hbar.fromTinybars(amount));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the new balance should reflect cryptotransfer of {long}")
    public void accountReceivedFunds(long amount) {
        assertThat(accountClient.getBalance(senderAccountId)).isGreaterThanOrEqualTo(startingBalance + amount);
    }

    @Then("the mirror node REST API should return status {int} for the crypto transfer transaction")
    public void verifyMirrorAPICryptoTransferResponse(int status) {
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        TransactionByIdResponse mirrorTransactionByIdResponse = mirrorClient.getTransactions(transactionId);

        // verify valid set of transactions
        List<TransactionDetail> transactions = mirrorTransactionByIdResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();

        // verify transaction details
        TransactionDetail mirrorTransaction = transactions.get(0);
        if (status == HttpStatus.OK.value()) {
            assertThat(mirrorTransaction.getResult()).isEqualTo("SUCCESS");
        }
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());
        assertThat(mirrorTransaction.getName()).isEqualTo(CRYPTOTRANSFER);

        assertThat(mirrorTransaction.getTransfers()).hasSizeGreaterThanOrEqualTo(2); // Minimal fee transfers

        // verify transfer credit and debits balance out
        long transferSum = 0;
        for (TransactionTransfersInner cryptoTransfer : mirrorTransaction.getTransfers()) {
            transferSum += cryptoTransfer.getAmount();
        }

        assertThat(transferSum).isZero();
    }

    @Then("the mirror node REST API should confirm the approved {long} tℏ crypto allowance")
    public void verifyMirrorAPIApprovedCryptoAllowanceResponse(long approvedAmount) {
        verifyMirrorAPIApprovedCryptoAllowanceResponse(approvedAmount, 0L);
    }

    @And("the mirror node REST API should confirm the approved allowance of {long} tℏ was debited by {long} tℏ")
    public void verifyMirrorAPICryptoAllowanceAmountResponse(long approvedAmount, long transferAmount) {
        verifyMirrorAPIApprovedCryptoAllowanceResponse(approvedAmount, transferAmount);
    }

    private void verifyMirrorAPIApprovedCryptoAllowanceResponse(long approvedAmount, long transferAmount) {
        verifyMirrorTransactionsResponse(mirrorClient, HttpStatus.OK.value());

        var owner = accountClient.getClient().getOperatorAccountId().toString();
        var spender = spenderAccountId.getAccountId().toString();
        var mirrorCryptoAllowanceResponse = mirrorClient.getAccountCryptoAllowanceBySpender(owner, spender);
        var remainingAmount = approvedAmount - transferAmount;

        // verify valid set of allowance
        assertThat(mirrorCryptoAllowanceResponse.getAllowances())
                .isNotEmpty()
                .first()
                .isNotNull()
                .returns(remainingAmount, CryptoAllowance::getAmount)
                .returns(approvedAmount, CryptoAllowance::getAmountGranted)
                .returns(owner, CryptoAllowance::getOwner)
                .returns(spender, CryptoAllowance::getSpender)
                .extracting(CryptoAllowance::getTimestamp)
                .isNotNull()
                .satisfies(t -> assertThat(t.getFrom()).isNotBlank())
                .satisfies(t -> assertThat(t.getTo()).isBlank());
    }

    @Then("the mirror node REST API should confirm the approved transfer of {long} tℏ")
    public void verifyMirrorAPIApprovedCryptoTransferResponse(long transferAmount) {
        var transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        var mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        // verify valid set of transactions
        var owner = accountClient.getClient().getOperatorAccountId().toString();
        var expectedCryptoTransfer = new TransactionTransfersInner()
                .account(owner)
                .amount(-transferAmount)
                .isApproval(true);
        var transactions = mirrorTransactionsResponse.getTransactions();
        assertThat(transactions).hasSize(1).first().satisfies(t -> assertThat(t.getTransfers())
                .contains(expectedCryptoTransfer));
    }

    @When("I delete the crypto allowance for {string}")
    public void deleteCryptoAllowance(String spender) {
        setCryptoAllowance(spender, 0);
    }

    @Then("the mirror node REST API should confirm the crypto allowance no longer exists")
    public void verifyCryptoAllowanceDelete() {
        verifyMirrorTransactionsResponse(mirrorClient, HttpStatus.OK.value());

        var owner = accountClient.getClient().getOperatorAccountId().toString();
        var spender = spenderAccountId.getAccountId().toString();
        var mirrorCryptoAllowanceResponse = mirrorClient.getAccountCryptoAllowanceBySpender(owner, spender);
        assertThat(mirrorCryptoAllowanceResponse.getAllowances()).isEmpty();
    }

    private void setCryptoAllowance(String accountName, long amount) {
        spenderAccountId = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        networkTransactionResponse =
                accountClient.approveCryptoAllowance(spenderAccountId.getAccountId(), Hbar.fromTinybars(amount));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }
}
