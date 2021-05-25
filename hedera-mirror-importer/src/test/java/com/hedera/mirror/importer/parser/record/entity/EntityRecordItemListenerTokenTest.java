package com.hedera.mirror.importer.parser.record.entity;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.StringValue;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;

import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.config.CacheConfiguration;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenFreezeStatusEnum;
import com.hedera.mirror.importer.domain.TokenKycStatusEnum;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.repository.TokenAccountRepository;
import com.hedera.mirror.importer.repository.TokenRepository;
import com.hedera.mirror.importer.repository.TokenTransferRepository;

public class EntityRecordItemListenerTokenTest extends AbstractEntityRecordItemListenerTest {

    private static final long ASSOCIATE_TIMESTAMP = 5L;
    private static final long AUTO_RENEW_PERIOD = 30L;
    private static final long CREATE_TIMESTAMP = 1L;
    private static final Timestamp EXPIRY_TIMESTAMP = Timestamp.newBuilder().setSeconds(360L).build();
    private static final long EXPIRY_NS = EXPIRY_TIMESTAMP.getSeconds() * 1_000_000_000 + EXPIRY_TIMESTAMP.getNanos();
    private static final long INITIAL_SUPPLY = 1_000_000L;
    private static final String SYMBOL = "FOOCOIN";
    private static final String TOKEN_CREATE_MEMO = "TokenCreate memo";
    private static final TokenID TOKEN_ID = TokenID.newBuilder().setShardNum(0).setRealmNum(0).setTokenNum(2).build();
    private static final Key TOKEN_REF_KEY = keyFromString(KEY);
    private static final long TOKEN_UPDATE_AUTO_RENEW_PERIOD = 12L;
    private static final Key TOKEN_UPDATE_REF_KEY = keyFromString(KEY2);
    private static final String TOKEN_UPDATE_MEMO = "TokenUpdate memo";

    @Resource
    protected TokenRepository tokenRepository;

    @Resource
    protected TokenAccountRepository tokenAccountRepository;

    @Resource
    protected TokenTransferRepository tokenTransferRepository;

    @Qualifier(CacheConfiguration.EXPIRE_AFTER_30M)
    @Resource
    private CacheManager cacheManager;

    void beforeAll() {
        cacheManager.getCache("tokenaccounts").clear();
    }

    @BeforeEach
    void before() {
        entityProperties.getPersist().setTokens(true);
    }

    @Test
    void tokenCreate() throws InvalidProtocolBufferException {
        createTokenEntity(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, false, false);

        Entity expected = createEntity(EntityId.of(TOKEN_ID), TOKEN_REF_KEY, EntityId.of(PAYER), AUTO_RENEW_PERIOD,
                false, EXPIRY_NS, TOKEN_CREATE_MEMO, null);
        assertEquals(4, entityRepository.count()); // Node, payer, token and autorenew
        assertEntity(expected);

        // verify token
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, CREATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertThat(tokenTransferRepository.count()).isEqualTo(1L);
    }

