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

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.StringValue;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.Nft;
import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenFreezeStatusEnum;
import com.hedera.mirror.importer.domain.TokenKycStatusEnum;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.repository.NftRepository;
import com.hedera.mirror.importer.repository.NftTransferRepository;
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
    private static final String METADATA = "METADATA";
    private static final long SERIAL_NUMBER_1 = 1L;
    private static final long SERIAL_NUMBER_2 = 2L;
    private static final List SERIAL_NUMBER_LIST = Arrays.asList(SERIAL_NUMBER_1, SERIAL_NUMBER_2);
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

    @Resource
    protected NftRepository nftRepository;

    @Resource
    protected NftTransferRepository nftTransferRepository;

    @BeforeEach
    void before() {
        entityProperties.getPersist().setTokens(true);
    }

    @Test
    void tokenCreate() throws InvalidProtocolBufferException {
        createTokenEntity(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, false, false);

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

        createTokenEntity(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, false, false);

        assertTokenInRepository(TOKEN_ID, false, CREATE_TIMESTAMP, CREATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
        assertThat(tokenTransferRepository.count()).isZero();
    }

    @Test
    void tokenCreateWithNfts() throws InvalidProtocolBufferException {
        createTokenEntity(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP, false, false);

        Entity expected = createEntity(EntityId.of(TOKEN_ID), TOKEN_REF_KEY, EntityId.of(PAYER), AUTO_RENEW_PERIOD,
                false, EXPIRY_NS, TOKEN_CREATE_MEMO, null);
        assertEquals(4, entityRepository.count()); // Node, payer, token and autorenew
        assertEntity(expected);

        // verify token
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, CREATE_TIMESTAMP, SYMBOL, 0);
    }

    @Test
    void tokenAssociate() throws InvalidProtocolBufferException {
        createTokenEntity(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, true, true);

        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER);
        insertAndParseTransaction(associateTransaction, ASSOCIATE_TIMESTAMP, INITIAL_SUPPLY, null);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, true,
                TokenFreezeStatusEnum.UNFROZEN, TokenKycStatusEnum.REVOKED);
    }

    @Test
    void tokenAssociateWithMissingToken() throws InvalidProtocolBufferException {
        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER);
        insertAndParseTransaction(associateTransaction, ASSOCIATE_TIMESTAMP, INITIAL_SUPPLY, null);

        // verify token account was not created
        assertTokenAccountInRepository(TOKEN_ID, PAYER, false, CREATE_TIMESTAMP, CREATE_TIMESTAMP, true,
                TokenFreezeStatusEnum.UNFROZEN, TokenKycStatusEnum.REVOKED);
    }

    @Test
    void tokenDissociate() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, INITIAL_SUPPLY);

        Transaction dissociateTransaction = tokenDissociate(List.of(TOKEN_ID), PAYER);
        long dissociateTimeStamp = 10L;
        insertAndParseTransaction(dissociateTransaction, dissociateTimeStamp, INITIAL_SUPPLY, null);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, dissociateTimeStamp, false,
                TokenFreezeStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.NOT_APPLICABLE);
    }

    @Test
    void tokenDelete() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, INITIAL_SUPPLY);

        // delete token
        Transaction deleteTransaction = tokenDeleteTransaction(TOKEN_ID);
        insertAndParseTransaction(deleteTransaction, 10L, INITIAL_SUPPLY, null);

        Entity expected = createEntity(EntityId.of(TOKEN_ID), TOKEN_REF_KEY, EntityId.of(PAYER), AUTO_RENEW_PERIOD,
                true, EXPIRY_NS, TOKEN_CREATE_MEMO, null);
        assertEquals(4, entityRepository.count()); // Node, payer, token and autorenew
        assertEntity(expected);

        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, CREATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
    }

    @Test
    void tokenUpdate() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, INITIAL_SUPPLY);

        String newSymbol = "NEWSYMBOL";
        Transaction transaction = tokenUpdateTransaction(
                TOKEN_ID,
                newSymbol,
                TOKEN_UPDATE_MEMO,
                TOKEN_UPDATE_REF_KEY,
                PAYER2);
        long updateTimeStamp = 10L;
        insertAndParseTransaction(transaction, updateTimeStamp, INITIAL_SUPPLY, null);

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
        insertAndParseTransaction(transaction, 10L, INITIAL_SUPPLY, null);

        // verify token was not created when missing
        assertTokenInRepository(TOKEN_ID, false, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
    }

    @Test
    void tokenAccountFreeze() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, true, false, INITIAL_SUPPLY);

        Transaction transaction = tokenFreezeTransaction(TOKEN_ID, true);
        long freezeTimeStamp = 15L;
        insertAndParseTransaction(transaction, freezeTimeStamp, INITIAL_SUPPLY, null);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, freezeTimeStamp, true,
                TokenFreezeStatusEnum.FROZEN, TokenKycStatusEnum.NOT_APPLICABLE);
    }

    @Test
    void tokenAccountUnfreeze() throws InvalidProtocolBufferException {
        // create token with freeze default
        createTokenEntity(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, true, false);

        // associate account
        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER);
        insertAndParseTransaction(associateTransaction, ASSOCIATE_TIMESTAMP, INITIAL_SUPPLY, null);

        Transaction freezeTransaction = tokenFreezeTransaction(TOKEN_ID, true);
        long freezeTimeStamp = 10L;
        insertAndParseTransaction(freezeTransaction, freezeTimeStamp, INITIAL_SUPPLY, null);

        // unfreeze
        Transaction unfreezeTransaction = tokenFreezeTransaction(TOKEN_ID, false);
        long unfreezeTimeStamp = 444;
        insertAndParseTransaction(unfreezeTransaction, unfreezeTimeStamp, INITIAL_SUPPLY, null);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, unfreezeTimeStamp, true,
                TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.NOT_APPLICABLE);
    }

    @Test
    void tokenAccountGrantKyc() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, true, INITIAL_SUPPLY);

        Transaction transaction = tokenKycTransaction(TOKEN_ID, true);
        long grantTimeStamp = 10L;
        insertAndParseTransaction(transaction, grantTimeStamp, INITIAL_SUPPLY, null);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, grantTimeStamp, true,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.GRANTED);
    }

    @Test
    void tokenAccountGrantKycWithMissingTokenAccount() throws InvalidProtocolBufferException {
        createTokenEntity(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, false, true);

        Transaction transaction = tokenKycTransaction(TOKEN_ID, true);
        long grantTimeStamp = 10L;
        insertAndParseTransaction(transaction, grantTimeStamp, INITIAL_SUPPLY, null);

        // verify token account was not created when missing
        assertTokenAccountInRepository(TOKEN_ID, PAYER, false, ASSOCIATE_TIMESTAMP, grantTimeStamp, true,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.GRANTED);
    }

    @Test
    void tokenAccountRevokeKyc() throws InvalidProtocolBufferException {
        // create token with kyc revoked
        createTokenEntity(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, false, true);

        // associate account
        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER);
        insertAndParseTransaction(associateTransaction, ASSOCIATE_TIMESTAMP, INITIAL_SUPPLY, null);

        Transaction grantTransaction = tokenKycTransaction(TOKEN_ID, true);
        long grantTimeStamp = 10L;
        insertAndParseTransaction(grantTransaction, grantTimeStamp, INITIAL_SUPPLY, null);

        // revoke
        Transaction revokeTransaction = tokenKycTransaction(TOKEN_ID, false);
        long revokeTimeStamp = 333;
        insertAndParseTransaction(revokeTransaction, revokeTimeStamp, INITIAL_SUPPLY, null);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, revokeTimeStamp, true,
                TokenFreezeStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.REVOKED);
    }

    @Test
    void tokenBurn() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, INITIAL_SUPPLY);

        long amount = -1000;
        long burnTimestamp = 10L;
        TokenTransferList tokenTransfer = tokenTransfer(TOKEN_ID, PAYER, amount);
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, TokenType.FUNGIBLE_COMMON, false, amount, null);
        insertAndParseTransaction(transaction, burnTimestamp, INITIAL_SUPPLY - amount, null, tokenTransfer);

        // Verify
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, burnTimestamp, amount);
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, burnTimestamp, SYMBOL, INITIAL_SUPPLY - amount);
    }

    @Test
    void tokenBurnNft() throws InvalidProtocolBufferException {
        //TODO Need to understand structure of NftTransfer for mint/burn NFTs to add transfer checks.
        createAndAssociateToken(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, 0L);

        long mintTimestamp = 10L;
        Transaction mintTransaction = tokenSupplyTransaction(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, true, 0,
                SERIAL_NUMBER_LIST);

        insertAndParseTransaction(mintTransaction, mintTimestamp, SERIAL_NUMBER_LIST.size(), SERIAL_NUMBER_LIST);

        long burnTimestamp = 15L;
        Transaction burnTransaction = tokenSupplyTransaction(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, false, 0, Arrays
                .asList(SERIAL_NUMBER_1));
        insertAndParseTransaction(burnTransaction, burnTimestamp, 0, null);

        // Verify
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, burnTimestamp, SYMBOL, 0);
        assertNftInRepository(TOKEN_ID, 1L, true, mintTimestamp, burnTimestamp, METADATA.getBytes(), EntityId
                .of(PAYER), true);
        assertNftInRepository(TOKEN_ID, 2L, true, mintTimestamp, mintTimestamp, METADATA.getBytes(), EntityId
                .of(PAYER), false);
    }

    @Test
    void tokenBurnNftMissingNft() throws InvalidProtocolBufferException {
        //TODO Need to understand structure of NftTransfer for mint/burn NFTs to add transfer checks.
        createAndAssociateToken(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, 0L);

        long mintTimestamp = 10L;
        Transaction mintTransaction = tokenSupplyTransaction(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, true, 0,
                Arrays.asList(SERIAL_NUMBER_2));

        insertAndParseTransaction(mintTransaction, mintTimestamp, SERIAL_NUMBER_LIST.size(), Arrays
                .asList(SERIAL_NUMBER_2));

        long burnTimestamp = 15L;
        Transaction burnTransaction = tokenSupplyTransaction(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, false, 0, Arrays
                .asList(SERIAL_NUMBER_1));
        insertAndParseTransaction(burnTransaction, burnTimestamp, 0, null);

        // Verify
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, burnTimestamp, SYMBOL, 0);
        assertNftInRepository(TOKEN_ID, SERIAL_NUMBER_1, false, mintTimestamp, burnTimestamp, METADATA
                .getBytes(), EntityId.of(PAYER), true);
        assertNftInRepository(TOKEN_ID, SERIAL_NUMBER_2, true, mintTimestamp, mintTimestamp, METADATA
                .getBytes(), EntityId.of(PAYER), false);
    }

    @Test
    void tokenMint() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, INITIAL_SUPPLY);

        long amount = 1000;
        long mintTimestamp = 10L;
        TokenTransferList tokenTransfer = tokenTransfer(TOKEN_ID, PAYER, amount);
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, TokenType.FUNGIBLE_COMMON, true, amount, null);
        insertAndParseTransaction(transaction, mintTimestamp, INITIAL_SUPPLY + amount, null, tokenTransfer);

        // Verify
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, mintTimestamp, amount);
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, mintTimestamp, SYMBOL, INITIAL_SUPPLY + amount);
    }

    @Test
    void tokenMintNfts() throws InvalidProtocolBufferException {
        //TODO Need to understand structure of NftTransfer for create/delete NFTs to further test.
        createAndAssociateToken(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER, false, false, 0);

        long mintTimestamp = 10L;
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, true, 0, Arrays
                .asList(1L));

        // Verify
        insertAndParseTransaction(transaction, mintTimestamp, 2, SERIAL_NUMBER_LIST);
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, mintTimestamp, SYMBOL, 2);
        assertNftInRepository(TOKEN_ID, SERIAL_NUMBER_1, true, mintTimestamp, mintTimestamp, METADATA
                .getBytes(), EntityId.of(PAYER), false);
        assertNftInRepository(TOKEN_ID, SERIAL_NUMBER_2, true, mintTimestamp, mintTimestamp, METADATA
                .getBytes(), EntityId.of(PAYER), false);
    }

    @Test
    void tokenMintNftsMissingToken() throws InvalidProtocolBufferException {
        //TODO Need to understand structure of NftTransfer for create/delete NFTs to further test.
        long mintTimestamp = 10L;
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, true, 2,
                SERIAL_NUMBER_LIST);

        // Verify
        insertAndParseTransaction(transaction, mintTimestamp, 1, SERIAL_NUMBER_LIST);
        assertTokenInRepository(TOKEN_ID, false, CREATE_TIMESTAMP, mintTimestamp, SYMBOL, 1);
        assertNftInRepository(TOKEN_ID, SERIAL_NUMBER_1, false, mintTimestamp, mintTimestamp, METADATA
                .getBytes(), EntityId.of(PAYER), false);
        assertNftInRepository(TOKEN_ID, SERIAL_NUMBER_2, false, mintTimestamp, mintTimestamp, METADATA
                .getBytes(), EntityId.of(PAYER), false);
    }

    @Test
    void tokenTransfer() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, INITIAL_SUPPLY);
        TokenID tokenID2 = TokenID.newBuilder().setShardNum(0).setRealmNum(0).setTokenNum(7).build();
        String symbol2 = "MIRROR";
        createTokenEntity(tokenID2, TokenType.FUNGIBLE_COMMON, symbol2, 10L, false, false);

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
        insertAndParseTransaction(transaction, transferTimeStamp, INITIAL_SUPPLY, null, transferList1, transferList2);

        assertTokenTransferInRepository(TOKEN_ID, PAYER, transferTimeStamp, -1000);
        assertTokenTransferInRepository(TOKEN_ID, accountId, transferTimeStamp, 1000);
        assertTokenTransferInRepository(tokenID2, PAYER, transferTimeStamp, 333);
        assertTokenTransferInRepository(tokenID2, accountId, transferTimeStamp, -333);
    }

    @Test
    void nftTransfer() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, 0);

        TokenID tokenID2 = TokenID.newBuilder().setShardNum(0).setRealmNum(0).setTokenNum(7).build();
        String symbol2 = "MIRROR";
        createTokenEntity(tokenID2, TokenType.FUNGIBLE_COMMON, symbol2, 15L, false, false);

        long mintTimestamp1 = 20L;
        Transaction mintTransaction1 = tokenSupplyTransaction(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, true, 0, Arrays
                .asList(SERIAL_NUMBER_1));

        // Verify
        insertAndParseTransaction(mintTransaction1, mintTimestamp1, 2, Arrays.asList(SERIAL_NUMBER_1));

        long mintTimestamp2 = 30L;
        Transaction mintTransaction2 = tokenSupplyTransaction(tokenID2, TokenType.NON_FUNGIBLE_UNIQUE, true, 0, Arrays
                .asList(SERIAL_NUMBER_2));

        // Verify
        insertAndParseTransaction(mintTransaction2, mintTimestamp2, 2, Arrays.asList(SERIAL_NUMBER_2));

        // token transfer
        Transaction transaction = tokenTransferTransaction();

        TokenTransferList transferList1 = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addNftTransfers(NftTransfer.newBuilder().setReceiverAccountID(RECEIVER).setSenderAccountID(PAYER)
                        .setSerialNumber(SERIAL_NUMBER_1).build())
                .build();
        TokenTransferList transferList2 = TokenTransferList.newBuilder()
                .setToken(tokenID2)
                .addNftTransfers(NftTransfer.newBuilder().setReceiverAccountID(RECEIVER).setSenderAccountID(PAYER)
                        .setSerialNumber(SERIAL_NUMBER_2).build())
                .build();

        long transferTimeStamp = 40L;
        insertAndParseTransaction(transaction, transferTimeStamp, 0, null, transferList1, transferList2);

        assertNftTransferInRepository(transferTimeStamp, 1L, TOKEN_ID, RECEIVER, PAYER);
        assertNftTransferInRepository(transferTimeStamp, 2L, tokenID2, RECEIVER, PAYER);
        assertNftInRepository(TOKEN_ID, SERIAL_NUMBER_1, true, mintTimestamp1, transferTimeStamp, METADATA
                .getBytes(), EntityId.of(RECEIVER), false);
        assertNftInRepository(tokenID2, SERIAL_NUMBER_2, true, mintTimestamp2, transferTimeStamp, METADATA
                .getBytes(), EntityId.of(RECEIVER), false);
    }

    @Test
    void nftTransferMissingNft() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, 0);

        TokenID tokenID2 = TokenID.newBuilder().setShardNum(0).setRealmNum(0).setTokenNum(7).build();
        String symbol2 = "MIRROR";
        createTokenEntity(tokenID2, TokenType.FUNGIBLE_COMMON, symbol2, 15L, false, false);

        // token transfer
        Transaction transaction = tokenTransferTransaction();

        TokenTransferList transferList1 = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addNftTransfers(NftTransfer.newBuilder().setReceiverAccountID(RECEIVER).setSenderAccountID(PAYER)
                        .setSerialNumber(SERIAL_NUMBER_1).build())
                .build();
        TokenTransferList transferList2 = TokenTransferList.newBuilder()
                .setToken(tokenID2)
                .addNftTransfers(NftTransfer.newBuilder().setReceiverAccountID(RECEIVER).setSenderAccountID(PAYER)
                        .setSerialNumber(SERIAL_NUMBER_2).build())
                .build();

        long transferTimeStamp = 25L;
        insertAndParseTransaction(transaction, transferTimeStamp, 0, null, transferList1, transferList2);

        assertNftTransferInRepository(transferTimeStamp, 1L, TOKEN_ID, RECEIVER, PAYER);
        assertNftTransferInRepository(transferTimeStamp, 2L, tokenID2, RECEIVER, PAYER);
        assertNftInRepository(TOKEN_ID, SERIAL_NUMBER_1, false, transferTimeStamp, transferTimeStamp, METADATA
                .getBytes(), EntityId.of(RECEIVER), false);
        assertNftInRepository(tokenID2, SERIAL_NUMBER_2, false, transferTimeStamp, transferTimeStamp, METADATA
                .getBytes(), EntityId.of(RECEIVER), false);
    }

    @Test
    void tokenWipe() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, INITIAL_SUPPLY);

        long transferAmount = -1000L;
        long wipeAmount = 100L;
        long wipeTimestamp = 10L;
        TokenTransferList tokenTransfer = tokenTransfer(TOKEN_ID, PAYER, transferAmount);
        Transaction transaction = tokenWipeTransaction(TOKEN_ID, TokenType.FUNGIBLE_COMMON, wipeAmount, null);
        insertAndParseTransaction(transaction, wipeTimestamp, INITIAL_SUPPLY - wipeAmount, null, tokenTransfer);

        // Verify
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, wipeTimestamp, SYMBOL, INITIAL_SUPPLY - wipeAmount);
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, wipeTimestamp, transferAmount);
    }

    @Test
    void tokenWipeNft() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, 0);

        long mintTimestamp = 10L;
        Transaction mintTransaction = tokenSupplyTransaction(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, true, 0, Arrays
                .asList(1L, 2L));

        insertAndParseTransaction(mintTransaction, mintTimestamp, 1, Arrays.asList(1L, 2L));

        long wipeTimestamp = 15L;
        Transaction transaction = tokenWipeTransaction(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, 0, Arrays.asList(1L));
        insertAndParseTransaction(transaction, wipeTimestamp, 0, Arrays.asList(1L));

        // Verify
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, wipeTimestamp, SYMBOL, 0);
        assertNftInRepository(TOKEN_ID, 1L, true, mintTimestamp, wipeTimestamp, METADATA.getBytes(), EntityId
                .of(PAYER), true);
        assertNftInRepository(TOKEN_ID, 2L, true, mintTimestamp, mintTimestamp, METADATA.getBytes(), EntityId
                .of(PAYER), false);
    }

    @Test
    void tokenWipeWithMissingToken() throws InvalidProtocolBufferException {
        Transaction transaction = tokenWipeTransaction(TOKEN_ID, TokenType.FUNGIBLE_COMMON, 100L, null);
        insertAndParseTransaction(transaction, 10L, INITIAL_SUPPLY, null);

        assertTokenInRepository(TOKEN_ID, false, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
    }

    @Test
    void tokenWipeNftMissingNft() throws InvalidProtocolBufferException {
        createAndAssociateToken(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, 0);

        long wipeTimestamp = 15L;
        Transaction transaction = tokenWipeTransaction(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, 0, Arrays
                .asList(SERIAL_NUMBER_1));
        insertAndParseTransaction(transaction, wipeTimestamp, 0, Arrays.asList(SERIAL_NUMBER_1));

        // Verify
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, wipeTimestamp, SYMBOL, 0);
        assertNftInRepository(TOKEN_ID, 1L, false, wipeTimestamp, wipeTimestamp, METADATA.getBytes(), EntityId
                .of(PAYER), true);
    }

    @Test
    void tokenCreateAndAssociateAndWipeInSameRecordFile() throws InvalidProtocolBufferException {
        long transferAmount = -1000L;
        long wipeAmount = 100L;
        long wipeTimestamp = 10L;
        long newTotalSupply = INITIAL_SUPPLY - wipeAmount;

        // create token with a transfer
        Transaction createTransaction = tokenCreateTransaction(TokenType.FUNGIBLE_COMMON, false, false, SYMBOL);
        TransactionBody createTransactionBody = getTransactionBody(createTransaction);
        TokenTransferList createTokenTransfer = tokenTransfer(TOKEN_ID, PAYER, INITIAL_SUPPLY);
        var createTransactionRecord = createTransactionRecord(CREATE_TIMESTAMP, TOKEN_ID
                        .getTokenNum(), createTransactionBody, ResponseCodeEnum.SUCCESS, INITIAL_SUPPLY, null,
                createTokenTransfer);

        // associate with token
        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER);
        TransactionBody associateTransactionBody = getTransactionBody(associateTransaction);
        var associateRecord = createTransactionRecord(ASSOCIATE_TIMESTAMP, TOKEN_ID
                .getTokenNum(), associateTransactionBody, ResponseCodeEnum.SUCCESS, INITIAL_SUPPLY, null);

        // wipe amount from token with a transfer
        TokenTransferList wipeTokenTransfer = tokenTransfer(TOKEN_ID, PAYER, transferAmount);
        Transaction wipeTransaction = tokenWipeTransaction(TOKEN_ID, TokenType.FUNGIBLE_COMMON, wipeAmount, null);
        TransactionBody wipeTransactionBody = getTransactionBody(wipeTransaction);
        var wipeRecord = createTransactionRecord(wipeTimestamp, TOKEN_ID
                        .getTokenNum(), wipeTransactionBody, ResponseCodeEnum.SUCCESS, newTotalSupply,
                null, wipeTokenTransfer);

        // process all record items in a single file
        parseRecordItemsAndCommit(
                new RecordItem(createTransaction, createTransactionRecord),
                new RecordItem(associateTransaction, associateRecord),
                new RecordItem(wipeTransaction, wipeRecord));

        // Verify token, tokenAccount and tokenTransfer
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, wipeTimestamp, SYMBOL, newTotalSupply);
        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, true,
                TokenFreezeStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.NOT_APPLICABLE);
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, wipeTimestamp, transferAmount);
    }

    private void insertAndParseTransaction(Transaction transaction, long timeStamp, long newTotalSupply,
                                           List<Long> serialNumbers, TokenTransferList... tokenTransferLists) throws InvalidProtocolBufferException {
        TransactionBody transactionBody = getTransactionBody(transaction);

        var transactionRecord = createTransactionRecord(timeStamp, TOKEN_ID
                        .getTokenNum(), transactionBody, ResponseCodeEnum.SUCCESS, newTotalSupply, serialNumbers,
                tokenTransferLists);

        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));
        assertTransactionInRepository(ResponseCodeEnum.SUCCESS, timeStamp, null);
    }

    private Transaction tokenCreateTransaction(TokenType tokenType, boolean setFreezeKey, boolean setKycKey,
                                               String symbol) {
        return buildTransaction(builder -> {
            builder.getTokenCreationBuilder()
                    .setAdminKey(TOKEN_REF_KEY)
                    .setAutoRenewAccount(PAYER)
                    .setAutoRenewPeriod(Duration.newBuilder().setSeconds(AUTO_RENEW_PERIOD))
                    .setDecimals(1000)
                    .setExpiry(EXPIRY_TIMESTAMP)
                    .setFreezeDefault(false)
                    .setMemo(TOKEN_CREATE_MEMO)
                    .setName(symbol + "_token_name")
                    .setSupplyKey(TOKEN_REF_KEY)
                    .setSupplyType(TokenSupplyType.INFINITE)
                    .setSymbol(symbol)
                    .setTokenType(tokenType)
                    .setTreasury(PAYER)
                    .setWipeKey(TOKEN_REF_KEY);

            if (tokenType == tokenType.FUNGIBLE_COMMON) {
                builder.getTokenCreationBuilder().
                        setInitialSupply(INITIAL_SUPPLY);
            } else {
                //TODO this might just be default
                builder.getTokenCreationBuilder().
                        setInitialSupply(0);
            }
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

    private Transaction tokenSupplyTransaction(TokenID tokenID, TokenType tokenType, boolean mint, long amount,
                                               List<Long> serialNumbers) {
        Transaction transaction = null;
        if (mint) {
            transaction = buildTransaction(builder -> {
                builder.getTokenMintBuilder()
                        .setToken(tokenID);
                if (tokenType == TokenType.FUNGIBLE_COMMON) {
                    builder.getTokenMintBuilder().setAmount(amount);
                } else {
                    builder.getTokenMintBuilder().addAllMetadata(Collections
                            .nCopies(serialNumbers.size(), ByteString.copyFromUtf8(METADATA)));
                }
            });
        } else {
            transaction = buildTransaction(builder -> {
                builder.getTokenBurnBuilder()
                        .setToken(tokenID);
                if (tokenType == TokenType.FUNGIBLE_COMMON) {
                    builder.getTokenBurnBuilder().setAmount(amount);
                } else {
                    builder.getTokenBurnBuilder()
                            .addAllSerialNumbers(serialNumbers);
                }
            });
        }

        return transaction;
    }

    private Transaction tokenWipeTransaction(TokenID tokenID, TokenType tokenType, long amount,
                                             List<Long> serialNumbers) {
        return buildTransaction(builder -> {
            builder.getTokenWipeBuilder()

                    .setToken(tokenID)
                    .setAccount(PAYER)
                    .build();
            if (tokenType == TokenType.FUNGIBLE_COMMON) {
                builder.getTokenWipeBuilder().setAmount(amount);
            } else {
                builder.getTokenWipeBuilder().addAllSerialNumbers(serialNumbers);
            }
        });
    }

    private Transaction tokenTransferTransaction() {
        return buildTransaction(builder -> builder.getCryptoTransferBuilder());
    }

    private TransactionRecord createTransactionRecord(long consensusTimestamp, long tokenNum,
                                                      TransactionBody transactionBody,
                                                      ResponseCodeEnum responseCode,
                                                      long newTotalSupply,
                                                      List<Long> serialNumbers,
                                                      TokenTransferList... tokenTransferLists) {
        var receipt = TransactionReceipt.newBuilder()
                .setStatus(responseCode)
                .setTokenID(TokenID.newBuilder().setShardNum(0).setRealmNum(0).setTokenNum(tokenNum).build())
                .setNewTotalSupply(newTotalSupply);

        //TODO maybe not needed
        if (serialNumbers != null) {
            receipt.addAllSerialNumbers(serialNumbers);
        }

        return buildTransactionRecord(recordBuilder -> {
                    recordBuilder
                            .setReceipt(receipt)
                            .setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp));

                    if (tokenTransferLists != null && tokenTransferLists.length != 0) {
                        recordBuilder.addAllTokenTransferLists(Arrays.asList(tokenTransferLists));
                    }

                    recordBuilder.getReceiptBuilder().setAccountID(PAYER);
                },
                transactionBody, responseCode.getNumber());
    }

    private void assertTokenInRepository(TokenID tokenID, boolean present, long createdTimestamp,
                                         long modifiedTimestamp, String symbol, long totalSupply) {
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

    private void assertNftInRepository(TokenID tokenID, long serialNumber, boolean present, long createdTimestamp,
                                       long modifiedTimestamp, byte[] metadata, EntityId accountId, boolean deleted) {
        Optional<Nft> nftOptional = nftRepository.findById(new Nft.Id(serialNumber, EntityId.of(tokenID)));
        if (present) {
            assertThat(nftOptional.get())
                    .returns(createdTimestamp, from(Nft::getCreatedTimestamp))
                    .returns(modifiedTimestamp, from(Nft::getModifiedTimestamp))
                    .returns(metadata, from(Nft::getMetadata))
                    .returns(accountId, from(Nft::getAccountId))
                    .returns(deleted, from(Nft::isDeleted));
        } else {
            assertThat(nftOptional.isPresent()).isFalse();
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

    private void assertNftTransferInRepository(long consensusTimestamp, long serialNumber, TokenID tokenID,
                                               AccountID receiverId, AccountID senderId) {
        com.hedera.mirror.importer.domain.NftTransfer nftTransfer = nftTransferRepository
                .findById(new com.hedera.mirror.importer.domain.NftTransfer.Id(consensusTimestamp, serialNumber,
                        EntityId
                                .of(tokenID))).get();
        assertThat(nftTransfer)
                .returns(EntityId
                        .of(receiverId), from(com.hedera.mirror.importer.domain.NftTransfer::getReceiverAccountId))
                .returns(EntityId
                        .of(senderId), from(com.hedera.mirror.importer.domain.NftTransfer::getSenderAccountId));
    }

    private void createTokenEntity(TokenID tokenID, TokenType tokenType, String symbol, long consensusTimestamp,
                                   boolean setFreezeKey, boolean setKycKey) throws InvalidProtocolBufferException {
        Transaction createTransaction = tokenCreateTransaction(tokenType, setFreezeKey, setKycKey, symbol);
        TransactionBody createTransactionBody = getTransactionBody(createTransaction);
        TokenTransferList tokenTransfer = tokenType == TokenType.FUNGIBLE_COMMON ? tokenTransfer(tokenID, PAYER,
                INITIAL_SUPPLY) : TokenTransferList.getDefaultInstance();
        var createTransactionRecord = createTransactionRecord(consensusTimestamp, tokenID
                .getTokenNum(), createTransactionBody, ResponseCodeEnum.SUCCESS, INITIAL_SUPPLY, null, tokenTransfer);

        parseRecordItemAndCommit(new RecordItem(createTransaction, createTransactionRecord));
    }

    private void createAndAssociateToken(TokenID tokenID, TokenType tokenType, String symbol, long createTimestamp,
                                         long associateTimestamp, AccountID accountID, boolean setFreezeKey,
                                         boolean setKycKey, long initialSupply) throws InvalidProtocolBufferException {
        createTokenEntity(tokenID, tokenType, symbol, createTimestamp, setFreezeKey, setKycKey);
        assertTokenInRepository(tokenID, true, createTimestamp, createTimestamp, symbol, initialSupply);

        Transaction associateTransaction = tokenAssociate(List.of(tokenID), accountID);
        insertAndParseTransaction(associateTransaction, associateTimestamp, initialSupply, null);

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
