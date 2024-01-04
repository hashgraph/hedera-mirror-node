/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.CustomFee;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.PrivateKey;
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
import com.hedera.hashgraph.sdk.proto.TokenKycStatus;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import jakarta.inject.Named;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.retry.support.RetryTemplate;

@Named
public class TokenClient extends AbstractNetworkClient {

    private final Collection<TokenId> tokenIds = new CopyOnWriteArrayList<>();

    private final Map<TokenNameEnum, TokenResponse> tokenMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<TokenAccount, NetworkTransactionResponse> associations = new ConcurrentHashMap<>();

    public TokenClient(SDKClient sdkClient, RetryTemplate retryTemplate) {
        super(sdkClient, retryTemplate);
    }

    @Override
    public void clean() {
        var admin = sdkClient.getExpandedOperatorAccountId();
        log.info("Deleting {} tokens and dissociating {} token relationships", tokenIds.size(), associations.size());
        deleteAll(tokenIds, tokenId -> delete(admin, tokenId));
        deleteAll(associations.keySet(), association -> dissociate(association.accountId, association.tokenId));
    }

    @Override
    public int getOrder() {
        return -1;
    }

    public TokenResponse getToken(TokenNameEnum tokenNameEnum) {
        return getToken(tokenNameEnum, List.of());
    }

    public TokenResponse getToken(TokenNameEnum tokenNameEnum, List<CustomFee> customFees) {
        // Create token only if it does not currently exist
        var tokenId = tokenMap.computeIfAbsent(tokenNameEnum, x -> {
            var operator = sdkClient.getExpandedOperatorAccountId();
            try {
                var networkTransactionResponse = createToken(tokenNameEnum, operator, customFees);
                return new TokenResponse(networkTransactionResponse.getReceipt().tokenId, networkTransactionResponse);
            } catch (Exception e) {
                log.warn("Issue creating additional token: {}, operator: {}, ex: {}", tokenNameEnum, operator, e);
                return null;
            }
        });

        if (tokenId == null) {
            throw new NetworkException("Null tokenId retrieved from receipt");
        }

        log.debug("Retrieved token: {} with ID: {}", tokenNameEnum, tokenId);
        return tokenMap.get(tokenNameEnum);
    }

    private NetworkTransactionResponse createToken(
            TokenNameEnum tokenNameEnum, ExpandedAccountId expandedAccountId, List<CustomFee> customFees) {
        return createToken(
                expandedAccountId,
                tokenNameEnum.symbol,
                tokenNameEnum.tokenFreezeStatus.getNumber(),
                tokenNameEnum.tokenKycStatus.getNumber(),
                expandedAccountId,
                1_000_000,
                TokenSupplyType.INFINITE,
                1,
                tokenNameEnum.tokenType,
                customFees);
    }

    public NetworkTransactionResponse createToken(
            ExpandedAccountId expandedAccountId,
            String symbol,
            int freezeStatus,
            int kycStatus,
            ExpandedAccountId treasuryAccount,
            long initialSupply,
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
            long initialSupply,
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
        tokenIds.add(tokenId);
        associations.put(new TokenAccount(tokenId, treasuryAccount), response);
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
        tokenIds.add(tokenId);
        associations.put(new TokenAccount(tokenId, treasuryAccount), response);
        return response;
    }

    public NetworkTransactionResponse associate(ContractId contractId, TokenId token) {
        return associate(
                new ExpandedAccountId(
                        AccountId.fromString(contractId.toString()),
                        sdkClient.getExpandedOperatorAccountId().getPrivateKey()),
                token);
    }

