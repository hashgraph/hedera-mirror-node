package com.hedera.mirror.test.e2e.acceptance.client;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import java.util.List;
import javax.inject.Named;
import org.apache.commons.lang3.RandomStringUtils;
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
import com.hedera.hashgraph.sdk.TokenPauseTransaction;
import com.hedera.hashgraph.sdk.TokenRevokeKycTransaction;
import com.hedera.hashgraph.sdk.TokenSupplyType;
import com.hedera.hashgraph.sdk.TokenType;
import com.hedera.hashgraph.sdk.TokenUnfreezeTransaction;
import com.hedera.hashgraph.sdk.TokenUnpauseTransaction;
import com.hedera.hashgraph.sdk.TokenUpdateTransaction;
import com.hedera.hashgraph.sdk.TokenWipeTransaction;
import com.hedera.hashgraph.sdk.TransferTransaction;
import com.hedera.hashgraph.sdk.proto.TokenFreezeStatus;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;

@Named
public class TokenClient extends AbstractNetworkClient {

    public TokenClient(SDKClient sdkClient, RetryTemplate retryTemplate) {
        super(sdkClient, retryTemplate);
    }

    public NetworkTransactionResponse createToken(ExpandedAccountId expandedAccountId, String symbol, int freezeStatus,
                                                  int kycStatus, ExpandedAccountId treasuryAccount,
                                                  int initialSupply, TokenSupplyType tokenSupplyType, long maxSupply,
                                                  TokenType tokenType,
                                                  List<CustomFee> customFees) {

        if (tokenType == TokenType.FUNGIBLE_COMMON) {
            return createFungibleToken(expandedAccountId, symbol, freezeStatus, kycStatus, treasuryAccount,
                    initialSupply, tokenSupplyType, maxSupply, customFees);
        } else {
            return createNonFungibleToken(expandedAccountId, symbol, freezeStatus, kycStatus, treasuryAccount,
                    tokenSupplyType, maxSupply, customFees);
        }
    }

    private TokenCreateTransaction getTokenCreateTransaction(ExpandedAccountId expandedAccountId, String symbol,
                                                             int freezeStatus, int kycStatus,
                                                             ExpandedAccountId treasuryAccount, TokenType tokenType,
                                                             TokenSupplyType tokenSupplyType, long maxSupply,
                                                             List<CustomFee> customFees) {
        String memo = getMemo("Create token");
        PublicKey adminKey = expandedAccountId.getPublicKey();
        TokenCreateTransaction transaction = new TokenCreateTransaction()
                .setAutoRenewAccountId(expandedAccountId.getAccountId())
                .setAutoRenewPeriod(Duration.ofSeconds(6_999_999L))
                .setTokenMemo(memo)
                .setTokenName(symbol + "_name")
                .setSupplyType(tokenSupplyType)
                .setTokenSymbol(symbol)
                .setTokenType(tokenType)
                .setTreasuryAccountId(treasuryAccount.getAccountId())
                .setTransactionMemo(memo);

        if (tokenSupplyType == TokenSupplyType.FINITE) {
            transaction.setMaxSupply(maxSupply);
        }

        if (adminKey != null) {
            transaction
                    .setAdminKey(adminKey)
                    .setPauseKey(adminKey)
                    .setSupplyKey(adminKey)
                    .setWipeKey(adminKey);
        }

        if (freezeStatus > 0 && adminKey != null) {
            transaction
                    .setFreezeDefault(freezeStatus == TokenFreezeStatus.Frozen_VALUE)
                    .setFreezeKey(adminKey);
        }

        if (kycStatus > 0 && adminKey != null) {
            transaction.setKycKey(adminKey);
        }

        if (customFees != null && adminKey != null) {
            transaction
                    .setCustomFees(customFees)
                    .setFeeScheduleKey(adminKey);
        }
        return transaction;
    }

