/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.fees.calculation.utils;

import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hedera.services.utils.IdUtils.asAccount;
import static com.hedera.services.utils.IdUtils.asToken;
import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.google.protobuf.ByteString;
import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.IdUtils;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpUsageCtxHelperTest {

    @Mock
    private Store store;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private SignedTxnAccessor accessor;

    private OpUsageCtxHelper subject;

    @Mock
    private MirrorEvmContractAliases aliasManager;

    @BeforeEach
    void setUp() {
        subject = new OpUsageCtxHelper();
    }

    @Test
    void returnsExpectedCtxForAccount() {
        final var merkleAccount = mock(Account.class);
        given(store.getAccount(any(), any())).willReturn(merkleAccount);
        given(merkleAccount.getCryptoAllowances()).willReturn(new TreeMap<>());
        given(merkleAccount.getApproveForAllNfts()).willReturn(new TreeSet<>());
        given(merkleAccount.getFungibleTokenAllowances()).willReturn(new TreeMap<>());
        given(merkleAccount.getKey()).willReturn(asUsableFcKey(key).get());
        given(merkleAccount.getExpiry()).willReturn(now);
        given(merkleAccount.getMaxAutomaticAssociations()).willReturn(maxAutomaticAssociations);
        given(merkleAccount.getProxy()).willReturn(Id.DEFAULT);

        final var ctx = subject.ctxForCryptoUpdate(TransactionBody.getDefaultInstance(), store, aliasManager);

        assertEquals(maxAutomaticAssociations, ctx.currentMaxAutomaticAssociations());
        assertEquals(now, ctx.currentExpiry());
    }

    @Test
    void returnsExpectedCtxForCryptoApproveAccount() {
        final var merkleAccount = mock(Account.class);
        given(store.getAccount(any(), any())).willReturn(merkleAccount);
        given(merkleAccount.getKey()).willReturn(asUsableFcKey(key).get());
        given(merkleAccount.getExpiry()).willReturn(now);
        given(merkleAccount.getMaxAutomaticAssociations()).willReturn(maxAutomaticAssociations);
        given(merkleAccount.getProxy()).willReturn(Id.DEFAULT);
        given(merkleAccount.getCryptoAllowances()).willReturn(cryptoAllowance);
        given(merkleAccount.getFungibleTokenAllowances()).willReturn(tokenAllowance);
        given(merkleAccount.getApproveForAllNfts()).willReturn(nftAllowance);
        given(accessor.getTxn()).willReturn(TransactionBody.getDefaultInstance());
        given(accessor.getPayer()).willReturn(AccountID.getDefaultInstance());

        final var ctx = subject.ctxForCryptoAllowance(accessor, store, aliasManager);

        assertEquals(maxAutomaticAssociations, ctx.currentMaxAutomaticAssociations());
        assertEquals(Map.of(spender1.getAccountNum(), 10L), ctx.currentCryptoAllowances());
        assertEquals(1, ctx.currentTokenAllowances().size());
        assertEquals(1, ctx.currentTokenAllowances().size());
        assertEquals(1, ctx.currentNftAllowances().size());
    }

    @Test
    void returnsMissingCtxWhenAccountNotFound() {
        given(store.getAccount(any(), any())).willReturn(Account.getEmptyAccount());

        final var ctx = subject.ctxForCryptoUpdate(TransactionBody.getDefaultInstance(), store, aliasManager);

        assertEquals("", ctx.currentMemo());
        assertEquals(0, ctx.currentExpiry());
    }

    @Test
    void returnsMissingCtxWhenApproveAccountNotFound() {
        given(accessor.getTxn()).willReturn(TransactionBody.getDefaultInstance());
        given(accessor.getPayer()).willReturn(AccountID.getDefaultInstance());
        given(store.getAccount(any(), any())).willReturn(Account.getEmptyAccount());

        final var ctx = subject.ctxForCryptoAllowance(accessor, store, aliasManager);

        assertEquals("", ctx.currentMemo());
        assertEquals(Collections.emptySet(), ctx.currentNftAllowances());
        assertEquals(Collections.emptyMap(), ctx.currentTokenAllowances());
        assertEquals(Collections.emptyMap(), ctx.currentCryptoAllowances());
    }

    @Test
    void getMetaForTokenMintWorks() {
        final TokenMintTransactionBody mintTxnBody = getUniqueTokenMintOp();
        final TransactionBody txn = getTxnBody(mintTxnBody);
        final Token extant = new Token(
                0L,
                Id.fromGrpcToken(token1),
                new ArrayList<>(),
                new ArrayList<>(),
                new HashMap<>(),
                false,
                TokenType.FUNGIBLE_COMMON,
                TokenSupplyType.FINITE,
                0,
                21_000_000,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                false,
                false,
                false,
                now,
                0l,
                false,
                "the mother",
                "bitcoin",
                "BTC",
                10,
                0L,
                0L,
                Collections.emptyList());

        given(accessor.getTxn()).willReturn(txn);
        given(accessor.getSubType()).willReturn(TOKEN_NON_FUNGIBLE_UNIQUE);

        given(store.getToken(asTypedEvmAddress(target), OnMissing.THROW)).willReturn(extant);

        final var tokenMintMeta = subject.metaForTokenMint(accessor, store);

        // then:
        assertEquals(34, tokenMintMeta.getBpt());
        assertEquals(TOKEN_NON_FUNGIBLE_UNIQUE, tokenMintMeta.getSubType());
        assertEquals(12345670, tokenMintMeta.getRbs());
        assertEquals(80, tokenMintMeta.getTransferRecordDb());
    }

    private TokenMintTransactionBody getUniqueTokenMintOp() {
        return TokenMintTransactionBody.newBuilder()
                .setToken(target)
                .addAllMetadata(List.of(ByteString.copyFromUtf8("NFT meta 1")))
                .build();
    }

    private TransactionBody getTxnBody(final TokenMintTransactionBody op) {
        return TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
                .setTokenMint(op)
                .build();
    }

    private final long now = 1_234_567L;
    private final Key key = Key.newBuilder()
            .setEd25519(ByteString.copyFromUtf8("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
            .build();
    private final String memo = "accountInfo";
    private final int maxAutomaticAssociations = 12;
    private final TokenID target = IdUtils.asToken("0.0.1003");
    private static final AccountID spender1 = asAccount("0.0.123");
    private static final TokenID token1 = asToken("0.0.100");
    private static final SortedMap<EntityNum, Long> cryptoAllowance = new TreeMap<>() {
        {
            put(EntityNum.fromAccountId(spender1), 10L);
        }
    };

    private static final SortedMap<FcTokenAllowanceId, Long> tokenAllowance = new TreeMap<>() {
        {
            put(FcTokenAllowanceId.from(EntityNum.fromTokenId(token1), EntityNum.fromAccountId(spender1)), 10L);
        }
    };

    private final SortedSet<FcTokenAllowanceId> nftAllowance = new TreeSet<>() {
        {
            add(FcTokenAllowanceId.from(new EntityNum(1000), EntityNum.fromAccountId(spender1)));
        }
    };
}
