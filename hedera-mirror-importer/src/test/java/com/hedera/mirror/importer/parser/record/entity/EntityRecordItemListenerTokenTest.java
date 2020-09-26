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
import com.hederahashgraph.api.proto.java.TokenRef;
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
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.repository.TokenAccountRepository;
import com.hedera.mirror.importer.repository.TokenRepository;
import com.hedera.mirror.importer.repository.TokenTransferRepository;

public class EntityRecordItemListenerTokenTest extends AbstractEntityRecordItemListenerTest {
    //    private static final AccountID accountId = AccountID.newBuilder().setShardNum(0).setRealmNum(0)
    //    .setAccountNum(1001)
//            .build();
    static final String SYMBOL = "FOOCOIN";
    static final
    Key tokenRefKey = keyFromString("0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92");
    private static final
    TokenID tokenID = TokenID.newBuilder().setShardNum(0).setRealmNum(0).setTokenNum(2).build();

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
        createTokenEntityVerification(tokenID, SYMBOL, 1L, 5);
        assertTokenInRepository(tokenID, true, 1L, 1L, SYMBOL);
    }

    @Test
    void tokenCreateWithoutPersistence() throws InvalidProtocolBufferException {
        entityProperties.getPersist().setTokens(false);

        createTokenEntityVerification(tokenID, SYMBOL, 1L, 5);
        assertTokenInRepository(tokenID, false, 1L, 1L, SYMBOL);
    }

    @Test
    void tokenDelete() throws InvalidProtocolBufferException {
        createTokenEntityVerification(tokenID, SYMBOL, 1L, 5);

        // delete token
        Transaction deleteTransaction = tokenDeleteTransaction(tokenID, SYMBOL);
        long transferTimeStamp = 5L;
        insertAndParseTransaction(deleteTransaction, transferTimeStamp, null);

        Entities tokenEntities = getTokenEntity(tokenID);
        var expectedEntity = createEntity(tokenEntities, null, null, tokenRefKey, null, TRANSACTION_MEMO, 1L,
                30L, EntityTypeEnum.TOKEN);

        expectedEntity.setDeleted(true);
        assertThat(getEntity(tokenEntities.getId())).isEqualTo(expectedEntity);

        assertEquals(5, entityRepository.count());
        assertTokenInRepository(tokenID, true, 1L, transferTimeStamp, SYMBOL);
    }

    @Test
    void tokenAccountFreeze() throws InvalidProtocolBufferException {
        createTokenEntityVerification(tokenID, SYMBOL, 1L, 5);

        Transaction transaction = tokenFreezeTransaction(tokenID, SYMBOL, true);
        long transferTimeStamp = 10L;
        insertAndParseTransaction(transaction, transferTimeStamp, null);

        assertTokenAccountInRepository(tokenID, PAYER, true, transferTimeStamp, transferTimeStamp, true, true, false,
                false);
    }

    @Test
    void tokenAccountUnfreeze() throws InvalidProtocolBufferException {
        createTokenEntityVerification(tokenID, SYMBOL, 1L, 5);

        // freeze first
        Transaction freezeTransaction = tokenFreezeTransaction(tokenID, SYMBOL, true);
        long freezeTimeStamp = 10L;
        insertAndParseTransaction(freezeTransaction, freezeTimeStamp, null);

        assertTokenAccountInRepository(tokenID, PAYER, true, freezeTimeStamp, freezeTimeStamp, true, true, false,
                false);

        // unfreeze
        Transaction unfreezeTransaction = tokenFreezeTransaction(tokenID, SYMBOL, false);
        long unfreezeTimeStamp = 444;
        insertAndParseTransaction(unfreezeTransaction, unfreezeTimeStamp, null);

        assertTokenAccountInRepository(tokenID, PAYER, true, freezeTimeStamp, unfreezeTimeStamp, true, false, false,
                false);
    }

    @Test
    void tokenAccountGrantKyc() throws InvalidProtocolBufferException {
        createTokenEntityVerification(tokenID, SYMBOL, 1L, 5);

        Transaction transaction = tokenKycTransaction(tokenID, SYMBOL, true);
        long transferTimeStamp = 10L;
        insertAndParseTransaction(transaction, transferTimeStamp, null);

        assertTokenAccountInRepository(tokenID, PAYER, true, transferTimeStamp, transferTimeStamp, true, false, true,
                false);
    }

    @Test
    void tokenAccountRevokeKyc() throws InvalidProtocolBufferException {
        createTokenEntityVerification(tokenID, SYMBOL, 1L, 5);

        // grant first
        Transaction grantTransaction = tokenKycTransaction(tokenID, SYMBOL, true);
        long grantTimeStamp = 10L;
        insertAndParseTransaction(grantTransaction, grantTimeStamp, null);

        assertTokenAccountInRepository(tokenID, PAYER, true, grantTimeStamp, grantTimeStamp, true, false, true,
                false);

        // revoke
        Transaction revokeTransaction = tokenKycTransaction(tokenID, SYMBOL, false);
        long revokeTimeStamp = 333;
        insertAndParseTransaction(revokeTransaction, revokeTimeStamp, null);

        assertTokenAccountInRepository(tokenID, PAYER, true, grantTimeStamp, revokeTimeStamp, true, false, false,
                false);
    }

    @Test
    void tokenBurn() throws InvalidProtocolBufferException {
        createTokenEntityVerification(tokenID, SYMBOL, 1L, 5);

        Transaction transaction = tokenSupplyTransaction(tokenID, SYMBOL, false);
        long transferTimeStamp = 10L;
        insertAndParseTransaction(transaction, transferTimeStamp, null);
    }

    @Test
    void tokenMint() throws InvalidProtocolBufferException {
        createTokenEntityVerification(tokenID, SYMBOL, 1L, 5);

        Transaction transaction = tokenSupplyTransaction(tokenID, SYMBOL, true);
        long transferTimeStamp = 10L;
        insertAndParseTransaction(transaction, transferTimeStamp, null);
    }

    @Test
    void tokenTransfer() throws InvalidProtocolBufferException {
        createTokenEntityVerification(tokenID, SYMBOL, 1L, 5);
        TokenID tokenID2 = TokenID.newBuilder().setShardNum(0).setRealmNum(0).setTokenNum(7).build();
        String symbol2 = "MIRROR";
        createTokenEntityVerification(tokenID2, symbol2, 5L, 6);

        AccountID accountId = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(1).build();

        // token transfer
        Transaction transaction = tokenTransferTransaction();

        TokenTransferList transferList1 = TokenTransferList.newBuilder()
                .setToken(tokenID)
                .addTransfers(AccountAmount.newBuilder().setAccountID(PAYER).setAmount(-1000).build())
                .addTransfers(AccountAmount.newBuilder().setAccountID(accountId).setAmount(1000).build())
                .build();
        TokenTransferList transferList2 = TokenTransferList.newBuilder()
                .setToken(tokenID2)
                .addTransfers(AccountAmount.newBuilder().setAccountID(PAYER).setAmount(333).build())
                .addTransfers(AccountAmount.newBuilder().setAccountID(accountId).setAmount(-333).build())
                .build();

        long transferTimeStamp = 10L;
        insertAndParseTransaction(transaction, transferTimeStamp, List.of(transferList1, transferList2));

        assertTokenTransferInRepository(tokenID, PAYER, transferTimeStamp, -1000);
        assertTokenTransferInRepository(tokenID, accountId, transferTimeStamp, 1000);
        assertTokenTransferInRepository(tokenID2, PAYER, transferTimeStamp, 333);
        assertTokenTransferInRepository(tokenID2, accountId, transferTimeStamp, -333);
    }

    @Test
    void tokenWipe() throws InvalidProtocolBufferException {
        createTokenEntityVerification(tokenID, SYMBOL, 1L, 5);

        Transaction transaction = tokenWipeTransaction(tokenID, SYMBOL);
        long wipeTimeStamp = 10L;
        insertAndParseTransaction(transaction, wipeTimeStamp, null);

        assertTokenAccountInRepository(tokenID, PAYER, true, wipeTimeStamp, wipeTimeStamp, true, false, false,
                true);
    }

    private void insertAndParseTransaction(Transaction transaction, long transferTimeStamp, List tokenTransferLists) throws InvalidProtocolBufferException {
        TransactionBody transactionBody = getTransactionBody(transaction);

        var transactionRecord = createTransactionRecord(transferTimeStamp, tokenID
                .getTokenNum(), transactionBody, ResponseCodeEnum.SUCCESS, tokenTransferLists);

        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));
        assertTransactionInRepository(ResponseCodeEnum.SUCCESS, transferTimeStamp, null);
    }

    private Transaction tokenCreateTransaction(Key key, String symbol) {
        return buildTransaction(builder -> builder.getTokenCreationBuilder()
                .setAdminKey(key)
                .setDivisibility(1000)
                .setFloat(1_000_000L)
                .setFreezeDefault(false)
                .setFreezeKey(key)
                .setKycDefault(false)
                .setKycKey(key)
                .setSupplyKey(key)
                .setSymbol(symbol)
                .setTreasury(PAYER)
                .setWipeKey(key));
    }

    private Transaction tokenDeleteTransaction(TokenID tokenID, String symbol) {
        return buildTransaction(builder -> builder.getTokenDeletionBuilder()
                .setToken(TokenRef.newBuilder().setTokenId(tokenID).setSymbol(symbol).build()));
    }

    private Transaction tokenFreezeTransaction(TokenID tokenID, String symbol, boolean freeze) {
        TokenRef tokenRef = TokenRef.newBuilder().setTokenId(tokenID).setSymbol(symbol).build();
        Transaction transaction = null;
        if (freeze) {
            transaction = buildTransaction(builder -> builder.getTokenFreezeBuilder()
                    .setToken(tokenRef)
                    .setAccount(PAYER));
        } else {
            transaction = buildTransaction(builder -> builder.getTokenUnfreezeBuilder()
                    .setToken(tokenRef)
                    .setAccount(PAYER));
        }

        return transaction;
    }

    private Transaction tokenKycTransaction(TokenID tokenID, String symbol, boolean kyc) {
        TokenRef tokenRef = TokenRef.newBuilder().setTokenId(tokenID).setSymbol(symbol).build();
        Transaction transaction = null;
        if (kyc) {
            transaction = buildTransaction(builder -> builder.getTokenGrantKycBuilder()
                    .setToken(tokenRef)
                    .setAccount(PAYER));
        } else {
            transaction = buildTransaction(builder -> builder.getTokenRevokeKycBuilder()
                    .setToken(tokenRef)
                    .setAccount(PAYER));
        }

        return transaction;
    }

    private Transaction tokenSupplyTransaction(TokenID tokenID, String symbol, boolean mint) {
        TokenRef tokenRef = TokenRef.newBuilder().setTokenId(tokenID).setSymbol(symbol).build();
        Transaction transaction = null;
        if (mint) {
            transaction = buildTransaction(builder -> builder.getTokenMintBuilder()
                    .setToken(tokenRef));
        } else {
            transaction = buildTransaction(builder -> builder.getTokenBurnBuilder()
                    .setToken(tokenRef));
        }

        return transaction;
    }

    private Transaction tokenWipeTransaction(TokenID tokenID, String symbol) {
        TokenRef tokenRef = TokenRef.newBuilder().setTokenId(tokenID).setSymbol(symbol).build();
        return buildTransaction(builder -> builder.getTokenWipeBuilder()
                .setToken(tokenRef)
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
                                                boolean frozen, boolean kyc, boolean wiped) {
        Optional<TokenAccount> tokenAccountOptional = tokenAccountRepository
                .findByTokenIdAndAccountId(EntityId.of(tokenID), EntityId.of(accountId));
        if (present) {
            assertThat(tokenAccountOptional.get())
//                    .returns(createdTimestamp, from(TokenAccount::getCreatedTimestamp))
                    .returns(modifiedTimestamp, from(TokenAccount::getModifiedTimestamp))
                    .returns(associated, from(TokenAccount::isAssociated))
                    .returns(frozen, from(TokenAccount::isFrozen))
                    .returns(kyc, from(TokenAccount::isKyc))
                    .returns(wiped, from(TokenAccount::isWiped));
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

    private void createTokenEntityVerification(TokenID tokenID, String symbol, long consensusTimestamp,
                                               int entityCount) throws InvalidProtocolBufferException {
        Transaction createTransaction = tokenCreateTransaction(tokenRefKey, symbol);
        TransactionBody createTransactionBody = getTransactionBody(createTransaction);
        var createTransactionRecord = createTransactionRecord(consensusTimestamp, tokenID
                .getTokenNum(), createTransactionBody, ResponseCodeEnum.SUCCESS, null);

        parseRecordItemAndCommit(new RecordItem(createTransaction, createTransactionRecord));

        Entities tokenEntities = getTokenEntity(tokenID);
        var expectedEntity = createEntity(tokenEntities, null, null, tokenRefKey, null, TRANSACTION_MEMO, 1L,
                30L, EntityTypeEnum.TOKEN);
        assertEquals(entityCount, entityRepository.count()); // Node, payer, token and autorenew
        assertThat(getEntity(tokenEntities.getId())).isEqualTo(expectedEntity);
    }
}
