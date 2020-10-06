package com.hedera.mirror.importer.parser.record.entity;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.List;
import java.util.Optional;
import javax.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenFreezeStatusEnum;
import com.hedera.mirror.importer.domain.TokenKycStatusEnum;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.repository.TokenAccountRepository;
import com.hedera.mirror.importer.repository.TokenRepository;
import com.hedera.mirror.importer.repository.TokenTransferRepository;

public class EntityRecordItemListenerTokenTest extends AbstractEntityRecordItemListenerTest {
    static final String SYMBOL = "FOOCOIN";
    static final
    Key TOKEN_REF_KEY = keyFromString("0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92");
    private static final
    TokenID TOKEN_ID = TokenID.newBuilder().setShardNum(0).setRealmNum(0).setTokenNum(2).build();
    private static final long CREATE_TIMESTAMP = 1L;
    private static final long ASSOCIATE_TIMESTAMP = 5L;

    @Resource
    protected TokenRepository tokenRepository;
    @Resource
    protected TokenAccountRepository tokenAccountRepository;
    @Resource
    protected TokenTransferRepository tokenTransferRepository;

    @BeforeEach
    void before() {
        entityProperties.getPersist().setTokens(true);
    }

    @Test
    void tokenCreate() throws InvalidProtocolBufferException {
        createTokenEntity(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, false, false);

        // verify entity count
        Entities tokenEntities = getTokenEntity(TOKEN_ID);
        var expectedEntity = createEntity(tokenEntities, null, null, TOKEN_REF_KEY, null, TRANSACTION_MEMO, 1L,
                30L, EntityTypeEnum.TOKEN);
        assertEquals(5, entityRepository.count()); // Node, payer, token and autorenew
        assertThat(getEntity(tokenEntities.getId())).isEqualTo(expectedEntity);

        // verify token
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, CREATE_TIMESTAMP, SYMBOL);
    }

    @Test
    void tokenCreateWithoutPersistence() throws InvalidProtocolBufferException {
        entityProperties.getPersist().setTokens(false);

        createTokenEntity(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, false, false);

        assertTokenInRepository(TOKEN_ID, false, CREATE_TIMESTAMP, CREATE_TIMESTAMP, SYMBOL);
    }

    @Test
    void tokenAssociate() throws InvalidProtocolBufferException {
        createTokenEntity(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, true, true);

        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER);
        insertAndParseTransaction(associateTransaction, ASSOCIATE_TIMESTAMP, null);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, true,
                TokenFreezeStatusEnum.UNFROZEN, TokenKycStatusEnum.REVOKED);
    }

    @Test
    void tokenAssociateWithMissingToken() throws InvalidProtocolBufferException {
        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER);
        insertAndParseTransaction(associateTransaction, ASSOCIATE_TIMESTAMP, null);

        // verify token account was not created
        assertTokenAccountInRepository(TOKEN_ID, PAYER, false, CREATE_TIMESTAMP, CREATE_TIMESTAMP, true,
                TokenFreezeStatusEnum.UNFROZEN, TokenKycStatusEnum.REVOKED);
    }

    @Test
    void tokenDissociate() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, PAYER, false, false);

        Transaction dissociateTransaction = tokenDissociate(List.of(TOKEN_ID), PAYER);
        long dissociateTimeStamp = 10L;
        insertAndParseTransaction(dissociateTransaction, dissociateTimeStamp, null);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, dissociateTimeStamp, false,
                TokenFreezeStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.NOT_APPLICABLE);
    }

    @Test
    void tokenDelete() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, PAYER, false, false);

        // delete token
        Transaction deleteTransaction = tokenDeleteTransaction(TOKEN_ID);
        insertAndParseTransaction(deleteTransaction, 10L, null);

        Entities tokenEntities = getTokenEntity(TOKEN_ID);
        var expectedEntity = createEntity(tokenEntities, null, null, TOKEN_REF_KEY, null, TRANSACTION_MEMO, 1L,
                30L, EntityTypeEnum.TOKEN);

        expectedEntity.setDeleted(true);
        assertThat(getEntity(tokenEntities.getId())).isEqualTo(expectedEntity);

        assertEquals(5, entityRepository.count());
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, CREATE_TIMESTAMP, SYMBOL);
    }

    @Test
    void tokenUpdate() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, PAYER, false, false);

        String newSymbol = "NEWSYMBOL";
        Transaction transaction = tokenUpdateTransaction(
                TOKEN_ID,
                newSymbol,
                keyFromString("updated-key"),
                AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(2002).build());
        long updateTimeStamp = 10L;
        insertAndParseTransaction(transaction, updateTimeStamp, null);

        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, updateTimeStamp, newSymbol);
    }

    @Test
    void tokenUpdateWithMissingToken() throws InvalidProtocolBufferException {
        String newSymbol = "NEWSYMBOL";
        Transaction transaction = tokenUpdateTransaction(
                TOKEN_ID,
                newSymbol,
                keyFromString("updated-key"),
                AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(2002).build());
        insertAndParseTransaction(transaction, 10L, null);

        // verify token was not created when missing
        assertTokenInRepository(TOKEN_ID, false, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, SYMBOL);
    }

    @Test
    void tokenAccountFreeze() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, PAYER, true, false);

        Transaction transaction = tokenFreezeTransaction(TOKEN_ID, true);
        long freezeTimeStamp = 15L;
        insertAndParseTransaction(transaction, freezeTimeStamp, null);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, freezeTimeStamp, true,
                TokenFreezeStatusEnum.FROZEN, TokenKycStatusEnum.NOT_APPLICABLE);
    }

    @Test
    void tokenAccountUnfreeze() throws InvalidProtocolBufferException {
        // create token with freeze default
        createTokenEntity(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, true, false);

        // associate account
        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER);
        insertAndParseTransaction(associateTransaction, ASSOCIATE_TIMESTAMP, null);

        Transaction freezeTransaction = tokenFreezeTransaction(TOKEN_ID, true);
        long freezeTimeStamp = 10L;
        insertAndParseTransaction(freezeTransaction, freezeTimeStamp, null);

        // unfreeze
        Transaction unfreezeTransaction = tokenFreezeTransaction(TOKEN_ID, false);
        long unfreezeTimeStamp = 444;
        insertAndParseTransaction(unfreezeTransaction, unfreezeTimeStamp, null);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, unfreezeTimeStamp, true,
                TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.NOT_APPLICABLE);
    }

    @Test
    void tokenAccountGrantKyc() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, PAYER, false, true);

        Transaction transaction = tokenKycTransaction(TOKEN_ID, true);
        long grantTimeStamp = 10L;
        insertAndParseTransaction(transaction, grantTimeStamp, null);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, grantTimeStamp, true,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.GRANTED);
    }

    @Test
    void tokenAccountGrantKycWithMissingTokenAccount() throws InvalidProtocolBufferException {
        createTokenEntity(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, false, true);

        Transaction transaction = tokenKycTransaction(TOKEN_ID, true);
        long grantTimeStamp = 10L;
        insertAndParseTransaction(transaction, grantTimeStamp, null);

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
        insertAndParseTransaction(associateTransaction, ASSOCIATE_TIMESTAMP, null);

        Transaction grantTransaction = tokenKycTransaction(TOKEN_ID, true);
        long grantTimeStamp = 10L;
        insertAndParseTransaction(grantTransaction, grantTimeStamp, null);

        // revoke
        Transaction revokeTransaction = tokenKycTransaction(TOKEN_ID, false);
        long revokeTimeStamp = 333;
        insertAndParseTransaction(revokeTransaction, revokeTimeStamp, null);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, revokeTimeStamp, true,
                TokenFreezeStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.REVOKED);
    }

    @Test
    void tokenBurn() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, PAYER, false, false);

        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, false);
        insertAndParseTransaction(transaction, 10L, null);
    }

    @Test
    void tokenMint() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, PAYER, false, false);

        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, true);
        insertAndParseTransaction(transaction, 10L, null);
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
        insertAndParseTransaction(transaction, transferTimeStamp, List.of(transferList1, transferList2));

        assertTokenTransferInRepository(TOKEN_ID, PAYER, transferTimeStamp, -1000);
        assertTokenTransferInRepository(TOKEN_ID, accountId, transferTimeStamp, 1000);
        assertTokenTransferInRepository(tokenID2, PAYER, transferTimeStamp, 333);
        assertTokenTransferInRepository(tokenID2, accountId, transferTimeStamp, -333);
    }

    @Test
    void tokenWipe() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, PAYER, false, false);

        Transaction transaction = tokenWipeTransaction(TOKEN_ID);
        long wipeTimestamp = 10L;
        insertAndParseTransaction(transaction, wipeTimestamp, null);

        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, wipeTimestamp, SYMBOL);
    }

    @Test
    void tokenWipeWithMissingToken() throws InvalidProtocolBufferException {
        Transaction transaction = tokenWipeTransaction(TOKEN_ID);
        insertAndParseTransaction(transaction, 10L, null);

        assertTokenInRepository(TOKEN_ID, false, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, SYMBOL);
    }

    private void insertAndParseTransaction(Transaction transaction, long timeStamp, List tokenTransferLists) throws InvalidProtocolBufferException {
        TransactionBody transactionBody = getTransactionBody(transaction);

        var transactionRecord = createTransactionRecord(timeStamp, TOKEN_ID
                .getTokenNum(), transactionBody, ResponseCodeEnum.SUCCESS, tokenTransferLists);

        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));
        assertTransactionInRepository(ResponseCodeEnum.SUCCESS, timeStamp, null);
    }

    private Transaction tokenCreateTransaction(boolean setFreezeKey, boolean setKycKey, String symbol) {
        return buildTransaction(builder -> {
            builder.getTokenCreationBuilder()
                    .setAdminKey(TOKEN_REF_KEY)
                    .setDecimals(1000)
                    .setExpiry(360)
                    .setInitialSupply(1_000_000L)
                    .setFreezeDefault(false)
                    .setSupplyKey(TOKEN_REF_KEY)
                    .setSymbol(symbol)
                    .setTreasury(PAYER)
                    .setAutoRenewAccount(PAYER)
                    .setAutoRenewPeriod(100)
                    .setName(symbol + "_token_name")
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

    private Transaction tokenUpdateTransaction(TokenID tokenID, String symbol, Key newKey, AccountID accountID) {
        return buildTransaction(builder -> builder.getTokenUpdateBuilder()
                .setToken(tokenID)
                .setAdminKey(newKey)
                .setKycKey(newKey)
                .setSupplyKey(newKey)
                .setSymbol(symbol)
                .setName(symbol + "_update_name")
                .setTreasury(accountID)
                .setAutoRenewAccount(accountID)
                .setAutoRenewPeriod(12)
                .setExpiry(360)
                .setFreezeKey(newKey)
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

    private Transaction tokenSupplyTransaction(TokenID tokenID, boolean mint) {
        Transaction transaction = null;
        if (mint) {
            transaction = buildTransaction(builder -> builder.getTokenMintBuilder()
                    .setToken(tokenID));
        } else {
            transaction = buildTransaction(builder -> builder.getTokenBurnBuilder()
                    .setToken(tokenID));
        }

        return transaction;
    }

    private Transaction tokenWipeTransaction(TokenID tokenID) {
        return buildTransaction(builder -> builder.getTokenWipeBuilder()
                .setToken(tokenID)
                .setAccount(PAYER));
    }

    private Transaction tokenTransferTransaction() {
        return buildTransaction(builder -> builder.getTokenTransfersBuilder());
    }

    private TransactionRecord createTransactionRecord(long consensusTimestamp, long tokenNum,
                                                      TransactionBody transactionBody,
                                                      ResponseCodeEnum responseCode,
                                                      List<TokenTransferList> tokenTransferLists) {
        var receipt = TransactionReceipt.newBuilder()
                .setStatus(responseCode)
                .setTokenId(TokenID.newBuilder().setShardNum(0).setRealmNum(0).setTokenNum(tokenNum).build());

        return buildTransactionRecord(recordBuilder -> {
                    recordBuilder
                            .setReceipt(receipt)
                            .setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp));

                    if (tokenTransferLists != null) {
                        recordBuilder.addAllTokenTransferLists(tokenTransferLists);
                    }

                    recordBuilder.getReceiptBuilder().setAccountID(PAYER);
                },
                transactionBody, responseCode.getNumber());
    }

    private void assertTokenInRepository(TokenID tokenID, boolean present, long createdTimestamp,
                                         long modifiedTimestamp, String symbol) {
        Optional<Token> tokenOptional = tokenRepository.findById(new Token.Id(EntityId.of(tokenID)));
        if (present) {
            assertThat(tokenOptional.get())
                    .returns(createdTimestamp, from(Token::getCreatedTimestamp))
                    .returns(modifiedTimestamp, from(Token::getModifiedTimestamp))
                    .returns(symbol, from(Token::getSymbol));
        } else {
            assertThat(tokenOptional.isPresent()).isFalse();
        }
    }

    private void assertTokenAccountInRepository(TokenID tokenID, AccountID accountId, boolean present,
                                                long createdTimestamp, long modifiedTimestamp, boolean associated,
                                                TokenFreezeStatusEnum frozenStatus, TokenKycStatusEnum kycStatus) {
        Optional<TokenAccount> tokenAccountOptional = tokenAccountRepository
                .findByTokenIdAndAccountId(EntityId.of(tokenID).getId(), EntityId.of(accountId).getId());
        if (present) {
            assertThat(tokenAccountOptional.get())
                    .returns(createdTimestamp, from(TokenAccount::getCreatedTimestamp))
                    .returns(modifiedTimestamp, from(TokenAccount::getModifiedTimestamp))
                    .returns(associated, from(TokenAccount::isAssociated))
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

    private Entities getTokenEntity(TokenID tokenID) {
        return getEntity(EntityId.of(tokenID).getId());
    }

    private void createTokenEntity(TokenID tokenID, String symbol, long consensusTimestamp, boolean setFreezeKey,
                                   boolean setKycKey) throws InvalidProtocolBufferException {
        Transaction createTransaction = tokenCreateTransaction(setFreezeKey, setKycKey, symbol);
        TransactionBody createTransactionBody = getTransactionBody(createTransaction);
        var createTransactionRecord = createTransactionRecord(consensusTimestamp, tokenID
                .getTokenNum(), createTransactionBody, ResponseCodeEnum.SUCCESS, null);

        parseRecordItemAndCommit(new RecordItem(createTransaction, createTransactionRecord));
    }

    private void createAndAssociateToken(TokenID tokenID, String symbol, long createTimestamp,
                                         long associateTimestamp, AccountID accountID, boolean setFreezeKey,
                                         boolean setKycKey) throws InvalidProtocolBufferException {
        createTokenEntity(tokenID, symbol, createTimestamp, setFreezeKey, setKycKey);
        assertTokenInRepository(tokenID, true, createTimestamp, createTimestamp, symbol);

        Transaction associateTransaction = tokenAssociate(List.of(tokenID), accountID);
        insertAndParseTransaction(associateTransaction, associateTimestamp, null);

        assertTokenAccountInRepository(tokenID, accountID, true, associateTimestamp, associateTimestamp, true,
                setFreezeKey ? TokenFreezeStatusEnum.UNFROZEN : TokenFreezeStatusEnum.NOT_APPLICABLE,
                setKycKey ? TokenKycStatusEnum.REVOKED : TokenKycStatusEnum.NOT_APPLICABLE);
    }
}
