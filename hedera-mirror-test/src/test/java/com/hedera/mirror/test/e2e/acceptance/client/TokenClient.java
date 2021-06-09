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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

import com.hedera.hashgraph.sdk.AccountBalanceQuery;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.hashgraph.sdk.TokenAssociateTransaction;
import com.hedera.hashgraph.sdk.TokenBurnTransaction;
import com.hedera.hashgraph.sdk.TokenCreateTransaction;
import com.hedera.hashgraph.sdk.TokenDeleteTransaction;
import com.hedera.hashgraph.sdk.TokenDissociateTransaction;
import com.hedera.hashgraph.sdk.TokenFreezeTransaction;
import com.hedera.hashgraph.sdk.TokenGrantKycTransaction;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TokenMintTransaction;
import com.hedera.hashgraph.sdk.TokenRevokeKycTransaction;
import com.hedera.hashgraph.sdk.TokenUnfreezeTransaction;
import com.hedera.hashgraph.sdk.TokenUpdateTransaction;
import com.hedera.hashgraph.sdk.TokenWipeTransaction;
import com.hedera.hashgraph.sdk.TransferTransaction;
import com.hedera.hashgraph.sdk.proto.TokenFreezeStatus;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;

@Log4j2
@Named
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TokenClient extends AbstractNetworkClient {

    public TokenClient(SDKClient sdkClient) {
        super(sdkClient);
        log.debug("Creating Token Client");
    }

    public NetworkTransactionResponse createToken(ExpandedAccountId expandedAccountId, String symbol, int freezeStatus,
                                                  int kycStatus, ExpandedAccountId treasuryAccount,
                                                  int initialSupply) throws ReceiptStatusException,
            PrecheckStatusException, TimeoutException {

        log.debug("Create new token {}", symbol);
        Instant refInstant = Instant.now();
        String memo = String.format("Create token {}_{}", symbol, refInstant);
        PublicKey adminKey = expandedAccountId.getPublicKey();
        TokenCreateTransaction tokenCreateTransaction = new TokenCreateTransaction()
                .setAutoRenewAccountId(expandedAccountId.getAccountId())
                .setAutoRenewPeriod(Duration.ofSeconds(6_999_999L))
                .setDecimals(10)
                .setFreezeDefault(false)
                .setInitialSupply(initialSupply)
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTokenMemo(memo)
                .setTokenName(symbol + "_name")
                .setTokenSymbol(symbol)
                .setTreasuryAccountId(treasuryAccount.getAccountId())
                .setTransactionMemo(memo);

        if (adminKey != null) {
            tokenCreateTransaction
                    .setAdminKey(adminKey)
                    .setSupplyKey(adminKey)
                    .setWipeKey(adminKey);
        }

        if (freezeStatus > 0 && adminKey != null) {
            tokenCreateTransaction
                    .setFreezeDefault(freezeStatus == TokenFreezeStatus.Frozen_VALUE ? true : false)
                    .setFreezeKey(adminKey);
        }

        if (kycStatus > 0 && adminKey != null) {
            tokenCreateTransaction
                    .setKycKey(adminKey);
        }

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenCreateTransaction, KeyList
                        .of(treasuryAccount.getPrivateKey()));
        TokenId tokenId = networkTransactionResponse.getReceipt().tokenId;
        log.debug("Created new token {}", tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse asssociate(ExpandedAccountId accountId, TokenId token) throws ReceiptStatusException, PrecheckStatusException, TimeoutException {

        log.debug("Associate account {} with token {}", accountId.getAccountId(), token);
        Instant refInstant = Instant.now();
        TokenAssociateTransaction tokenAssociateTransaction = new TokenAssociateTransaction()
                .setAccountId(accountId.getAccountId())
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTokenIds((List.of(token)))
                .setTransactionMemo("Associate w token_" + refInstant);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenAssociateTransaction,
                        KeyList.of(accountId.getPrivateKey()));

        log.debug("Associated {} with token {}", accountId, token);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse mint(TokenId tokenId, long amount) throws ReceiptStatusException,
            PrecheckStatusException, TimeoutException {

        log.debug("Mint {} tokens from {}", amount, tokenId);
        Instant refInstant = Instant.now();
        TokenMintTransaction tokenMintTransaction = new TokenMintTransaction()
                .setTokenId(tokenId)
                .setAmount(amount)
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTransactionMemo("Mint token_" + refInstant);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenMintTransaction, null);

        log.debug("Minted {} extra tokens for token {}", amount, tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse freeze(TokenId tokenId, AccountId accountId, PrivateKey freezeKey) throws ReceiptStatusException, PrecheckStatusException, TimeoutException {

        Instant refInstant = Instant.now();
        TokenFreezeTransaction tokenFreezeAccountTransaction = new TokenFreezeTransaction()
                .setAccountId(accountId)
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTokenId(tokenId)
                .setTransactionMemo("Freeze account_" + refInstant);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenFreezeAccountTransaction,
                        KeyList.of(freezeKey));

        log.debug("Freeze account {} with token {}", accountId, tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse unfreeze(TokenId tokenId, AccountId accountId, PrivateKey freezeKey) throws ReceiptStatusException, PrecheckStatusException, TimeoutException {

        Instant refInstant = Instant.now();
        TokenUnfreezeTransaction tokenUnfreezeTransaction = new TokenUnfreezeTransaction()
                .setAccountId(accountId)
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTokenId(tokenId)
                .setTransactionMemo("Unfreeze account_" + refInstant);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenUnfreezeTransaction,
                        KeyList.of(freezeKey));

        log.debug("Unfreeze account {} with token {}", accountId, tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse grantKyc(TokenId tokenId, AccountId accountId, PrivateKey kycKey) throws ReceiptStatusException, PrecheckStatusException, TimeoutException {

        log.debug("Grant account {} with KYC for token {}", accountId, tokenId);
        Instant refInstant = Instant.now();
        TokenGrantKycTransaction tokenGrantKycTransaction = new TokenGrantKycTransaction()
                .setAccountId(accountId)
                .setTokenId(tokenId)
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTransactionMemo("Grant kyc for token_" + refInstant);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenGrantKycTransaction, KeyList.of(kycKey));

        log.debug("Granted Kyc for account {} with token {}", accountId, tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse revokeKyc(TokenId tokenId, AccountId accountId, PrivateKey kycKey) throws ReceiptStatusException, PrecheckStatusException, TimeoutException {

        log.debug("Grant account {} with KYC for token {}", accountId, tokenId);
        Instant refInstant = Instant.now();
        TokenRevokeKycTransaction tokenRevokeKycTransaction = new TokenRevokeKycTransaction()
                .setAccountId(accountId)
                .setTokenId(tokenId)
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTransactionMemo("Revoke kyc for token_" + refInstant);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenRevokeKycTransaction, KeyList.of(kycKey));

        log.debug("Revoked Kyc for account {} with token {}", accountId, tokenId);

        return networkTransactionResponse;
    }

    public TransferTransaction getTokenTransferTransaction(TokenId tokenId, AccountId sender, AccountId recipient,
                                                           long amount) {
        Instant refInstant = Instant.now();
        return new TransferTransaction()
                .addTokenTransfer(tokenId, sender, Math.negateExact(amount))
                .addTokenTransfer(tokenId, recipient, amount)
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTransactionMemo("Transfer token_" + refInstant);
    }

    public NetworkTransactionResponse transferToken(TokenId tokenId, ExpandedAccountId sender, AccountId recipient,
                                                    long amount) throws ReceiptStatusException,
            PrecheckStatusException, TimeoutException {

        log.debug("Transfer {} of token {} from {} to {}", amount, tokenId, sender, recipient);
        TransferTransaction tokenTransferTransaction = getTokenTransferTransaction(tokenId, sender
                .getAccountId(), recipient, amount);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenTransferTransaction, KeyList.of(sender.getPrivateKey()));

        log.debug("Transferred {} tokens of {} from {} to {}", amount, tokenId, sender,
                recipient);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse updateToken(TokenId tokenId, ExpandedAccountId expandedAccountId) throws ReceiptStatusException, PrecheckStatusException, TimeoutException {
        PublicKey publicKey = expandedAccountId.getPublicKey();
        String newSymbol = RandomStringUtils.randomAlphabetic(4).toUpperCase();
        TokenUpdateTransaction tokenUpdateTransaction = new TokenUpdateTransaction()
                .setAdminKey(publicKey)
                .setAutoRenewAccountId(expandedAccountId.getAccountId())
                .setAutoRenewPeriod(Duration.ofSeconds(8_000_001L))
                .setExpirationTime(Instant.now().plus(120, ChronoUnit.DAYS))
                .setTokenName(newSymbol + "_name")
                .setSupplyKey(publicKey)
                .setTokenSymbol(newSymbol)
                .setTokenId(tokenId)
                .setTreasuryAccountId(client.getOperatorAccountId())
                .setWipeKey(publicKey)
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee());

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenUpdateTransaction, null);

        log.debug("Updated token {}.", tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse burn(TokenId tokenId, long amount) throws ReceiptStatusException,
            PrecheckStatusException, TimeoutException {

        log.debug("Burn {} tokens from {}", amount, tokenId);
        Instant refInstant = Instant.now();
        TokenBurnTransaction tokenBurnTransaction = new TokenBurnTransaction()
                .setTokenId(tokenId)
                .setAmount(amount)
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTransactionMemo("Burn token_" + refInstant);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenBurnTransaction, null);

        log.debug("Burned {} extra tokens for token {}", amount, tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse wipe(TokenId tokenId, long amount, ExpandedAccountId expandedAccountId) throws ReceiptStatusException, PrecheckStatusException, TimeoutException {

        log.debug("Wipe {} tokens from {}", amount, tokenId);
        Instant refInstant = Instant.now();
        TokenWipeTransaction tokenWipeAccountTransaction = new TokenWipeTransaction()
                .setAccountId(expandedAccountId.getAccountId())
                .setTokenId(tokenId)
                .setAmount(amount)
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTransactionMemo("Wipe token_" + refInstant);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenWipeAccountTransaction, null);

        log.debug("Wiped {} tokens from account {}", amount, expandedAccountId.getAccountId());

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse disssociate(ExpandedAccountId accountId, TokenId token) throws ReceiptStatusException, PrecheckStatusException, TimeoutException {

        log.debug("Dissociate account {} with token {}", accountId.getAccountId(), token);
        TokenDissociateTransaction tokenDissociateTransaction = new TokenDissociateTransaction()
                .setAccountId(accountId.getAccountId())
                .setTokenIds(List.of(token));

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenDissociateTransaction,
                        KeyList.of(accountId.getPrivateKey()));

        log.debug("Dissociated {} with token {}", accountId, token);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse delete(ExpandedAccountId accountId, TokenId token) throws ReceiptStatusException, PrecheckStatusException, TimeoutException {

        log.debug("Delete token {}", token);
        Instant refInstant = Instant.now();
        TokenDeleteTransaction tokenDissociateTransaction = new TokenDeleteTransaction()
                .setTokenId(token)
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTransactionMemo("Delete token_" + refInstant);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenDissociateTransaction,
                        KeyList.of(accountId.getPrivateKey()));

        log.debug("Deleted token {}", accountId, token);

        return networkTransactionResponse;
    }

    public long getTokenBalance(AccountId accountId, TokenId tokenId) throws TimeoutException, PrecheckStatusException {
        long balance = new AccountBalanceQuery()
                .setAccountId(accountId)
                .execute(client)
                .token.get(tokenId);

        log.debug("{}'s token balance is {} {} tokens", accountId, balance, tokenId);

        return balance;
    }
}
