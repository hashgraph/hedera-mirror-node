/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.ledger;

import static com.hedera.services.store.models.UniqueToken.getEmptyUniqueToken;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.StoreImpl;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class TransferLogicTest {

    private final long initialBalance = 1_000_000L;
    private final long initialAllowance = 100L;

    private final AccountID payer = AccountID.newBuilder().setAccountNum(12345L).build();
    private final AccountID owner = AccountID.newBuilder().setAccountNum(12347L).build();
    private final TokenID fungibleTokenID =
            TokenID.newBuilder().setTokenNum(1234L).build();

    private final TokenID nonFungibleTokenID =
            TokenID.newBuilder().setTokenNum(1235L).build();

    private final AccountID revokedSpender =
            AccountID.newBuilder().setAccountNum(12346L).build();
    private final EntityNum payerNum = EntityNum.fromAccountId(payer);

    private final FcTokenAllowanceId fungibleAllowanceId =
            FcTokenAllowanceId.from(EntityNum.fromTokenId(fungibleTokenID), payerNum);

    private TreeMap<EntityNum, Long> cryptoAllowances = new TreeMap<>() {
        {
            put(payerNum, initialAllowance);
        }
    };

    private TreeMap<FcTokenAllowanceId, Long> fungibleAllowances = new TreeMap<>() {
        {
            put(fungibleAllowanceId, initialAllowance);
        }
    };

    HederaTokenStore hederaTokenStore;

    @Mock
    AutoCreationLogic autoCreationLogic;

    @Mock
    MirrorEvmContractAliases mirrorEvmContractAliases;

    StoreImpl store;

    @Mock
    EntityIdSource ids;

    @Mock
    Account account;

    @Mock
    UniqueToken uniqueToken;

    private TransferLogic subject;

    @BeforeEach
    void setUp() {
        hederaTokenStore = mock(HederaTokenStore.class);
        subject = new TransferLogic(hederaTokenStore, autoCreationLogic, mirrorEvmContractAliases);
        store = mock(StoreImpl.class);
    }

    @Test
    void happyPathHbarAllowance() {
        final var change = BalanceChange.changingHbar(allowanceAA(owner, -50L), payer);
        var account = new Account(Id.fromGrpcAccount(owner), 0L).setCryptoAllowance(cryptoAllowances);
        given(store.getAccount(asTypedEvmAddress(change.accountId()), OnMissing.THROW))
                .willReturn(account);

        subject.doZeroSum(List.of(change), store, ids, asTypedEvmAddress(payer));

        verify(store).updateAccount(account);
    }

    @Test
    void happyPathFungibleAllowance() {
        final var change = BalanceChange.changingFtUnits(
                Id.fromGrpcToken(fungibleTokenID), fungibleTokenID, allowanceAA(owner, -50L), payer);
        given(hederaTokenStore.tryTokenChange(change)).willReturn(OK);
        var account = new Account(Id.fromGrpcAccount(owner), 0L).setFungibleTokenAllowances(fungibleAllowances);
        given(store.getAccount(asTypedEvmAddress(change.accountId()), OnMissing.THROW))
                .willReturn(account);

        assertDoesNotThrow(() -> subject.doZeroSum(List.of(change), store, ids, asTypedEvmAddress(payer)));
    }

    @Test
    void happyPathNFTAllowance() {
        final var nftId1 = NftId.withDefaultShardRealm(nonFungibleTokenID.getTokenNum(), 1L);
        final var nftId2 = NftId.withDefaultShardRealm(nonFungibleTokenID.getTokenNum(), 2L);
        final var change1 = BalanceChange.changingNftOwnership(
                Id.fromGrpcToken(nonFungibleTokenID),
                nonFungibleTokenID,
                allowanceNftTransfer(owner, revokedSpender, 1L),
                payer);
        final var change2 = BalanceChange.changingNftOwnership(
                Id.fromGrpcToken(nonFungibleTokenID),
                nonFungibleTokenID,
                allowanceNftTransfer(owner, revokedSpender, 123L),
                payer);
        final var change3 = BalanceChange.changingNftOwnership(
                Id.fromGrpcToken(nonFungibleTokenID),
                nonFungibleTokenID,
                nftTransfer(owner, revokedSpender, 2L),
                payer);
        var nft = getEmptyUniqueToken();

        given(hederaTokenStore.tryTokenChange(change1)).willReturn(OK);
        given(hederaTokenStore.tryTokenChange(change2)).willReturn(OK);
        given(hederaTokenStore.tryTokenChange(change3)).willReturn(OK);
        given(store.getUniqueToken(nftId1, OnMissing.THROW)).willReturn(nft);
        given(store.getUniqueToken(nftId2, OnMissing.THROW)).willReturn(nft);
        given(store.getUniqueToken(
                        NftId.withDefaultShardRealm(nonFungibleTokenID.getTokenNum(), 123L), OnMissing.THROW))
                .willReturn(nft);

        assertDoesNotThrow(
                () -> subject.doZeroSum(List.of(change1, change2, change3), store, ids, asTypedEvmAddress(payer)));
    }

    private AccountAmount allowanceAA(final AccountID accountID, final long amount) {
        return AccountAmount.newBuilder()
                .setAccountID(accountID)
                .setAmount(amount)
                .setIsApproval(true)
                .build();
    }

    private NftTransfer allowanceNftTransfer(final AccountID sender, final AccountID receiver, final long serialNum) {
        return NftTransfer.newBuilder()
                .setIsApproval(true)
                .setSenderAccountID(sender)
                .setReceiverAccountID(receiver)
                .setSerialNumber(serialNum)
                .build();
    }

    private NftTransfer nftTransfer(final AccountID sender, final AccountID receiver, final long serialNum) {
        return NftTransfer.newBuilder()
                .setIsApproval(false)
                .setSenderAccountID(sender)
                .setReceiverAccountID(receiver)
                .setSerialNumber(serialNum)
                .build();
    }
}
