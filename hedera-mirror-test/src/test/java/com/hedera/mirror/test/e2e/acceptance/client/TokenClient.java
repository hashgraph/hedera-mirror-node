package com.hedera.mirror.test.e2e.acceptance.client;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
import java.time.temporal.ChronoUnit;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;

import com.hedera.hashgraph.proto.TokenFreezeStatus;
import com.hedera.hashgraph.sdk.HederaStatusException;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;
import com.hedera.hashgraph.sdk.token.TokenAssociateTransaction;
import com.hedera.hashgraph.sdk.token.TokenBurnTransaction;
import com.hedera.hashgraph.sdk.token.TokenCreateTransaction;
import com.hedera.hashgraph.sdk.token.TokenDeleteTransaction;
import com.hedera.hashgraph.sdk.token.TokenDissociateTransaction;
import com.hedera.hashgraph.sdk.token.TokenFreezeTransaction;
import com.hedera.hashgraph.sdk.token.TokenGrantKycTransaction;
import com.hedera.hashgraph.sdk.token.TokenId;
import com.hedera.hashgraph.sdk.token.TokenMintTransaction;
import com.hedera.hashgraph.sdk.token.TokenRevokeKycTransaction;
import com.hedera.hashgraph.sdk.token.TokenTransferTransaction;
import com.hedera.hashgraph.sdk.token.TokenUnfreezeTransaction;
import com.hedera.hashgraph.sdk.token.TokenUpdateTransaction;
import com.hedera.hashgraph.sdk.token.TokenWipeTransaction;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;

@Log4j2
@Value
public class TokenClient extends AbstractNetworkClient {

    public TokenClient(SDKClient sdkClient) {
        super(sdkClient);
        log.debug("Creating Token Client");
    }

