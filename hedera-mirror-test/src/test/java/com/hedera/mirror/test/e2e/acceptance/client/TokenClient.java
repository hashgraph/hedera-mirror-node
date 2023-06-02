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
import jakarta.inject.Named;
import java.time.Duration;
import java.util.List;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.retry.support.RetryTemplate;

@Named
public class TokenClient extends AbstractNetworkClient {

    public TokenClient(SDKClient sdkClient, RetryTemplate retryTemplate) {
        super(sdkClient, retryTemplate);
    }

    public NetworkTransactionResponse createToken(
            ExpandedAccountId expandedAccountId,
            String symbol,
            int freezeStatus,
            int kycStatus,
            ExpandedAccountId treasuryAccount,
            int initialSupply,
            TokenSupplyType tokenSupplyType,
            long maxSupply,
            TokenType tokenType,
            List<CustomFee> customFees) {

        if (tokenType == TokenType.FUNGIBLE_COMMON) {
            return createFungibleToken(
                    expandedAccountId,
                    symbol,
                    freezeStatus,
                    kycStatus,
                    treasuryAccount,
                    initialSupply,
                    tokenSupplyType,
                    maxSupply,
                    customFees);
        } else {
            return createNonFungibleToken(
                    expandedAccountId,
                    symbol,
                    freezeStatus,
                    kycStatus,
                    treasuryAccount,
                    tokenSupplyType,
                    maxSupply,
                    customFees);
        }
    }

