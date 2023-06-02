/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.test.e2e.acceptance.client;

import com.hedera.hashgraph.sdk.AccountAllowanceApproveTransaction;
import com.hedera.hashgraph.sdk.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.TransferTransaction;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorAccountResponse;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import jakarta.inject.Named;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.retry.support.RetryTemplate;

@Named
public class AccountClient extends AbstractNetworkClient {

    private static final long DEFAULT_INITIAL_BALANCE = 50_000_000L; // 0.5 ℏ
    private static final long SMALL_INITIAL_BALANCE = 500_000L; // 0.005 ℏ
    private static Supplier<Long> COST = () -> 0L;

    private final Map<AccountNameEnum, ExpandedAccountId> accountMap = new ConcurrentHashMap<>();
    private ExpandedAccountId tokenTreasuryAccount = null;

    public AccountClient(SDKClient sdkClient, RetryTemplate retryTemplate) {
        super(sdkClient, retryTemplate);
        final long initialBalance = getBalance();
        log.info("Operator account {} initial balance is {}", sdkClient.getExpandedOperatorAccountId(), initialBalance);
        COST = () -> initialBalance - getBalance();
    }

    // Prints the operator balance. Static because used only by the static @AfterAll.
    public static long getCost() {
        return COST.get();
    }

    public synchronized ExpandedAccountId getTokenTreasuryAccount() {
        if (tokenTreasuryAccount == null) {
            tokenTreasuryAccount = createNewAccount(DEFAULT_INITIAL_BALANCE);
            log.debug("Treasury Account: {} will be used for current test session", tokenTreasuryAccount);
        }

        return tokenTreasuryAccount;
    }

    public ExpandedAccountId getAccount(AccountNameEnum accountNameEnum) {
        // retrieve account, setting if it doesn't exist
        ExpandedAccountId accountId = accountMap.computeIfAbsent(accountNameEnum, x -> {
            try {
                return createNewAccount(SMALL_INITIAL_BALANCE, accountNameEnum);
            } catch (Exception e) {
                log.warn("Issue creating additional account: {}, ex: {}", accountNameEnum, e);
                return null;
            }
        });

        if (accountId == null) {
            throw new NetworkException("Null accountId retrieved from receipt");
        }

        if (log.isDebugEnabled()) {
            long balance = getBalance(accountId);
            log.debug("Retrieved Account: {}, {} w balance {}", accountId, accountNameEnum, balance);
        }

        return accountId;
    }

    public TransferTransaction getCryptoTransferTransaction(AccountId sender, AccountId recipient, Hbar hbarAmount) {
        TransferTransaction transferTransaction = new TransferTransaction()
                .addHbarTransfer(sender, hbarAmount.negated())
                .addHbarTransfer(recipient, hbarAmount)
                .setTransactionMemo(getMemo("Crypto transfer"));
        return transferTransaction;
    }