    public NetworkTransactionResponse createToken(ExpandedAccountId expandedAccountId, String symbol, int freezeStatus,
                                                  int kycStatus) throws HederaStatusException {

        log.debug("Create new token {}", symbol);
        Instant refInstant = Instant.now();
        Ed25519PublicKey adminKey = expandedAccountId.getPublicKey();
        TokenCreateTransaction tokenCreateTransaction = new TokenCreateTransaction()
                .setAutoRenewAccount(expandedAccountId.getAccountId())
                .setDecimals(10)
                .setFreezeDefault(false)
                .setInitialSupply(1000000000)
                .setName(symbol + "_name")
                .setSymbol(symbol)
                .setTreasury(client.getOperatorId())
                .setMaxTransactionFee(1_000_000_000)
                .setExpirationTime(Instant.now().plus(120, ChronoUnit.DAYS))
                .setTransactionMemo("Create token_" + refInstant);

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
                executeTransactionAndRetrieveReceipt(tokenCreateTransaction, null);
        TokenId tokenId = networkTransactionResponse.getReceipt().getTokenId();
        log.debug("Created new token {}", tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse asssociate(ExpandedAccountId accountId, TokenId token) throws HederaStatusException {

        log.debug("Associate account {} with token {}", accountId.getAccountId(), token);
        Instant refInstant = Instant.now();
        TokenAssociateTransaction tokenAssociateTransaction = new TokenAssociateTransaction()
                .setAccountId(accountId.getAccountId())
                .addTokenId(token)
                .setMaxTransactionFee(1_000_000_000)
                .setTransactionMemo("Associate token_" + refInstant);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenAssociateTransaction,
                        accountId.getPrivateKey());

        log.debug("Associated {} with token {}", accountId, token);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse mint(TokenId tokenId, long amount) throws HederaStatusException {

        log.debug("Mint {} tokens from {}", amount, tokenId);
        Instant refInstant = Instant.now();
        TokenMintTransaction tokenMintTransaction = new TokenMintTransaction()
                .setTokenId(tokenId)
                .setAmount(amount)
                .setMaxTransactionFee(1_000_000_000)
                .setTransactionMemo("Associate token_" + refInstant);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenMintTransaction, null);

        log.debug("Minted {} extra tokens for token {}", amount, tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse freeze(TokenId tokenId, AccountId accountId, Ed25519PrivateKey freezeKey) throws HederaStatusException {

        Instant refInstant = Instant.now();
        TokenFreezeTransaction tokenFreezeAccountTransaction = new TokenFreezeTransaction()
                .setAccountId(accountId)
                .setMaxTransactionFee(1_000_000_000)
                .setTokenId(tokenId)
                .setTransactionMemo("Freeze account_" + refInstant);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenFreezeAccountTransaction,
                        freezeKey);

        log.debug("Freeze account {} with token {}", accountId, tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse unfreeze(TokenId tokenId, AccountId accountId, Ed25519PrivateKey freezeKey) throws HederaStatusException {

        Instant refInstant = Instant.now();
        TokenUnfreezeTransaction tokenUnfreezeTransaction = new TokenUnfreezeTransaction()
                .setAccountId(accountId)
                .setMaxTransactionFee(1_000_000_000)
                .setTokenId(tokenId)
                .setTransactionMemo("Unfreeze account_" + refInstant);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenUnfreezeTransaction,
                        freezeKey);

        log.debug("Unfreeze account {} with token {}", accountId, tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse grantKyc(TokenId tokenId, AccountId accountId, Ed25519PrivateKey kycKey) throws HederaStatusException {

        log.debug("Grant account {} with KYC for token {}", accountId, tokenId);
        Instant refInstant = Instant.now();
        TokenGrantKycTransaction tokenGrantKycTransaction = new TokenGrantKycTransaction()
                .setAccountId(accountId)
                .setTokenId(tokenId)
                .setMaxTransactionFee(1_000_000_000)
                .setTransactionMemo("Grant kyc for token_" + refInstant);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenGrantKycTransaction, kycKey);

        log.debug("Granted Kyc for account {} with token {}", accountId, tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse revokeKyc(TokenId tokenId, AccountId accountId, Ed25519PrivateKey kycKey) throws HederaStatusException {

        log.debug("Grant account {} with KYC for token {}", accountId, tokenId);
        Instant refInstant = Instant.now();
        TokenRevokeKycTransaction tokenRevokeKycTransaction = new TokenRevokeKycTransaction()
                .setAccountId(accountId)
                .setTokenId(tokenId)
                .setMaxTransactionFee(1_000_000_000)
                .setTransactionMemo("Revoke kyc for token_" + refInstant);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenRevokeKycTransaction, kycKey);

        log.debug("Revoked Kyc for account {} with token {}", accountId, tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse transferToken(TokenId tokenId, AccountId sender, AccountId recipient,
                                                    long amount) throws HederaStatusException {

        log.debug("Transfer {} of token {} from {} to {}", amount, tokenId, sender, recipient);
        Instant refInstant = Instant.now();
        TokenTransferTransaction tokenTransferTransaction = new TokenTransferTransaction()
                .addSender(tokenId, sender, amount)
                .addRecipient(tokenId, recipient, amount)
                .setMaxTransactionFee(1_000_000)
                .setTransactionMemo("Transfer token_" + refInstant);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenTransferTransaction, null);

        log.debug("Transferred {} tokens of {} from {} to {}", amount, tokenId, sender,
                recipient);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse updateToken(TokenId tokenId, ExpandedAccountId expandedAccountId) throws HederaStatusException {
        Ed25519PublicKey publicKey = expandedAccountId.getPublicKey();
        String newSymbol = RandomStringUtils.randomAlphabetic(4).toUpperCase();
        TokenUpdateTransaction tokenUpdateTransaction = new TokenUpdateTransaction()
                .setAdminKey(publicKey)
                .setAutoRenewAccount(expandedAccountId.getAccountId())
                .setExpirationTime(Instant.now().plus(120, ChronoUnit.DAYS))
                .setName(newSymbol + "_name")
                .setSupplyKey(publicKey)
                .setSybmol(newSymbol)
                .setTokenId(tokenId)
                .setTreasury(client.getOperatorId())
                .setWipeKey(publicKey);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenUpdateTransaction, null);

        log.debug("Updated token {}.", tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse burn(TokenId tokenId, long amount) throws HederaStatusException {

        log.debug("Burn {} tokens from {}", amount, tokenId);
        Instant refInstant = Instant.now();
        TokenBurnTransaction tokenBurnTransaction = new TokenBurnTransaction()
                .setTokenId(tokenId)
                .setAmount(amount)
                .setMaxTransactionFee(1_000_000_000)
                .setTransactionMemo("Burn token_" + refInstant);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenBurnTransaction, null);

        log.debug("Burned {} extra tokens for token {}", amount, tokenId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse wipe(TokenId tokenId, long amount, ExpandedAccountId expandedAccountId) throws HederaStatusException {

        log.debug("Wipe {} tokens from {}", amount, tokenId);
        Instant refInstant = Instant.now();
        TokenWipeTransaction tokenWipeAccountTransaction = new TokenWipeTransaction()
                .setAccountId(expandedAccountId.getAccountId())
                .setTokenId(tokenId)
                .setAmount(amount)
                .setMaxTransactionFee(1_000_000_000)
                .setTransactionMemo("Wipe token_" + refInstant);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenWipeAccountTransaction, null);

        log.debug("Wiped {} tokens from account {}", amount, expandedAccountId.getAccountId());

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse disssociate(ExpandedAccountId accountId, TokenId token) throws HederaStatusException {

        log.debug("Dissociate account {} with token {}", accountId.getAccountId(), token);
        Instant refInstant = Instant.now();
        TokenDissociateTransaction tokenDissociateTransaction = new TokenDissociateTransaction()
                .setAccountId(accountId.getAccountId())
                .addTokenId(token)
                .setMaxTransactionFee(1_000_000_000)
                .setTransactionMemo("Dissociate token_" + refInstant);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenDissociateTransaction,
                        accountId.getPrivateKey());

        log.debug("Dissociated {} with token {}", accountId, token);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse delete(ExpandedAccountId accountId, TokenId token) throws HederaStatusException {

        log.debug("Delete token {}", token);
        Instant refInstant = Instant.now();
        TokenDeleteTransaction tokenDissociateTransaction = new TokenDeleteTransaction()
                .setTokenId(token)
                .setMaxTransactionFee(1_000_000_000)
                .setTransactionMemo("Delete token_" + refInstant);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(tokenDissociateTransaction,
                        accountId.getPrivateKey());

        log.debug("Deleted token {}", accountId, token);

        return networkTransactionResponse;
    }
}