    @Test
    void tokenCreateWithoutPersistence() throws InvalidProtocolBufferException {
        entityProperties.getPersist().setTokens(false);

        createTokenEntity(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, false, false);

        assertTokenInRepository(TOKEN_ID, false, CREATE_TIMESTAMP, CREATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
        assertThat(tokenTransferRepository.count()).isZero();
    }

    @Test
    void tokenAssociate() throws InvalidProtocolBufferException {
        createTokenEntity(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, true, true);

        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER);
        insertAndParseTransaction(associateTransaction, ASSOCIATE_TIMESTAMP, INITIAL_SUPPLY);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, true,
                TokenFreezeStatusEnum.UNFROZEN, TokenKycStatusEnum.REVOKED);
    }

    @Test
    void tokenAssociateWithMissingToken() throws InvalidProtocolBufferException {
        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER);
        insertAndParseTransaction(associateTransaction, ASSOCIATE_TIMESTAMP, INITIAL_SUPPLY);

        // verify token account was not created
        assertTokenAccountInRepository(TOKEN_ID, PAYER, false, CREATE_TIMESTAMP, CREATE_TIMESTAMP, true,
                TokenFreezeStatusEnum.UNFROZEN, TokenKycStatusEnum.REVOKED);
    }

    @Test
    void tokenDissociate() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, PAYER, false, false);

        Transaction dissociateTransaction = tokenDissociate(List.of(TOKEN_ID), PAYER);
        long dissociateTimeStamp = 10L;
        insertAndParseTransaction(dissociateTransaction, dissociateTimeStamp, INITIAL_SUPPLY);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, dissociateTimeStamp, false,
                TokenFreezeStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.NOT_APPLICABLE);
    }

    @Test
    void tokenDelete() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, PAYER, false, false);

        // delete token
        Transaction deleteTransaction = tokenDeleteTransaction(TOKEN_ID);
        insertAndParseTransaction(deleteTransaction, 10L, INITIAL_SUPPLY);

        Entity expected = createEntity(EntityId.of(TOKEN_ID), TOKEN_REF_KEY, EntityId.of(PAYER), AUTO_RENEW_PERIOD,
                true, EXPIRY_NS, TOKEN_CREATE_MEMO, null);
        assertEquals(4, entityRepository.count()); // Node, payer, token and autorenew
        assertEntity(expected);

        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, CREATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
    }

    @Test
    void tokenUpdate() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, PAYER, false, false);

        String newSymbol = "NEWSYMBOL";
        Transaction transaction = tokenUpdateTransaction(
                TOKEN_ID,
                newSymbol,
                TOKEN_UPDATE_MEMO,
                TOKEN_UPDATE_REF_KEY,
                PAYER2);
        long updateTimeStamp = 10L;
        insertAndParseTransaction(transaction, updateTimeStamp, INITIAL_SUPPLY);

        Entity expected = createEntity(EntityId.of(TOKEN_ID), TOKEN_UPDATE_REF_KEY, EntityId.of(PAYER2),
                TOKEN_UPDATE_AUTO_RENEW_PERIOD, false, EXPIRY_NS, TOKEN_UPDATE_MEMO, null);
        assertEquals(5, entityRepository.count()); // Node, payer, token, old autorenew, and new autorenew
        assertEntity(expected);

        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, updateTimeStamp, newSymbol, INITIAL_SUPPLY);
    }

    @Test
    void tokenUpdateWithMissingToken() throws InvalidProtocolBufferException {
        String newSymbol = "NEWSYMBOL";
        Transaction transaction = tokenUpdateTransaction(
                TOKEN_ID,
                newSymbol,
                TOKEN_UPDATE_MEMO,
                keyFromString("updated-key"),
                AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(2002).build());
        insertAndParseTransaction(transaction, 10L, INITIAL_SUPPLY);

        // verify token was not created when missing
        assertTokenInRepository(TOKEN_ID, false, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
    }

    @Test
    void tokenAccountFreeze() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, PAYER, true, false);

        Transaction transaction = tokenFreezeTransaction(TOKEN_ID, true);
        long freezeTimeStamp = 15L;
        insertAndParseTransaction(transaction, freezeTimeStamp, INITIAL_SUPPLY);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, freezeTimeStamp, true,
                TokenFreezeStatusEnum.FROZEN, TokenKycStatusEnum.NOT_APPLICABLE);
    }

    @Test
    void tokenAccountUnfreeze() throws InvalidProtocolBufferException {
        // create token with freeze default
        createTokenEntity(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, true, false);

        // associate account
        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER);
        insertAndParseTransaction(associateTransaction, ASSOCIATE_TIMESTAMP, INITIAL_SUPPLY);

        Transaction freezeTransaction = tokenFreezeTransaction(TOKEN_ID, true);
        long freezeTimeStamp = 10L;
        insertAndParseTransaction(freezeTransaction, freezeTimeStamp, INITIAL_SUPPLY);

        // unfreeze
        Transaction unfreezeTransaction = tokenFreezeTransaction(TOKEN_ID, false);
        long unfreezeTimeStamp = 444;
        insertAndParseTransaction(unfreezeTransaction, unfreezeTimeStamp, INITIAL_SUPPLY);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, unfreezeTimeStamp, true,
                TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.NOT_APPLICABLE);
    }

    @Test
    void tokenAccountGrantKyc() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, PAYER, false, true);

        Transaction transaction = tokenKycTransaction(TOKEN_ID, true);
        long grantTimeStamp = 10L;
        insertAndParseTransaction(transaction, grantTimeStamp, INITIAL_SUPPLY);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, grantTimeStamp, true,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.GRANTED);
    }

    @Test
    void tokenAccountGrantKycWithMissingTokenAccount() throws InvalidProtocolBufferException {
        createTokenEntity(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, false, true);

        Transaction transaction = tokenKycTransaction(TOKEN_ID, true);
        long grantTimeStamp = 10L;
        insertAndParseTransaction(transaction, grantTimeStamp, INITIAL_SUPPLY);

        // verify token account was not created when missing
        assertTokenAccountInRepository(TOKEN_ID, PAYER, false, ASSOCIATE_TIMESTAMP, grantTimeStamp, true,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.GRANTED);
    }

    @Test
    void tokenAccountRevokeKyc() throws InvalidProtocolBufferException {
        // create token with kyc revoked
        createTokenEntity(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, false, true);

        // associate account
        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER);
        insertAndParseTransaction(associateTransaction, ASSOCIATE_TIMESTAMP, INITIAL_SUPPLY);

        Transaction grantTransaction = tokenKycTransaction(TOKEN_ID, true);
        long grantTimeStamp = 10L;
        insertAndParseTransaction(grantTransaction, grantTimeStamp, INITIAL_SUPPLY);

        // revoke
        Transaction revokeTransaction = tokenKycTransaction(TOKEN_ID, false);
        long revokeTimeStamp = 333;
        insertAndParseTransaction(revokeTransaction, revokeTimeStamp, INITIAL_SUPPLY);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, revokeTimeStamp, true,
                TokenFreezeStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.REVOKED);
    }

    @Test
    void tokenBurn() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, PAYER, false, false);

        long amount = -1000;
        long burnTimestamp = 10L;
        TokenTransferList tokenTransfer = tokenTransfer(TOKEN_ID, PAYER, amount);
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, false, amount);
        insertAndParseTransaction(transaction, burnTimestamp, INITIAL_SUPPLY - amount, tokenTransfer);

        // Verify
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, burnTimestamp, amount);
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, burnTimestamp, SYMBOL, INITIAL_SUPPLY - amount);
    }

    @Test
    void tokenMint() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, PAYER, false, false);

        long amount = 1000;
        long mintTimestamp = 10L;
        TokenTransferList tokenTransfer = tokenTransfer(TOKEN_ID, PAYER, amount);
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, true, amount);
        insertAndParseTransaction(transaction, mintTimestamp, INITIAL_SUPPLY + amount, tokenTransfer);

        // Verify
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, mintTimestamp, amount);
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, mintTimestamp, SYMBOL, INITIAL_SUPPLY + amount);
    }

    @Test
    void tokenTransfer() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, PAYER, false, false);
        TokenID tokenID2 = TokenID.newBuilder().setShardNum(0).setRealmNum(0).setTokenNum(7).build();
        String symbol2 = "MIRROR";
        createTokenEntity(tokenID2, symbol2, 10L, false, false);

        AccountID accountId = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(1).build();

        // token transfer
        Transaction transaction = tokenTransferTransaction();

        TokenTransferList transferList1 = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addTransfers(AccountAmount.newBuilder().setAccountID(PAYER).setAmount(-1000).build())
                .addTransfers(AccountAmount.newBuilder().setAccountID(accountId).setAmount(1000).build())
                .build();
        TokenTransferList transferList2 = TokenTransferList.newBuilder()
                .setToken(tokenID2)
                .addTransfers(AccountAmount.newBuilder().setAccountID(PAYER).setAmount(333).build())
                .addTransfers(AccountAmount.newBuilder().setAccountID(accountId).setAmount(-333).build())
                .build();

        long transferTimeStamp = 15L;
        insertAndParseTransaction(transaction, transferTimeStamp, INITIAL_SUPPLY, transferList1, transferList2);

        assertTokenTransferInRepository(TOKEN_ID, PAYER, transferTimeStamp, -1000);
        assertTokenTransferInRepository(TOKEN_ID, accountId, transferTimeStamp, 1000);
        assertTokenTransferInRepository(tokenID2, PAYER, transferTimeStamp, 333);
        assertTokenTransferInRepository(tokenID2, accountId, transferTimeStamp, -333);
    }

    @Test
    void tokenWipe() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, PAYER, false, false);

        long transferAmount = -1000L;
        long wipeAmount = 100L;
        long wipeTimestamp = 10L;
        TokenTransferList tokenTransfer = tokenTransfer(TOKEN_ID, PAYER, transferAmount);
        Transaction transaction = tokenWipeTransaction(TOKEN_ID, wipeAmount);
        insertAndParseTransaction(transaction, wipeTimestamp, INITIAL_SUPPLY - wipeAmount, tokenTransfer);

        // Verify
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, wipeTimestamp, SYMBOL, INITIAL_SUPPLY - wipeAmount);
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, wipeTimestamp, transferAmount);
    }

    @Test
    void tokenWipeWithMissingToken() throws InvalidProtocolBufferException {
        Transaction transaction = tokenWipeTransaction(TOKEN_ID, 100L);
        insertAndParseTransaction(transaction, 10L, INITIAL_SUPPLY);

        assertTokenInRepository(TOKEN_ID, false, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
    }

    @Test
    void tokenCreateAndAssociateAndWipeInSameRecordFile() throws InvalidProtocolBufferException {
        long transferAmount = -1000L;
        long wipeAmount = 100L;
        long wipeTimestamp = 10L;
        long newTotalSupply = INITIAL_SUPPLY - wipeAmount;

        // create token with a transfer
        Transaction createTransaction = tokenCreateTransaction(false, false, SYMBOL);
        TransactionBody createTransactionBody = getTransactionBody(createTransaction);
        TokenTransferList createTokenTransfer = tokenTransfer(TOKEN_ID, PAYER, INITIAL_SUPPLY);
        var createTransactionRecord = createTransactionRecord(CREATE_TIMESTAMP, TOKEN_ID
                .getTokenNum(), createTransactionBody, ResponseCodeEnum.SUCCESS, INITIAL_SUPPLY, createTokenTransfer);

        // associate with token
        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER);
        TransactionBody associateTransactionBody = getTransactionBody(associateTransaction);
        var associateRecord = createTransactionRecord(ASSOCIATE_TIMESTAMP, TOKEN_ID
                .getTokenNum(), associateTransactionBody, ResponseCodeEnum.SUCCESS, INITIAL_SUPPLY, null);

        // wipe amount from token with a transfer
        TokenTransferList wipeTokenTransfer = tokenTransfer(TOKEN_ID, PAYER, transferAmount);
        Transaction wipeTransaction = tokenWipeTransaction(TOKEN_ID, wipeAmount);
        TransactionBody wipeTransactionBody = getTransactionBody(wipeTransaction);
        var wipeRecord = createTransactionRecord(wipeTimestamp, TOKEN_ID
                        .getTokenNum(), wipeTransactionBody, ResponseCodeEnum.SUCCESS, newTotalSupply,
                wipeTokenTransfer);

        // process all record items in a single file
        parseRecordItemsAndCommit(List.of(
                new RecordItem(createTransaction, createTransactionRecord),
                new RecordItem(associateTransaction, associateRecord),
                new RecordItem(wipeTransaction, wipeRecord)));

        // Verify token, tokenAccount and tokenTransfer
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, wipeTimestamp, SYMBOL, newTotalSupply);
        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, true,
                TokenFreezeStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.NOT_APPLICABLE);
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, wipeTimestamp, transferAmount);
    }

    private void insertAndParseTransaction(Transaction transaction, long timeStamp, long newTotalSupply,
                                           TokenTransferList... tokenTransferLists) throws InvalidProtocolBufferException {
        TransactionBody transactionBody = getTransactionBody(transaction);

        var transactionRecord = createTransactionRecord(timeStamp, TOKEN_ID
                .getTokenNum(), transactionBody, ResponseCodeEnum.SUCCESS, newTotalSupply, tokenTransferLists);

        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));
        assertTransactionInRepository(ResponseCodeEnum.SUCCESS, timeStamp, null);
    }

    private Transaction tokenCreateTransaction(boolean setFreezeKey, boolean setKycKey, String symbol) {
        return buildTransaction(builder -> {
            builder.getTokenCreationBuilder()
                    .setAdminKey(TOKEN_REF_KEY)
                    .setAutoRenewAccount(PAYER)
                    .setAutoRenewPeriod(Duration.newBuilder().setSeconds(AUTO_RENEW_PERIOD))
                    .setDecimals(1000)
                    .setExpiry(EXPIRY_TIMESTAMP)
                    .setFreezeDefault(false)
                    .setInitialSupply(INITIAL_SUPPLY)
                    .setMemo(TOKEN_CREATE_MEMO)
                    .setName(symbol + "_token_name")
                    .setSupplyKey(TOKEN_REF_KEY)
                    .setSymbol(symbol)
                    .setTreasury(PAYER)
                    .setWipeKey(TOKEN_REF_KEY);

            if (setFreezeKey) {
                builder.getTokenCreationBuilder()
                        .setFreezeKey(TOKEN_REF_KEY);
            }

            if (setKycKey) {
                builder.getTokenCreationBuilder().setKycKey(TOKEN_REF_KEY);
            }
        });
    }

    private Transaction tokenUpdateTransaction(TokenID tokenID, String symbol, String memo, Key newKey,
                                               AccountID accountID) {
        return buildTransaction(builder -> builder.getTokenUpdateBuilder()
                .setAdminKey(newKey)
                .setAutoRenewAccount(accountID)
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(TOKEN_UPDATE_AUTO_RENEW_PERIOD))
                .setExpiry(EXPIRY_TIMESTAMP)
                .setFreezeKey(newKey)
                .setKycKey(newKey)
                .setMemo(StringValue.of(memo))
                .setName(symbol + "_update_name")
                .setSupplyKey(newKey)
                .setSymbol(symbol)
                .setToken(tokenID)
                .setTreasury(accountID)
                .setWipeKey(newKey)
        );
    }

    private Transaction tokenAssociate(List<TokenID> tokenIDs, AccountID accountID) {
        return buildTransaction(builder -> builder.getTokenAssociateBuilder()
                .setAccount(accountID)
                .addAllTokens(tokenIDs));
    }

    private Transaction tokenDissociate(List<TokenID> tokenIDs, AccountID accountID) {
        return buildTransaction(builder -> builder.getTokenDissociateBuilder()
                .setAccount(accountID)
                .addAllTokens(tokenIDs));
    }

    private Transaction tokenDeleteTransaction(TokenID tokenID) {
        return buildTransaction(builder -> builder.getTokenDeletionBuilder()
                .setToken(tokenID));
    }

    private Transaction tokenFreezeTransaction(TokenID tokenID, boolean freeze) {
        Transaction transaction = null;
        if (freeze) {
            transaction = buildTransaction(builder -> builder.getTokenFreezeBuilder()
                    .setToken(tokenID)
                    .setAccount(PAYER));
        } else {
            transaction = buildTransaction(builder -> builder.getTokenUnfreezeBuilder()
                    .setToken(tokenID)
                    .setAccount(PAYER));
        }

        return transaction;
    }

    private Transaction tokenKycTransaction(TokenID tokenID, boolean kyc) {
        Transaction transaction = null;
        if (kyc) {
            transaction = buildTransaction(builder -> builder.getTokenGrantKycBuilder()
                    .setToken(tokenID)
                    .setAccount(PAYER));
        } else {
            transaction = buildTransaction(builder -> builder.getTokenRevokeKycBuilder()
                    .setToken(tokenID)
                    .setAccount(PAYER));
        }

        return transaction;
    }

    private Transaction tokenSupplyTransaction(TokenID tokenID, boolean mint, long amount) {
        Transaction transaction = null;
        if (mint) {
            transaction = buildTransaction(builder -> builder.getTokenMintBuilder()
                    .setToken(tokenID).setAmount(amount));
        } else {
            transaction = buildTransaction(builder -> builder.getTokenBurnBuilder()
                    .setToken(tokenID).setAmount(amount));
        }

        return transaction;
    }

    private Transaction tokenWipeTransaction(TokenID tokenID, long amount) {
        return buildTransaction(builder -> builder.getTokenWipeBuilder()
                .setToken(tokenID)
                .setAccount(PAYER)
                .setAmount(amount));
    }

    private Transaction tokenTransferTransaction() {
        return buildTransaction(builder -> builder.getCryptoTransferBuilder());
    }

    private TransactionRecord createTransactionRecord(long consensusTimestamp, long tokenNum,
                                                      TransactionBody transactionBody,
                                                      ResponseCodeEnum responseCode,
                                                      long newTotalSupply,
                                                      TokenTransferList... tokenTransferLists) {
        var receipt = TransactionReceipt.newBuilder()
                .setStatus(responseCode)
                .setTokenID(TokenID.newBuilder().setShardNum(0).setRealmNum(0).setTokenNum(tokenNum).build())
                .setNewTotalSupply(newTotalSupply);

        return buildTransactionRecord(recordBuilder -> {
                    recordBuilder
                            .setReceipt(receipt)
                            .setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp));

                    if (tokenTransferLists != null) {
                        recordBuilder.addAllTokenTransferLists(Arrays.asList(tokenTransferLists));
                    }

                    recordBuilder.getReceiptBuilder().setAccountID(PAYER);
                },
                transactionBody, responseCode.getNumber());
    }

    private void assertTokenInRepository(TokenID tokenID, boolean present, long createdTimestamp,
                                         long modifiedTimestamp, String symbol, long totalSupply) {
        // clear cache for PgCopy scenarios which don't utilize it
        cacheManager.getCache("tokens").clear();

        Optional<Token> tokenOptional = tokenRepository.findById(new Token.Id(EntityId.of(tokenID)));
        if (present) {
            assertThat(tokenOptional.get())
                    .returns(createdTimestamp, from(Token::getCreatedTimestamp))
                    .returns(modifiedTimestamp, from(Token::getModifiedTimestamp))
                    .returns(symbol, from(Token::getSymbol))
                    .returns(totalSupply, from(Token::getTotalSupply));
        } else {
            assertThat(tokenOptional.isPresent()).isFalse();
        }
    }

    private void assertTokenAccountInRepository(TokenID tokenID, AccountID accountId, boolean present,
                                                long createdTimestamp, long modifiedTimestamp, boolean associated,
                                                TokenFreezeStatusEnum frozenStatus, TokenKycStatusEnum kycStatus) {
        // clear cache for PgCopy scenarios which don't utilize it
        cacheManager.getCache("tokenaccounts").clear();

        Optional<TokenAccount> tokenAccountOptional = tokenAccountRepository
                .findByTokenIdAndAccountId(EntityId.of(tokenID).getId(), EntityId.of(accountId).getId());
        if (present) {
            assertThat(tokenAccountOptional.get())
                    .returns(createdTimestamp, from(TokenAccount::getCreatedTimestamp))
                    .returns(modifiedTimestamp, from(TokenAccount::getModifiedTimestamp))
                    .returns(associated, from(TokenAccount::getAssociated))
                    .returns(frozenStatus, from(TokenAccount::getFreezeStatus))
                    .returns(kycStatus, from(TokenAccount::getKycStatus));
        } else {
            assertThat(tokenAccountOptional.isPresent()).isFalse();
        }
    }

    private void assertTokenTransferInRepository(TokenID tokenID, AccountID accountID, long consensusTimestamp,
                                                 long amount) {
        com.hedera.mirror.importer.domain.TokenTransfer tokenTransfer = tokenTransferRepository
                .findById(new com.hedera.mirror.importer.domain.TokenTransfer.Id(consensusTimestamp, EntityId
                        .of(tokenID), EntityId.of(accountID))).get();
        assertThat(tokenTransfer)
                .returns(amount, from(com.hedera.mirror.importer.domain.TokenTransfer::getAmount));
    }

    private void createTokenEntity(TokenID tokenID, String symbol, long consensusTimestamp, boolean setFreezeKey,
                                   boolean setKycKey) throws InvalidProtocolBufferException {
        Transaction createTransaction = tokenCreateTransaction(setFreezeKey, setKycKey, symbol);
        TransactionBody createTransactionBody = getTransactionBody(createTransaction);
        TokenTransferList tokenTransfer = tokenTransfer(tokenID, PAYER, INITIAL_SUPPLY);
        var createTransactionRecord = createTransactionRecord(consensusTimestamp, tokenID
                .getTokenNum(), createTransactionBody, ResponseCodeEnum.SUCCESS, INITIAL_SUPPLY, tokenTransfer);

        parseRecordItemAndCommit(new RecordItem(createTransaction, createTransactionRecord));
    }

    private void createAndAssociateToken(TokenID tokenID, String symbol, long createTimestamp,
                                         long associateTimestamp, AccountID accountID, boolean setFreezeKey,
                                         boolean setKycKey) throws InvalidProtocolBufferException {
        createTokenEntity(tokenID, symbol, createTimestamp, setFreezeKey, setKycKey);
        assertTokenInRepository(tokenID, true, createTimestamp, createTimestamp, symbol, INITIAL_SUPPLY);

        Transaction associateTransaction = tokenAssociate(List.of(tokenID), accountID);
        insertAndParseTransaction(associateTransaction, associateTimestamp, INITIAL_SUPPLY, null);

        assertTokenAccountInRepository(tokenID, accountID, true, associateTimestamp, associateTimestamp, true,
                setFreezeKey ? TokenFreezeStatusEnum.UNFROZEN : TokenFreezeStatusEnum.NOT_APPLICABLE,
                setKycKey ? TokenKycStatusEnum.REVOKED : TokenKycStatusEnum.NOT_APPLICABLE);
    }

    private TokenTransferList tokenTransfer(TokenID tokenId, AccountID accountId, long amount) {
        return TokenTransferList.newBuilder()
                .setToken(tokenId)
                .addTransfers(AccountAmount.newBuilder().setAccountID(accountId).setAmount(amount).build())
                .build();
    }
}