    public NetworkTransactionResponse createFungibleToken(ExpandedAccountId expandedAccountId, String symbol,
                                                          int freezeStatus,
                                                          int kycStatus, ExpandedAccountId treasuryAccount,
                                                          int initialSupply, TokenSupplyType tokenSupplyType,
                                                          long maxSupply, List<CustomFee> customFees) {
        log.debug("Create new fungible token {}", symbol);
        TokenCreateTransaction tokenCreateTransaction = getTokenCreateTransaction(expandedAccountId, symbol,
                freezeStatus, kycStatus, treasuryAccount, TokenType.FUNGIBLE_COMMON, tokenSupplyType, maxSupply,
                customFees)
                .setDecimals(10)
                .setInitialSupply(initialSupply);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenCreateTransaction,
                        KeyList.of(treasuryAccount.getPrivateKey()));
        TokenId tokenId = networkTransactionResponse.getReceipt().tokenId;
        log.debug("Created new fungible token {}", tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse createNonFungibleToken(ExpandedAccountId expandedAccountId, String symbol,
                                                             int freezeStatus,
                                                             int kycStatus, ExpandedAccountId treasuryAccount,
                                                             TokenSupplyType tokenSupplyType, long maxSupply,
                                                             List<CustomFee> customFees) {
        log.debug("Create new non-fungible token {}", symbol);
        TokenCreateTransaction tokenCreateTransaction = getTokenCreateTransaction(expandedAccountId, symbol,
                freezeStatus, kycStatus, treasuryAccount, TokenType.NON_FUNGIBLE_UNIQUE, tokenSupplyType, maxSupply,
                customFees);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenCreateTransaction,
                        KeyList.of(treasuryAccount.getPrivateKey()));
        TokenId tokenId = networkTransactionResponse.getReceipt().tokenId;
        log.debug("Created new non-fungible token {}", tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse associate(ExpandedAccountId accountId, TokenId token) {
        log.debug("Associate account {} with token {}", accountId.getAccountId(), token);
        TokenAssociateTransaction tokenAssociateTransaction = new TokenAssociateTransaction()
                .setAccountId(accountId.getAccountId())
                .setTokenIds((List.of(token)))
                .setTransactionMemo(getMemo("Associate w token"));

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
        TokenMintTransaction tokenMintTransaction = new TokenMintTransaction()
                .setTokenId(tokenId)
                .setTransactionMemo(getMemo("Mint token"));

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
        TokenFreezeTransaction tokenFreezeAccountTransaction = new TokenFreezeTransaction()
                .setAccountId(accountId)
                .setTokenId(tokenId)
                .setTransactionMemo(getMemo("Freeze token account"));

        NetworkTransactionResponse response = executeTransactionAndRetrieveReceipt(tokenFreezeAccountTransaction);

        log.debug("Freeze account {} with token {}", accountId, tokenId);

        return response;
    }

    public NetworkTransactionResponse unfreeze(TokenId tokenId, AccountId accountId) {
        TokenUnfreezeTransaction tokenUnfreezeTransaction = new TokenUnfreezeTransaction()
                .setAccountId(accountId)
                .setTokenId(tokenId)
                .setTransactionMemo(getMemo("Unfreeze token account"));

        NetworkTransactionResponse response = executeTransactionAndRetrieveReceipt(tokenUnfreezeTransaction);

        log.debug("Unfreeze account {} with token {}", accountId, tokenId);

        return response;
    }

    public NetworkTransactionResponse grantKyc(TokenId tokenId, AccountId accountId) {
        log.debug("Grant account {} with KYC for token {}", accountId, tokenId);
        TokenGrantKycTransaction tokenGrantKycTransaction = new TokenGrantKycTransaction()
                .setAccountId(accountId)
                .setTokenId(tokenId)
                .setTransactionMemo(getMemo("Grant kyc for token"));

        NetworkTransactionResponse networkTransactionResponse = executeTransactionAndRetrieveReceipt(
                tokenGrantKycTransaction);

        log.debug("Granted Kyc for account {} with token {}", accountId, tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse revokeKyc(TokenId tokenId, AccountId accountId) {
        log.debug("Grant account {} with KYC for token {}", accountId, tokenId);
        TokenRevokeKycTransaction tokenRevokeKycTransaction = new TokenRevokeKycTransaction()
                .setAccountId(accountId)
                .setTokenId(tokenId)
                .setTransactionMemo(getMemo("Revoke kyc for token"));

        NetworkTransactionResponse networkTransactionResponse = executeTransactionAndRetrieveReceipt(
                tokenRevokeKycTransaction);

        log.debug("Revoked Kyc for account {} with token {}", accountId, tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse pause(TokenId tokenId) {
        log.debug("Pausing token {}", tokenId);
        TokenPauseTransaction tokenPauseTransaction = new TokenPauseTransaction()
                .setTokenId(tokenId)
                .setTransactionMemo(getMemo("Pause token"));

        NetworkTransactionResponse networkTransactionResponse = executeTransactionAndRetrieveReceipt(
                tokenPauseTransaction);

        log.debug("Paused token {}", tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse unpause(TokenId tokenId) {
        log.debug("Unpausing token {}", tokenId);
        TokenUnpauseTransaction tokenUnpauseTransaction = new TokenUnpauseTransaction()
                .setTokenId(tokenId)
                .setTransactionMemo(getMemo("Unpause token"));

        NetworkTransactionResponse networkTransactionResponse = executeTransactionAndRetrieveReceipt(
                tokenUnpauseTransaction);

        log.debug("Unpaused token {}", tokenId);

        return networkTransactionResponse;
    }

    public TransferTransaction getFungibleTokenTransferTransaction(TokenId tokenId, AccountId sender,
                                                                   AccountId recipient, long amount) {
        return getTransferTransaction()
                .addTokenTransfer(tokenId, sender, Math.negateExact(amount))
                .addTokenTransfer(tokenId, recipient, amount);
    }

    public TransferTransaction getNonFungibleTokenTransferTransaction(TokenId tokenId, AccountId sender,
                                                                      AccountId recipient, List<Long> serialNumbers) {
        TransferTransaction transaction = getTransferTransaction();
        for (Long serialNumber : serialNumbers) {
            transaction.addNftTransfer(new NftId(tokenId, serialNumber), sender, recipient);
        }
        return transaction;
    }

    private TransferTransaction getTransferTransaction() {
        return new TransferTransaction()
                .setTransactionMemo(getMemo("Transfer token"));
    }

    public NetworkTransactionResponse transferFungibleToken(TokenId tokenId, ExpandedAccountId sender,
                                                            AccountId recipient, long amount) {
        TransferTransaction transaction = getFungibleTokenTransferTransaction(tokenId, sender
                .getAccountId(), recipient, amount);
        return executeTransactionAndRetrieveReceipt(transaction, sender);
    }

    public NetworkTransactionResponse transferNonFungibleToken(TokenId tokenId, ExpandedAccountId sender,
                                                               AccountId recipient,
                                                               List<Long> serialNumbers) {

        TransferTransaction transaction = getNonFungibleTokenTransferTransaction(tokenId, sender
                .getAccountId(), recipient, serialNumbers);
        return executeTransactionAndRetrieveReceipt(transaction, sender);
    }

    public NetworkTransactionResponse updateToken(TokenId tokenId, ExpandedAccountId expandedAccountId) {
        PublicKey publicKey = expandedAccountId.getPublicKey();
        String newSymbol = RandomStringUtils.randomAlphabetic(4).toUpperCase();
        String memo = getMemo("Update token");
        TokenUpdateTransaction tokenUpdateTransaction = new TokenUpdateTransaction()
                .setAdminKey(publicKey)
                .setAutoRenewAccountId(expandedAccountId.getAccountId())
                .setAutoRenewPeriod(Duration.ofSeconds(8_000_001L))
                .setTokenName(newSymbol + "_name")
                .setSupplyKey(publicKey)
                .setTokenMemo(memo)
                .setTokenSymbol(newSymbol)
                .setTokenId(tokenId)
                .setTransactionMemo(memo)
                .setTreasuryAccountId(client.getOperatorAccountId())
                .setWipeKey(publicKey);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenUpdateTransaction);

        log.debug("Updated token {}.", tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse updateTokenTreasury(TokenId tokenId, ExpandedAccountId newTreasuryId) {
        AccountId treasuryAccountId = newTreasuryId.getAccountId();
        String memo = getMemo("Update token");
        TokenUpdateTransaction tokenUpdateTransaction = new TokenUpdateTransaction()
                .setTokenId(tokenId)
                .setTokenMemo(memo)
                .setTreasuryAccountId(treasuryAccountId)
                .setTransactionMemo(memo);

        KeyList keyList = KeyList.of(newTreasuryId.getPrivateKey());
        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenUpdateTransaction, keyList);

        log.debug("Updated token {} treasury account {}.", tokenId, treasuryAccountId);

        return networkTransactionResponse;
    }

    private TokenBurnTransaction getTokenBurnTransaction(TokenId tokenId) {
        return new TokenBurnTransaction()
                .setTokenId(tokenId)
                .setTransactionMemo(getMemo("Burn token"));
    }

    public NetworkTransactionResponse burnFungible(TokenId tokenId, long amount) {
        log.debug("Burn {} tokens from {}", amount, tokenId);
        TokenBurnTransaction tokenBurnTransaction = getTokenBurnTransaction(tokenId)
                .setAmount(amount)
                .setTransactionMemo(getMemo("Token burn"));
        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenBurnTransaction);

        log.debug("Burned {} extra tokens for token {}", amount, tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse burnNonFungible(TokenId tokenId, long serialNumber) {

        log.debug("Burn serial number {} from token {}", serialNumber, tokenId);
        TokenBurnTransaction tokenBurnTransaction = getTokenBurnTransaction(tokenId)
                .addSerial(serialNumber)
                .setTransactionMemo(getMemo("Token burn"));

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenBurnTransaction);

        log.debug("Burned serial number {} from token {}", serialNumber, tokenId);

        return networkTransactionResponse;
    }

    private TokenWipeTransaction getTokenWipeTransaction(TokenId tokenId, ExpandedAccountId expandedAccountId) {
        return new TokenWipeTransaction()
                .setAccountId(expandedAccountId.getAccountId())
                .setTokenId(tokenId)
                .setTransactionMemo(getMemo("Wipe token"));
    }

    public NetworkTransactionResponse wipeFungible(TokenId tokenId, long amount, ExpandedAccountId expandedAccountId) {
        log.debug("Wipe {} tokens from {}", amount, tokenId);
        TokenWipeTransaction transaction = getTokenWipeTransaction(tokenId, expandedAccountId)
                .setAmount(amount);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(transaction);

        log.debug("Wiped {} tokens from account {}", amount, expandedAccountId.getAccountId());

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse wipeNonFungible(TokenId tokenId, long serialNumber,
                                                      ExpandedAccountId expandedAccountId) {

        log.debug("Wipe serial number {} from token {}, account id {}", serialNumber, tokenId, expandedAccountId
                .getAccountId());

        TokenWipeTransaction transaction = getTokenWipeTransaction(tokenId, expandedAccountId)
                .addSerial(serialNumber);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(transaction);

        log.debug("Wiped serial number {} from token {}, account id {}", serialNumber, tokenId, expandedAccountId
                .getAccountId());

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse dissociate(ExpandedAccountId accountId, TokenId token) {

        log.debug("Dissociate account {} with token {}", accountId.getAccountId(), token);
        TokenDissociateTransaction tokenDissociateTransaction = new TokenDissociateTransaction()
                .setAccountId(accountId.getAccountId())
                .setTokenIds(List.of(token))
                .setTransactionMemo(getMemo("Dissociate token"));

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenDissociateTransaction,
                        KeyList.of(accountId.getPrivateKey()));

        log.debug("Dissociated {} with token {}", accountId, token);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse delete(ExpandedAccountId accountId, TokenId token) {

        log.debug("Delete token {}", token);
        TokenDeleteTransaction tokenDissociateTransaction = new TokenDeleteTransaction()
                .setTokenId(token)
                .setTransactionMemo(getMemo("Delete token"));

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
                .setTokenId(tokenId)
                .setTransactionMemo(getMemo("Update token fee schedule"));

        NetworkTransactionResponse response = executeTransactionAndRetrieveReceipt(transaction,
                KeyList.of(expandedAccountId.getPrivateKey()));

        log.debug("Updated custom fees schedule for token {}", tokenId);
        return response;
    }

    @SuppressWarnings("deprecation")
    public long getTokenBalance(AccountId accountId, TokenId tokenId) {
        // AccountBalanceQuery is free
        var query = new AccountBalanceQuery().setAccountId(accountId);
        var accountBalance = executeQuery(() -> query);
        long balance = accountBalance.tokens.get(tokenId);
        log.debug("{}'s token balance is {} {} tokens", accountId, balance, tokenId);
        return balance;
    }
}
