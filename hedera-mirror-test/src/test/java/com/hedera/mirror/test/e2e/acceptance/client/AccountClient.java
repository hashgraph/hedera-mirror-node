package com.hedera.mirror.test.e2e.acceptance.client;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.retry.support.RetryTemplate;

import com.hedera.hashgraph.sdk.AccountBalanceQuery;
import com.hedera.hashgraph.sdk.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.TransferTransaction;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;

@Log4j2
@Named
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AccountClient extends AbstractNetworkClient {

    private static final long DEFAULT_INITIAL_BALANCE = 50_000_000L; // 0.5 ℏ
    private static final long SMALL_INITIAL_BALANCE = 1_000L; // 1000 tℏ

    private ExpandedAccountId tokenTreasuryAccount = null;

    private final Map<AccountNameEnum, ExpandedAccountId> accountMap = new ConcurrentHashMap<>();

    public AccountClient(SDKClient sdkClient, RetryTemplate retryTemplate) {
        super(sdkClient, retryTemplate);
        log.debug("Creating Account Client");
    }

    public ExpandedAccountId getTokenTreasuryAccount() throws Exception {
        if (tokenTreasuryAccount == null) {
            tokenTreasuryAccount = createNewAccount(DEFAULT_INITIAL_BALANCE);
            log.debug("Treasury Account: {} will be used for current test session", tokenTreasuryAccount);
        }

        return tokenTreasuryAccount;
    }

    public ExpandedAccountId getAccount(AccountNameEnum accountNameEnum) {
        // retrieve account, setting if it doesn't exist
        AtomicReference<Exception> encounteredException = null;
        ExpandedAccountId accountId = accountMap
                .computeIfAbsent(accountNameEnum, x -> {
                    try {
                        return createNewAccount(SMALL_INITIAL_BALANCE,
                                accountNameEnum);
                    } catch (Exception e) {
                        encounteredException.set(e);
                        log.trace("Issue creating additional account: {}, ex: {}", accountNameEnum, e);
                    }
                    return null;
                });

        if (accountId == null) {
            throw new NetworkException(encounteredException.get().getMessage());
        }

        log.debug("Retrieve Account: {}, {}", accountId, accountNameEnum);
        return accountId;
    }

    @Override
    public long getBalance() throws TimeoutException, PrecheckStatusException {
        return getBalance(sdkClient.getExpandedOperatorAccountId().getAccountId());
    }

    public long getBalance(AccountId accountId) throws TimeoutException, PrecheckStatusException {
        Hbar balance = new AccountBalanceQuery()
                .setAccountId(accountId)
                .execute(client)
                .hbars;

        log.debug("{} balance is {}", accountId, balance);

        return balance.toTinybars();
    }

    public TransferTransaction getCryptoTransferTransaction(AccountId sender, AccountId recipient, Hbar hbarAmount) {
        return new TransferTransaction()
                .addHbarTransfer(sender, hbarAmount.negated())
                .addHbarTransfer(recipient, hbarAmount)
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTransactionMemo("transfer test");
    }

    public TransactionReceipt sendCryptoTransfer(AccountId recipient, Hbar hbarAmount) throws Exception {
        log.debug(
                "Send CryptoTransfer of {} tℏ from {} to {}", hbarAmount.toTinybars(),
                sdkClient.getExpandedOperatorAccountId().getAccountId(),
                recipient);

        TransferTransaction cryptoTransferTransaction = getCryptoTransferTransaction(sdkClient
                .getExpandedOperatorAccountId().getAccountId(), recipient, hbarAmount);

        TransactionReceipt transactionReceipt = executeTransactionAndRetrieveReceipt(cryptoTransferTransaction, null)
                .getReceipt();

        log.debug("Sent CryptoTransfer");

        return transactionReceipt;
    }

    public AccountCreateTransaction getAccountCreateTransaction(Hbar initialBalance, KeyList publicKeys,
                                                                boolean receiverSigRequired, String memo) {
        return new AccountCreateTransaction()
                .setInitialBalance(initialBalance)
                // The only _required_ property here is `key`
                .setKey(publicKeys)
                .setAccountMemo(memo)
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setReceiverSignatureRequired(receiverSigRequired)
                .setTransactionMemo(memo);
    }

    public ExpandedAccountId createNewAccount(long initialBalance) throws Exception {
        return createCryptoAccount(Hbar.fromTinybars(initialBalance), false, null, null);
    }

    public ExpandedAccountId createNewAccount(long initialBalance, AccountNameEnum accountNameEnum) throws Exception {
        return createCryptoAccount(
                Hbar.fromTinybars(initialBalance),
                accountNameEnum.receiverSigRequired,
                null,
                accountNameEnum.toString());
    }

    public ExpandedAccountId createCryptoAccount(Hbar initialBalance, boolean receiverSigRequired, KeyList keyList,
                                                 String memo)
            throws Exception {
        // 1. Generate a Ed25519 private, public key pair
        PrivateKey privateKey = PrivateKey.generate();
        PublicKey publicKey = privateKey.getPublicKey();

        log.trace("Private key = {}", privateKey);
        log.trace("Public key = {}", publicKey);

        KeyList publicKeyList = KeyList.of(privateKey.getPublicKey());
        if (keyList != null) {
            publicKeyList.addAll(keyList);
        }

        AccountCreateTransaction accountCreateTransaction = getAccountCreateTransaction(
                initialBalance,
                publicKeyList,
                receiverSigRequired,
                String.format("Mirror new crypto account: %s_%s", memo == null ? "" : memo, Instant.now()));

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(accountCreateTransaction,
                        receiverSigRequired ? KeyList.of(privateKey) : null);
        TransactionReceipt receipt = networkTransactionResponse.getReceipt();

        AccountId newAccountId = receipt.accountId;

        // verify accountId
        if (receipt.accountId == null) {
            throw new NetworkException(String.format("Receipt for %s returned no accountId, receipt: %s",
                    networkTransactionResponse.getTransactionId(),
                    receipt));
        }

        log.debug("Created new account {}, receiverSigRequired: {}", newAccountId, receiverSigRequired);
        return new ExpandedAccountId(newAccountId, privateKey, privateKey.getPublicKey());
    }

    @RequiredArgsConstructor
    public enum AccountNameEnum {
        ALICE(false),
        BOB(false),
        CAROL(true),
        DAVE(true);

        private final boolean receiverSigRequired;

        @Override
        public String toString() {
            return String.format("%s, receiverSigRequired: %s", name(), receiverSigRequired);
        }
    }
}