    private TokenCreateTransaction getTokenCreateTransaction(
            ExpandedAccountId expandedAccountId,
            String symbol,
            int freezeStatus,
            int kycStatus,
            ExpandedAccountId treasuryAccount,
            TokenType tokenType,
            TokenSupplyType tokenSupplyType,
            long maxSupply,
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
            transaction.setCustomFees(customFees).setFeeScheduleKey(adminKey);
        }
        return transaction;
    }

    public NetworkTransactionResponse createFungibleToken(
            ExpandedAccountId expandedAccountId,
            String symbol,
            int freezeStatus,
            int kycStatus,
            ExpandedAccountId treasuryAccount,
            int initialSupply,
            TokenSupplyType tokenSupplyType,
            long maxSupply,
            List<CustomFee> customFees) {
        TokenCreateTransaction tokenCreateTransaction = getTokenCreateTransaction(
                        expandedAccountId,
                        symbol,
                        freezeStatus,
                        kycStatus,
                        treasuryAccount,
                        TokenType.FUNGIBLE_COMMON,
                        tokenSupplyType,
                        maxSupply,
                        customFees)
                .setDecimals(10)
                .setInitialSupply(initialSupply);

        var keyList = KeyList.of(treasuryAccount.getPrivateKey());
        var response = executeTransactionAndRetrieveReceipt(tokenCreateTransaction, keyList);
        var tokenId = response.getReceipt().tokenId;
        log.info("Created new fungible token {} with symbol {} via {}", tokenId, symbol, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse createNonFungibleToken(
            ExpandedAccountId expandedAccountId,
            String symbol,
            int freezeStatus,
            int kycStatus,
            ExpandedAccountId treasuryAccount,
            TokenSupplyType tokenSupplyType,
            long maxSupply,
            List<CustomFee> customFees) {
        log.debug("Create new non-fungible token {}", symbol);
        TokenCreateTransaction tokenCreateTransaction = getTokenCreateTransaction(
                expandedAccountId,
                symbol,
                freezeStatus,
                kycStatus,
                treasuryAccount,
                TokenType.NON_FUNGIBLE_UNIQUE,
                tokenSupplyType,
                maxSupply,
                customFees);

        var keyList = KeyList.of(treasuryAccount.getPrivateKey());
        var response = executeTransactionAndRetrieveReceipt(tokenCreateTransaction, keyList);
        var tokenId = response.getReceipt().tokenId;
        log.info("Created new NFT {} with symbol {} via {}", tokenId, symbol, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse associate(ExpandedAccountId accountId, TokenId token) {
        TokenAssociateTransaction tokenAssociateTransaction = new TokenAssociateTransaction()
                .setAccountId(accountId.getAccountId())
                .setTokenIds((List.of(token)))
                .setTransactionMemo(getMemo("Associate w token"));

        var keyList = KeyList.of(accountId.getPrivateKey());
        var response = executeTransactionAndRetrieveReceipt(tokenAssociateTransaction, keyList);
        log.info("Associated account {} with token {} via {}", accountId, token, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse mint(TokenId tokenId, long amount) {
        return mint(tokenId, amount, null);
    }

    public NetworkTransactionResponse mint(TokenId tokenId, byte[] metadata) {
        return mint(tokenId, 1, metadata);
    }

    private NetworkTransactionResponse mint(TokenId tokenId, long amount, byte[] metadata) {
        TokenMintTransaction tokenMintTransaction =
                new TokenMintTransaction().setTokenId(tokenId).setTransactionMemo(getMemo("Mint token"));

        if (metadata != null) {
            tokenMintTransaction.addMetadata(metadata);
        } else {
            tokenMintTransaction.setAmount(amount);
        }

        var response = executeTransactionAndRetrieveReceipt(tokenMintTransaction);
        log.info("Minted {} tokens to {} via {}", amount, tokenId, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse freeze(TokenId tokenId, AccountId accountId) {
        TokenFreezeTransaction tokenFreezeAccountTransaction = new TokenFreezeTransaction()
                .setAccountId(accountId)
                .setTokenId(tokenId)
                .setTransactionMemo(getMemo("Freeze token account"));

        var response = executeTransactionAndRetrieveReceipt(tokenFreezeAccountTransaction);
        log.info("Froze account {} with token {} via {}", accountId, tokenId, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse unfreeze(TokenId tokenId, AccountId accountId) {
        TokenUnfreezeTransaction tokenUnfreezeTransaction = new TokenUnfreezeTransaction()
                .setAccountId(accountId)
                .setTokenId(tokenId)
                .setTransactionMemo(getMemo("Unfreeze token account"));

        var response = executeTransactionAndRetrieveReceipt(tokenUnfreezeTransaction);
        log.info("Unfroze account {} with token {} via {}", accountId, tokenId, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse grantKyc(TokenId tokenId, AccountId accountId) {
        TokenGrantKycTransaction tokenGrantKycTransaction = new TokenGrantKycTransaction()
                .setAccountId(accountId)
                .setTokenId(tokenId)
                .setTransactionMemo(getMemo("Grant kyc for token"));

        var response = executeTransactionAndRetrieveReceipt(tokenGrantKycTransaction);
        log.info("Granted KYC for account {} with token {} via {}", accountId, tokenId, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse revokeKyc(TokenId tokenId, AccountId accountId) {
        TokenRevokeKycTransaction tokenRevokeKycTransaction = new TokenRevokeKycTransaction()
                .setAccountId(accountId)
                .setTokenId(tokenId)
                .setTransactionMemo(getMemo("Revoke kyc for token"));

        var response = executeTransactionAndRetrieveReceipt(tokenRevokeKycTransaction);
        log.info("Revoked KYC for account {} with token {} via {}", accountId, tokenId, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse pause(TokenId tokenId) {
        TokenPauseTransaction tokenPauseTransaction =
                new TokenPauseTransaction().setTokenId(tokenId).setTransactionMemo(getMemo("Pause token"));

        var response = executeTransactionAndRetrieveReceipt(tokenPauseTransaction);
        log.info("Paused token {} via {}", tokenId, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse unpause(TokenId tokenId) {
        TokenUnpauseTransaction tokenUnpauseTransaction =
                new TokenUnpauseTransaction().setTokenId(tokenId).setTransactionMemo(getMemo("Unpause token"));

        var response = executeTransactionAndRetrieveReceipt(tokenUnpauseTransaction);
        log.info("Un-paused token {} via {}", tokenId, response.getTransactionId());
        return response;
    }

    public TransferTransaction getFungibleTokenTransferTransaction(
            TokenId tokenId, AccountId sender, AccountId recipient, long amount) {
        return getTransferTransaction()
                .addTokenTransfer(tokenId, sender, Math.negateExact(amount))
                .addTokenTransfer(tokenId, recipient, amount);
    }

    public TransferTransaction getNonFungibleTokenTransferTransaction(
            TokenId tokenId, AccountId sender, AccountId recipient, List<Long> serialNumbers) {
        TransferTransaction transaction = getTransferTransaction();
        for (Long serialNumber : serialNumbers) {
            transaction.addNftTransfer(new NftId(tokenId, serialNumber), sender, recipient);
        }
        return transaction;
    }

    private TransferTransaction getTransferTransaction() {
        return new TransferTransaction().setTransactionMemo(getMemo("Transfer token"));
    }

    public NetworkTransactionResponse transferFungibleToken(
            TokenId tokenId, ExpandedAccountId sender, AccountId recipient, long amount) {
        var transaction = getFungibleTokenTransferTransaction(tokenId, sender.getAccountId(), recipient, amount);
        var response = executeTransactionAndRetrieveReceipt(transaction, sender);
        log.info(
                "Transferred {} tokens of {} from {} to {} via {}",
                amount,
                tokenId,
                sender,
                recipient,
                response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse transferNonFungibleToken(
            TokenId tokenId, ExpandedAccountId sender, AccountId recipient, List<Long> serialNumbers) {
        var transaction =
                getNonFungibleTokenTransferTransaction(tokenId, sender.getAccountId(), recipient, serialNumbers);
        var response = executeTransactionAndRetrieveReceipt(transaction, sender);
        log.info(
                "Transferred serial numbers {} of token {} from {} to {} via {}",
                serialNumbers,
                tokenId,
                sender,
                recipient,
                response.getTransactionId());
        return response;
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

        var response = executeTransactionAndRetrieveReceipt(tokenUpdateTransaction);
        log.info("Updated token {} with new symbol '{}' via {}", tokenId, newSymbol, response.getTransactionId());
        return response;
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
        var response = executeTransactionAndRetrieveReceipt(tokenUpdateTransaction, keyList);
        log.info(
                "Updated token {} treasury account {} via {}", tokenId, treasuryAccountId, response.getTransactionId());
        return response;
    }

    private TokenBurnTransaction getTokenBurnTransaction(TokenId tokenId) {
        return new TokenBurnTransaction().setTokenId(tokenId).setTransactionMemo(getMemo("Burn token"));
    }

    public NetworkTransactionResponse burnFungible(TokenId tokenId, long amount) {
        TokenBurnTransaction tokenBurnTransaction =
                getTokenBurnTransaction(tokenId).setAmount(amount).setTransactionMemo(getMemo("Token burn"));

        var response = executeTransactionAndRetrieveReceipt(tokenBurnTransaction);
        log.info("Burned amount {} from token {} via {}", amount, tokenId, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse burnNonFungible(TokenId tokenId, long serialNumber) {
        TokenBurnTransaction tokenBurnTransaction =
                getTokenBurnTransaction(tokenId).addSerial(serialNumber).setTransactionMemo(getMemo("Token burn"));

        var response = executeTransactionAndRetrieveReceipt(tokenBurnTransaction);
        log.info("Burned serial number {} from token {} via {}", serialNumber, tokenId, response.getTransactionId());
        return response;
    }

    private TokenWipeTransaction getTokenWipeTransaction(TokenId tokenId, ExpandedAccountId expandedAccountId) {
        return new TokenWipeTransaction()
                .setAccountId(expandedAccountId.getAccountId())
                .setTokenId(tokenId)
                .setTransactionMemo(getMemo("Wipe token"));
    }

    public NetworkTransactionResponse wipeFungible(TokenId tokenId, long amount, ExpandedAccountId expandedAccountId) {
        TokenWipeTransaction transaction =
                getTokenWipeTransaction(tokenId, expandedAccountId).setAmount(amount);

        var response = executeTransactionAndRetrieveReceipt(transaction);
        log.info("Wiped {} tokens from account {} via {}", amount, expandedAccountId, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse wipeNonFungible(
            TokenId tokenId, long serialNumber, ExpandedAccountId expandedAccountId) {
        TokenWipeTransaction transaction =
                getTokenWipeTransaction(tokenId, expandedAccountId).addSerial(serialNumber);

        var response = executeTransactionAndRetrieveReceipt(transaction);
        log.info(
                "Wiped serial {} from token {} account {} via {}",
                serialNumber,
                tokenId,
                expandedAccountId,
                response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse dissociate(ExpandedAccountId accountId, TokenId token) {
        TokenDissociateTransaction tokenDissociateTransaction = new TokenDissociateTransaction()
                .setAccountId(accountId.getAccountId())
                .setTokenIds(List.of(token))
                .setTransactionMemo(getMemo("Dissociate token"));

        var keyList = KeyList.of(accountId.getPrivateKey());
        var response = executeTransactionAndRetrieveReceipt(tokenDissociateTransaction, keyList);
        log.info("Dissociated account {} from token {} via {}", accountId, token, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse delete(ExpandedAccountId accountId, TokenId token) {
        TokenDeleteTransaction tokenDissociateTransaction =
                new TokenDeleteTransaction().setTokenId(token).setTransactionMemo(getMemo("Delete token"));

        var keyList = KeyList.of(accountId.getPrivateKey());
        var response = executeTransactionAndRetrieveReceipt(tokenDissociateTransaction, keyList);
        log.info("Deleted token {} via {}", token, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse updateTokenFeeSchedule(
            TokenId tokenId, ExpandedAccountId expandedAccountId, List<CustomFee> customFees) {
        TokenFeeScheduleUpdateTransaction transaction = new TokenFeeScheduleUpdateTransaction()
                .setCustomFees(customFees)
                .setTokenId(tokenId)
                .setTransactionMemo(getMemo("Update token fee schedule"));

        var response = executeTransactionAndRetrieveReceipt(transaction, KeyList.of(expandedAccountId.getPrivateKey()));
        log.info("Updated custom fees schedule for token {} via {}", tokenId, response.getTransactionId());
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