    public NetworkTransactionResponse associate(ExpandedAccountId accountId, TokenId token) {
        var key = new TokenAccount(token, accountId);

        return associations.computeIfAbsent(key, k -> {
            TokenAssociateTransaction tokenAssociateTransaction = new TokenAssociateTransaction()
                    .setAccountId(accountId.getAccountId())
                    .setTokenIds((List.of(token)))
                    .setTransactionMemo(getMemo("Associate w token"));

            KeyList keyList = (accountId.getPrivateKey() != null) ? KeyList.of(accountId.getPrivateKey()) : null;
            NetworkTransactionResponse response =
                    executeTransactionAndRetrieveReceipt(tokenAssociateTransaction, keyList);

            log.info("Associated account {} with token {} via {}", accountId, token, response.getTransactionId());

            return response;
        });
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

    private TransferTransaction getFungibleTokenTransferTransaction(
            TokenId tokenId, AccountId owner, AccountId sender, AccountId recipient, long amount, boolean isApproval) {
        var transferTransaction = getTransferTransaction();

        if (isApproval) {
            transferTransaction.addApprovedTokenTransfer(tokenId, owner, Math.negateExact(amount));
        } else {
            transferTransaction.addTokenTransfer(tokenId, sender, Math.negateExact(amount));
        }
        transferTransaction.addTokenTransfer(tokenId, recipient, amount);
        return transferTransaction;
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
            TokenId tokenId, ExpandedAccountId sender, AccountId recipient, PrivateKey privateKey, long amount) {
        return transferFungibleToken(tokenId, sender.getAccountId(), sender, recipient, amount, false, privateKey);
    }

    public NetworkTransactionResponse transferFungibleToken(
            TokenId tokenId,
            AccountId owner,
            ExpandedAccountId sender,
            AccountId recipient,
            long amount,
            boolean isApproval,
            PrivateKey privateKey) {
        var transaction = getFungibleTokenTransferTransaction(
                tokenId, owner, sender.getAccountId(), recipient, amount, isApproval);
        var response = executeTransactionAndRetrieveReceipt(
                transaction, privateKey == null ? null : KeyList.of(privateKey), sender);
        log.info(
                "Transferred {} tokens of {} from {} to {} via {}, isApproval {}",
                amount,
                tokenId,
                sender,
                recipient,
                response.getTransactionId(),
                isApproval);
        return response;
    }

    public NetworkTransactionResponse transferNonFungibleToken(
            TokenId tokenId,
            ExpandedAccountId sender,
            AccountId recipient,
            List<Long> serialNumbers,
            PrivateKey privateKey) {
        var transaction =
                getNonFungibleTokenTransferTransaction(tokenId, sender.getAccountId(), recipient, serialNumbers);
        var response = executeTransactionAndRetrieveReceipt(
                transaction, privateKey == null ? null : KeyList.of(privateKey), sender);
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
        associations.remove(new TokenAccount(token, accountId));
        return response;
    }

    public NetworkTransactionResponse delete(ExpandedAccountId accountId, TokenId token) {
        TokenDeleteTransaction tokenDissociateTransaction =
                new TokenDeleteTransaction().setTokenId(token).setTransactionMemo(getMemo("Delete token"));

        var keyList = KeyList.of(accountId.getPrivateKey());
        var response = executeTransactionAndRetrieveReceipt(tokenDissociateTransaction, keyList);
        log.info("Deleted token {} via {}", token, response.getTransactionId());
        tokenIds.remove(token);
        tokenMap.values().removeIf(tokenResponse -> token.equals(tokenResponse.tokenId));
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

    @RequiredArgsConstructor
    @Getter
    public enum TokenNameEnum {
        // also used in call.feature
        FUNGIBLE(
                "fungible",
                TokenType.FUNGIBLE_COMMON,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.FreezeNotApplicable),
        FUNGIBLE_DELETABLE(
                "fungible_deletable",
                TokenType.FUNGIBLE_COMMON,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.FreezeNotApplicable),
        FUNGIBLE_FOR_ETH_CALL(
                "fungible",
                TokenType.FUNGIBLE_COMMON,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.FreezeNotApplicable),
        // used in tokenAllowance.feature and not using snake_case because cucumber cannot detect the enum
        ALLOWANCEFUNGIBLE(
                "allowance_fungible",
                TokenType.FUNGIBLE_COMMON,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.FreezeNotApplicable),
        FUNGIBLE_WITH_CUSTOM_FEES(
                "fungible_custom_fees",
                TokenType.FUNGIBLE_COMMON,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.FreezeNotApplicable),
        NFT(
                "non_fungible",
                TokenType.NON_FUNGIBLE_UNIQUE,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.FreezeNotApplicable),
        NFT_FOR_ETH_CALL(
                "non_fungible",
                TokenType.NON_FUNGIBLE_UNIQUE,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.FreezeNotApplicable),
        NFT_ERC(
                "non_fungible_erc",
                TokenType.NON_FUNGIBLE_UNIQUE,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.FreezeNotApplicable),
        NFT_DELETABLE(
                "non_fungible_deletable",
                TokenType.NON_FUNGIBLE_UNIQUE,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.FreezeNotApplicable),
        FUNGIBLE_KYC_UNFROZEN(
                "fungible_kyc_unfrozen", TokenType.FUNGIBLE_COMMON, TokenKycStatus.Granted, TokenFreezeStatus.Unfrozen),
        FUNGIBLE_KYC_UNFROZEN_2(
                "fungible_kyc_unfrozen_2",
                TokenType.FUNGIBLE_COMMON,
                TokenKycStatus.Granted,
                TokenFreezeStatus.Unfrozen),
        FUNGIBLE_KYC_UNFROZEN_FOR_ETH_CALL(
                "fungible_kyc_unfrozen_for_eth_call",
                TokenType.FUNGIBLE_COMMON,
                TokenKycStatus.Granted,
                TokenFreezeStatus.Unfrozen),
        FUNGIBLE_KYC_NOT_APPLICABLE_UNFROZEN(
                "fungible_kyc_not_applicable_unfrozen",
                TokenType.FUNGIBLE_COMMON,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.Unfrozen),
        NFT_KYC_NOT_APPLICABLE_UNFROZEN(
                "nft_kyc_not_applicable_unfrozen",
                TokenType.NON_FUNGIBLE_UNIQUE,
                TokenKycStatus.KycNotApplicable,
                TokenFreezeStatus.Unfrozen),
        NFT_KYC_UNFROZEN(
                "nft_kyc_unfrozen", TokenType.NON_FUNGIBLE_UNIQUE, TokenKycStatus.Granted, TokenFreezeStatus.Unfrozen);

        private final String symbol;
        private final TokenType tokenType;
        private final TokenKycStatus tokenKycStatus;
        private final TokenFreezeStatus tokenFreezeStatus;

        @Override
        public String toString() {
            return String.format("%s (%s)", symbol, tokenType);
        }
    }

    public record TokenResponse(TokenId tokenId, NetworkTransactionResponse response) {}

    private record TokenAccount(TokenId tokenId, ExpandedAccountId accountId) {}
}
