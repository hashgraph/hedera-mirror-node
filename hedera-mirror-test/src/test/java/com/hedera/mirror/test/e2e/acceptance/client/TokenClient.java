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
import javax.inject.Named;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.retry.support.RetryTemplate;

import com.hedera.hashgraph.sdk.AccountBalanceQuery;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.CustomFee;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TokenAssociateTransaction;
import com.hedera.hashgraph.sdk.TokenBurnTransaction;
import com.hedera.hashgraph.sdk.TokenCreateTransaction;
import com.hedera.hashgraph.sdk.TokenDeleteTransaction;
import com.hedera.hashgraph.sdk.TokenDissociateTransaction;
import com.hedera.hashgraph.sdk.TokenFeeScheduleUpdateTransaction;
import com.hedera.hashgraph.sdk.TokenFreezeTransaction;
import com.hedera.hashgraph.sdk.TokenGrantKycTransaction;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TokenMintTransaction;
import com.hedera.hashgraph.sdk.TokenRevokeKycTransaction;
import com.hedera.hashgraph.sdk.TokenSupplyType;
import com.hedera.hashgraph.sdk.TokenType;
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

    public TokenClient(SDKClient sdkClient, RetryTemplate retryTemplate) {
        super(sdkClient, retryTemplate);
        log.debug("Creating Token Client");
    }

    public NetworkTransactionResponse createToken(ExpandedAccountId expandedAccountId, String symbol, int freezeStatus,
                                                  int kycStatus, ExpandedAccountId treasuryAccount,
                                                  int initialSupply, TokenType tokenType,
                                                  List<CustomFee> customFees) {
        return createToken(expandedAccountId, symbol, freezeStatus, kycStatus, treasuryAccount, initialSupply,
                tokenType, TokenSupplyType.INFINITE, 0, customFees);
    }

    public NetworkTransactionResponse createToken(ExpandedAccountId expandedAccountId, String symbol, int freezeStatus,
                                                  int kycStatus, ExpandedAccountId treasuryAccount,
                                                  int initialSupply, TokenType tokenType,
                                                  TokenSupplyType tokenSupplyType, long maxSupply,
                                                  List<CustomFee> customFees) {
        log.debug("Create new token {}", symbol);
        Instant refInstant = Instant.now();
        String memo = String.format("Create token %s_%s", symbol, refInstant);
        PublicKey adminKey = expandedAccountId.getPublicKey();
        TokenCreateTransaction tokenCreateTransaction = new TokenCreateTransaction()
                .setAutoRenewAccountId(expandedAccountId.getAccountId())
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTokenMemo(memo)
                .setTokenName(symbol + "_name")
                .setSupplyType(tokenSupplyType)
                .setTokenSymbol(symbol)
                .setTokenType(tokenType)
                .setTreasuryAccountId(treasuryAccount.getAccountId())
                .setTransactionMemo(memo);

        if (tokenType == TokenType.FUNGIBLE_COMMON) {
            tokenCreateTransaction
                    .setDecimals(10)
                    .setInitialSupply(initialSupply);
        }

        if (tokenSupplyType == TokenSupplyType.FINITE) {
            tokenCreateTransaction.setMaxSupply(maxSupply);
        }

        if (adminKey != null) {
            tokenCreateTransaction
                    .setAdminKey(adminKey)
                    .setSupplyKey(adminKey)
                    .setWipeKey(adminKey);
        }

        if (freezeStatus > 0 && adminKey != null) {
            tokenCreateTransaction
                    .setFreezeDefault(freezeStatus == TokenFreezeStatus.Frozen_VALUE)
                    .setFreezeKey(adminKey);
        }

        if (kycStatus > 0 && adminKey != null) {
            tokenCreateTransaction.setKycKey(adminKey);
        }

        if (customFees != null && adminKey != null) {
            tokenCreateTransaction
                    .setCustomFees(customFees)
                    .setFeeScheduleKey(adminKey);
        }

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenCreateTransaction,
                        KeyList.of(treasuryAccount.getPrivateKey()));
        TokenId tokenId = networkTransactionResponse.getReceipt().tokenId;
        log.debug("Created new token {}", tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse associate(ExpandedAccountId accountId, TokenId token) {
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

    public NetworkTransactionResponse mint(TokenId tokenId, long amount) {
        return mint(tokenId, amount, null);
    }

    public NetworkTransactionResponse mint(TokenId tokenId, byte[] metadata) {
        return mint(tokenId, 0, metadata);
    }

    public NetworkTransactionResponse mint(TokenId tokenId, long amount, byte[] metadata) {

        log.debug("Mint {} tokens from {}", amount, tokenId);
        Instant refInstant = Instant.now();
        TokenMintTransaction tokenMintTransaction = new TokenMintTransaction()
                .setTokenId(tokenId)
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTransactionMemo("Mint token_" + refInstant);

        if (metadata != null) {
            tokenMintTransaction.addMetadata(metadata);
        } else {
            tokenMintTransaction.setAmount(amount);
        }

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenMintTransaction);

        log.debug("Minted {} extra tokens for token {}", amount, tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse freeze(TokenId tokenId, AccountId accountId) {

        Instant refInstant = Instant.now();
        TokenFreezeTransaction tokenFreezeAccountTransaction = new TokenFreezeTransaction()
                .setAccountId(accountId)
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTokenId(tokenId)
                .setTransactionMemo("Freeze account_" + refInstant);

        NetworkTransactionResponse response = executeTransactionAndRetrieveReceipt(tokenFreezeAccountTransaction);

        log.debug("Freeze account {} with token {}", accountId, tokenId);

        return response;
    }

    public NetworkTransactionResponse unfreeze(TokenId tokenId, AccountId accountId) {

        Instant refInstant = Instant.now();
        TokenUnfreezeTransaction tokenUnfreezeTransaction = new TokenUnfreezeTransaction()
                .setAccountId(accountId)
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTokenId(tokenId)
                .setTransactionMemo("Unfreeze account_" + refInstant);

        NetworkTransactionResponse response = executeTransactionAndRetrieveReceipt(tokenUnfreezeTransaction);

        log.debug("Unfreeze account {} with token {}", accountId, tokenId);

        return response;
    }

    public NetworkTransactionResponse grantKyc(TokenId tokenId, AccountId accountId) {

        log.debug("Grant account {} with KYC for token {}", accountId, tokenId);
        Instant refInstant = Instant.now();
        TokenGrantKycTransaction tokenGrantKycTransaction = new TokenGrantKycTransaction()
                .setAccountId(accountId)
                .setTokenId(tokenId)
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTransactionMemo("Grant kyc for token_" + refInstant);

        NetworkTransactionResponse networkTransactionResponse = executeTransactionAndRetrieveReceipt(
                tokenGrantKycTransaction);

        log.debug("Granted Kyc for account {} with token {}", accountId, tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse revokeKyc(TokenId tokenId, AccountId accountId) {

        log.debug("Grant account {} with KYC for token {}", accountId, tokenId);
        Instant refInstant = Instant.now();
        TokenRevokeKycTransaction tokenRevokeKycTransaction = new TokenRevokeKycTransaction()
                .setAccountId(accountId)
                .setTokenId(tokenId)
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTransactionMemo("Revoke kyc for token_" + refInstant);

        NetworkTransactionResponse networkTransactionResponse = executeTransactionAndRetrieveReceipt(
                tokenRevokeKycTransaction);

        log.debug("Revoked Kyc for account {} with token {}", accountId, tokenId);

        return networkTransactionResponse;
    }

    public TransferTransaction getTokenTransferTransaction(TokenId tokenId, AccountId sender, AccountId recipient,
                                                           long amount, List<Long> serialNumbers) {
        Instant refInstant = Instant.now();
        TransferTransaction transaction = new TransferTransaction()
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTransactionMemo("Transfer token_" + refInstant);
        if (serialNumbers != null) {
            for (Long serialNumber : serialNumbers) {
                transaction.addNftTransfer(new NftId(tokenId, serialNumber), sender, recipient);
            }
        } else {
            transaction
                    .addTokenTransfer(tokenId, sender, Math.negateExact(amount))
                    .addTokenTransfer(tokenId, recipient, amount);
        }
        return transaction;
    }

    public NetworkTransactionResponse transferToken(TokenId tokenId, ExpandedAccountId sender, AccountId recipient,
                                                    long amount) {
        return transferToken(tokenId, sender, recipient, amount, null);
    }

    public NetworkTransactionResponse transferToken(TokenId tokenId, ExpandedAccountId sender, AccountId recipient,
                                                    List<Long> serialNumbers) {

        return transferToken(tokenId, sender, recipient, 0, serialNumbers);
    }

    public NetworkTransactionResponse transferToken(TokenId tokenId, ExpandedAccountId sender, AccountId recipient,
                                                    long amount, List<Long> serialNumbers) {

        TransferTransaction tokenTransferTransaction = getTokenTransferTransaction(tokenId, sender.getAccountId(),
                recipient, amount, serialNumbers);

        return executeTransactionAndRetrieveReceipt(tokenTransferTransaction, sender);
    }

    public NetworkTransactionResponse updateToken(TokenId tokenId, ExpandedAccountId expandedAccountId) {
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
                executeTransactionAndRetrieveReceipt(tokenUpdateTransaction);

        log.debug("Updated token {}.", tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse updateTokenTreasury(TokenId tokenId, ExpandedAccountId newTreasuryId) {
        AccountId treasuryAccountId = newTreasuryId.getAccountId();
        TokenUpdateTransaction tokenUpdateTransaction = new TokenUpdateTransaction()
                .setTokenId(tokenId)
                .setTreasuryAccountId(treasuryAccountId);

        KeyList keyList = KeyList.of(newTreasuryId.getPrivateKey());
        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenUpdateTransaction, keyList);

        log.debug("Updated token {} treasury account {}.", tokenId, treasuryAccountId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse burn(TokenId tokenId, long amount) {
        return burn(tokenId, amount, 0);
    }

    public NetworkTransactionResponse burn(TokenId tokenId, long amount, long serialNumber) {

        log.debug("Burn {} tokens from {}", amount, tokenId);
        Instant refInstant = Instant.now();
        TokenBurnTransaction tokenBurnTransaction = new TokenBurnTransaction()
                .setTokenId(tokenId)
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTransactionMemo("Burn token_" + refInstant);

        if (serialNumber != 0) {
            tokenBurnTransaction.addSerial(serialNumber);
        } else {
            tokenBurnTransaction.setAmount(amount);
        }

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenBurnTransaction);

        log.debug("Burned {} extra tokens for token {}", amount, tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse wipe(TokenId tokenId, long amount, ExpandedAccountId expandedAccountId) {
        return wipe(tokenId, amount, expandedAccountId, 0);
    }

    public NetworkTransactionResponse wipe(TokenId tokenId, long amount, ExpandedAccountId expandedAccountId,
                                           long serialNumber) {

        log.debug("Wipe {} tokens from {}", amount, tokenId);
        Instant refInstant = Instant.now();
        TokenWipeTransaction tokenWipeAccountTransaction = new TokenWipeTransaction()
                .setAccountId(expandedAccountId.getAccountId())
                .setTokenId(tokenId)
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTransactionMemo("Wipe token_" + refInstant);

        if (serialNumber != 0) {
            tokenWipeAccountTransaction.addSerial(serialNumber);
        } else {
            tokenWipeAccountTransaction.setAmount(amount);
        }

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenWipeAccountTransaction);

        log.debug("Wiped {} tokens from account {}", amount, expandedAccountId.getAccountId());

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse dissociate(ExpandedAccountId accountId, TokenId token) {

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

    public NetworkTransactionResponse delete(ExpandedAccountId accountId, TokenId token) {

        log.debug("Delete token {}", token);
        Instant refInstant = Instant.now();
        TokenDeleteTransaction tokenDissociateTransaction = new TokenDeleteTransaction()
                .setTokenId(token)
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTransactionMemo("Delete token_" + refInstant);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenDissociateTransaction,
                        KeyList.of(accountId.getPrivateKey()));

        log.debug("Deleted token {}", token);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse updateTokenFeeSchedule(TokenId tokenId, ExpandedAccountId expandedAccountId,
                                                             List<CustomFee> customFees) {
        TokenFeeScheduleUpdateTransaction transaction = new TokenFeeScheduleUpdateTransaction()
                .setCustomFees(customFees)
                .setMaxTransactionFee(sdkClient.getMaxTransactionFee())
                .setTokenId(tokenId);

        NetworkTransactionResponse response = executeTransactionAndRetrieveReceipt(transaction,
                KeyList.of(expandedAccountId.getPrivateKey()));

        log.debug("Updated custom fees schedule for token {}", tokenId);
        return response;
    }

    @SneakyThrows
    public long getTokenBalance(AccountId accountId, TokenId tokenId) {
        long balance = new AccountBalanceQuery()
                .setAccountId(accountId)
                .execute(client)
                .tokens.get(tokenId);

        log.debug("{}'s token balance is {} {} tokens", accountId, balance, tokenId);

        return balance;
    }
}
