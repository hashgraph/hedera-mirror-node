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
import com.hedera.hashgraph.sdk.AccountDeleteTransaction;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.AccountInfo;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.TransferTransaction;
import com.hedera.hashgraph.sdk.proto.Key;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.retry.support.RetryTemplate;

@CustomLog
@Named
public class AccountClient extends AbstractNetworkClient {

    private static final long DEFAULT_INITIAL_BALANCE = 50_000_000L; // 0.5 ℏ
    private static final long SMALL_INITIAL_BALANCE = 500_000L; // 0.005 ℏ

    private final Map<AccountNameEnum, ExpandedAccountId> accountMap = new ConcurrentHashMap<>();
    private final Collection<ExpandedAccountId> accountIds = new CopyOnWriteArrayList<>();
    private final long initialBalance;
    private ExpandedAccountId tokenTreasuryAccount = null;

    public AccountClient(SDKClient sdkClient, RetryTemplate retryTemplate) {
        super(sdkClient, retryTemplate);
        initialBalance = getBalance();
        log.info("Operator account {} initial balance is {}", sdkClient.getExpandedOperatorAccountId(), initialBalance);
    }

    @Override
    public void clean() {
        log.info("Deleting {} accounts", accountIds.size());
        accountIds.forEach(this::delete);
        accountIds.clear();
        accountMap.clear();

        var cost = initialBalance - getBalance();
        log.warn("Tests cost {} to run", Hbar.fromTinybars(cost));
    }

    public synchronized ExpandedAccountId getTokenTreasuryAccount() {
        if (tokenTreasuryAccount == null) {
            tokenTreasuryAccount = createNewAccount(DEFAULT_INITIAL_BALANCE);
            log.debug("Treasury Account: {} will be used for current test session", tokenTreasuryAccount);
        }

        return tokenTreasuryAccount;
    }

    public NetworkTransactionResponse delete(ExpandedAccountId accountId) {
        var accountDeleteTransaction = new AccountDeleteTransaction()
                .setAccountId(accountId.getAccountId())
                .setTransferAccountId(client.getOperatorAccountId())
                .freezeWith(client)
                .sign(accountId.getPrivateKey());
        var response = executeTransactionAndRetrieveReceipt(accountDeleteTransaction);
        log.info("Deleted account {} via {}", accountId, response.getTransactionId());
        return response;
    }

    public ExpandedAccountId getAccount(AccountNameEnum accountNameEnum) {
        // retrieve account, setting if it doesn't exist
        ExpandedAccountId accountId = accountMap.computeIfAbsent(accountNameEnum, x -> {
            try {
                return createNewAccount(DEFAULT_INITIAL_BALANCE, accountNameEnum);
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
        // By default, use ALICE's key type if not specified->ED25519
        Key.KeyCase keyType = AccountNameEnum.ALICE.keyType;
        return createCryptoAccount(Hbar.fromTinybars(initialBalance), false, null, null, keyType);
    }

    public ExpandedAccountId createNewAccount(long initialBalance, AccountNameEnum accountNameEnum) {
        // Get the keyType from the enum
        Key.KeyCase keyType = accountNameEnum.keyType;
        return createCryptoAccount(
                Hbar.fromTinybars(initialBalance), accountNameEnum.receiverSigRequired, null, null, keyType);
    }

    private ExpandedAccountId createCryptoAccount(
            Hbar initialBalance, boolean receiverSigRequired, KeyList keyList, String memo, Key.KeyCase keyType) {
        // Depending on keyType, generate an Ed25519 or ECDSA private, public key pair
        PrivateKey privateKey;
        PublicKey publicKey;
        if (keyType == Key.KeyCase.ED25519) {
            privateKey = PrivateKey.generateED25519();
            publicKey = privateKey.getPublicKey();
        } else if (keyType == Key.KeyCase.ECDSA_SECP256K1) {
            privateKey = PrivateKey.generateECDSA();
            publicKey = privateKey.getPublicKey();
        } else {
            throw new IllegalArgumentException("Unsupported key type: " + keyType);
        }

        log.trace("Private key = {}", privateKey);
        log.trace("Public key = {}", publicKey);

        KeyList publicKeyList = KeyList.of(privateKey.getPublicKey());
        if (keyList != null) {
            publicKeyList.addAll(keyList);
        }

        AccountId newAccountId;
        NetworkTransactionResponse response;
        if (keyType == Key.KeyCase.ED25519) {
            Transaction<?> transaction = getAccountCreateTransaction(
                    initialBalance, publicKeyList, receiverSigRequired, memo == null ? "" : memo);
            response = executeTransactionAndRetrieveReceipt(
                    transaction, receiverSigRequired ? KeyList.of(privateKey) : null);
            TransactionReceipt receipt = response.getReceipt();
            newAccountId = receipt.accountId;
            if (receipt.accountId == null) {
                throw new NetworkException(String.format(
                        "Receipt for %s returned no accountId, receipt: %s", response.getTransactionId(), receipt));
            }
        } else {
            AccountId accountIdFromEvmAddress = AccountId.fromEvmAddress(privateKey.getPublicKey().toEvmAddress());
            response = sendCryptoTransfer(accountIdFromEvmAddress, initialBalance);
            AccountInfo accountInfo = getAccountInfo(accountIdFromEvmAddress);
            newAccountId = new AccountId(accountInfo.accountId.num);
        }

        log.info("Created new account {} with {} via {}", newAccountId, initialBalance, response.getTransactionId());
        ExpandedAccountId accountId = new ExpandedAccountId(newAccountId, privateKey);
        accountIds.add(accountId);
        return accountId;
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
        ALICE(false, Key.KeyCase.ED25519),
        BOB(false, Key.KeyCase.ECDSA_SECP256K1),
        CAROL(true, Key.KeyCase.ED25519),
        DAVE(true, Key.KeyCase.ED25519);

        private final boolean receiverSigRequired;
        private final Key.KeyCase keyType;

        @Override
        public String toString() {
            return String.format("%s, receiverSigRequired: %s", name(), receiverSigRequired);
        }
    }
}