    public NetworkTransactionResponse sendApprovedCryptoTransfer(
            ExpandedAccountId spender, AccountId recipient, Hbar hbarAmount) {
        var transferTransaction = new TransferTransaction()
                .addApprovedHbarTransfer(getClient().getOperatorAccountId(), hbarAmount.negated())
                .addHbarTransfer(recipient, hbarAmount)
                .setTransactionMemo(getMemo("Approved transfer"));
        var response = executeTransactionAndRetrieveReceipt(transferTransaction, spender);
        log.info(
                "Approved transfer {} from {} to {} via {}",
                hbarAmount,
                spender,
                recipient,
                response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse sendCryptoTransfer(AccountId recipient, Hbar hbarAmount) {
        return sendCryptoTransfer(sdkClient.getExpandedOperatorAccountId(), recipient, hbarAmount);
    }

    private NetworkTransactionResponse sendCryptoTransfer(
            ExpandedAccountId sender, AccountId recipient, Hbar hbarAmount) {
        var cryptoTransferTransaction = getCryptoTransferTransaction(sender.getAccountId(), recipient, hbarAmount);
        var response = executeTransactionAndRetrieveReceipt(cryptoTransferTransaction);
        log.info("Transferred {} from {} to {} via {}", hbarAmount, sender, recipient, response.getTransactionId());
        return response;
    }

    public AccountCreateTransaction getAccountCreateTransaction(
            Hbar initialBalance, KeyList publicKeys, boolean receiverSigRequired, String customMemo) {
        String memo = getMemo(String.format("%s %s ", "Create Crypto Account", customMemo));
        return new AccountCreateTransaction()
                .setInitialBalance(initialBalance)
                // The only _required_ property here is `key`
                .setKey(publicKeys)
                .setAccountMemo(memo)
                .setReceiverSignatureRequired(receiverSigRequired)
                .setTransactionMemo(memo);
    }

    public ExpandedAccountId createNewAccount(long initialBalance) {
        return createCryptoAccount(Hbar.fromTinybars(initialBalance), false, null, null);
    }

    public ExpandedAccountId createNewECDSAAccount(long initialBalance) {
        PrivateKey newAccountPrivateKey = PrivateKey.generateECDSA();
        AccountId newAccountId = newAccountPrivateKey.getPublicKey().toAccountId(0, 0);
        sendCryptoTransfer(newAccountId, Hbar.fromTinybars(initialBalance));

        MirrorAccountResponse accountInfo = sdkClient.getMirrorNodeClient().getAccountDetailsUsingAlias(newAccountId);
        return new ExpandedAccountId(accountInfo.getAccount(), newAccountPrivateKey.toString());
    }

    public ExpandedAccountId createNewAccount(long initialBalance, AccountNameEnum accountNameEnum) {
        return createCryptoAccount(Hbar.fromTinybars(initialBalance), accountNameEnum.receiverSigRequired, null, null);
    }

    public ExpandedAccountId createCryptoAccount(
            Hbar initialBalance, boolean receiverSigRequired, KeyList keyList, String memo) {
        // 1. Generate a Ed25519 private, public key pair
        PrivateKey privateKey = PrivateKey.generateED25519();
        PublicKey publicKey = privateKey.getPublicKey();

        log.trace("Private key = {}", privateKey);
        log.trace("Public key = {}", publicKey);

        KeyList publicKeyList = KeyList.of(privateKey.getPublicKey());
        if (keyList != null) {
            publicKeyList.addAll(keyList);
        }

        AccountCreateTransaction accountCreateTransaction = getAccountCreateTransaction(
                initialBalance, publicKeyList, receiverSigRequired, memo == null ? "" : memo);

        var response = executeTransactionAndRetrieveReceipt(
                accountCreateTransaction, receiverSigRequired ? KeyList.of(privateKey) : null);
        TransactionReceipt receipt = response.getReceipt();
        AccountId newAccountId = receipt.accountId;

        // verify accountId
        if (receipt.accountId == null) {
            throw new NetworkException(String.format(
                    "Receipt for %s returned no accountId, receipt: %s", response.getTransactionId(), receipt));
        }

        log.info("Created new account {} with {} via {}", newAccountId, initialBalance, response.getTransactionId());
        return new ExpandedAccountId(newAccountId, privateKey);
    }

    public NetworkTransactionResponse approveCryptoAllowance(AccountId spender, Hbar hbarAmount) {
        var transaction = new AccountAllowanceApproveTransaction().approveHbarAllowance(null, spender, hbarAmount);
        var response = executeTransactionAndRetrieveReceipt(transaction);
        log.info("Approved spender {} an allowance of {} via {}", spender, hbarAmount, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse approveNft(NftId nftId, AccountId spender) {
        var ownerAccountId = sdkClient.getExpandedOperatorAccountId().getAccountId();
        var transaction =
                new AccountAllowanceApproveTransaction().approveTokenNftAllowance(nftId, ownerAccountId, spender);

        var response = executeTransactionAndRetrieveReceipt(transaction);
        log.info(
                "Approved spender {} a NFT allowance on {} and serial {} via {}",
                spender,
                nftId.tokenId,
                nftId.serial,
                response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse approveToken(TokenId tokenId, AccountId spender, long amount) {
        var ownerAccountId = sdkClient.getExpandedOperatorAccountId().getAccountId();
        var transaction = new AccountAllowanceApproveTransaction()
                .approveTokenAllowance(tokenId, ownerAccountId, spender, amount);

        var response = executeTransactionAndRetrieveReceipt(transaction);
        log.info(
                "Approved spender {} a token allowance on {} of {} via {}",
                spender,
                tokenId,
                amount,
                response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse approveNftAllSerials(TokenId tokenId, AccountId spender) {
        var ownerAccountId = sdkClient.getExpandedOperatorAccountId().getAccountId();
        var transaction = new AccountAllowanceApproveTransaction()
                .approveTokenNftAllowanceAllSerials(tokenId, ownerAccountId, spender);

        var response = executeTransactionAndRetrieveReceipt(transaction);
        log.info(
                "Approved spender {} an allowance for all serial numbers on {} via {}",
                spender,
                tokenId,
                response.getTransactionId());
        return response;
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
