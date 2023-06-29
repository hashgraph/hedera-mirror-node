/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.txns.crypto.helpers;

import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.updateSpender;
import static com.hedera.services.utils.IdUtils.asAccount;
import static com.hedera.services.utils.IdUtils.asModelId;
import static com.hedera.services.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.google.protobuf.BoolValue;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.UniqueToken;
import com.hederahashgraph.api.proto.java.NftAllowance;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AllowanceHelpersTest {
    @Mock
    private Store store;

    final Token token = mock(Token.class);
    final Account treasury = mock(Account.class);
    final Id ownerId = asModelId("0.0.123");
    final Id spenderId = asModelId("0.0.124");
    final Id tokenId = asModelId("0.0.125");
    final long serial1 = 1L;
    final long serial2 = 2L;

    @Test
    void aggregatedListCorrectly() {
        List<NftAllowance> list = new ArrayList<>();
        final var Nftid = NftAllowance.newBuilder()
                .setSpender(asAccount("0.0.1000"))
                .addAllSerialNumbers(List.of(1L, 10L))
                .setTokenId(asToken("0.0.10001"))
                .setOwner(asAccount("0.0.5000"))
                .setApprovedForAll(BoolValue.of(false))
                .build();
        final var Nftid2 = NftAllowance.newBuilder()
                .setSpender(asAccount("0.0.1000"))
                .addAllSerialNumbers(List.of(1L, 100L))
                .setTokenId(asToken("0.0.10001"))
                .setOwner(asAccount("0.0.5000"))
                .setApprovedForAll(BoolValue.of(false))
                .build();
        list.add(Nftid);
        list.add(Nftid2);
    }

    @Test
    void failsToUpdateSpenderIfWrongOwner() {
        final UniqueToken nft1 = new UniqueToken(tokenId, serial1, null, ownerId, null, null);
        final UniqueToken nft2 = new UniqueToken(tokenId, serial2, null, spenderId, null, null);
        final var serials = List.of(serial1, serial2);

        given(store.getUniqueToken(
                        new NftId(tokenId.shard(), tokenId.realm(), tokenId.num(), serial1), OnMissing.THROW))
                .willReturn(nft1);
        given(store.getUniqueToken(
                        new NftId(tokenId.shard(), tokenId.realm(), tokenId.num(), serial2), OnMissing.THROW))
                .willReturn(nft2);
        given(store.getToken(tokenId.asEvmAddress(), OnMissing.THROW)).willReturn(token);
        given(token.getTreasury()).willReturn(treasury);
        given(treasury.getId()).willReturn(ownerId);

        final var ex = assertThrows(
                InvalidTransactionException.class, () -> updateSpender(store, ownerId, spenderId, tokenId, serials));

        assertEquals(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO, ex.getResponseCode());
    }

    @Test
    void updatesSpenderAsExpected() {
        final UniqueToken nft1 = new UniqueToken(tokenId, serial1, null, ownerId, null, null);
        final UniqueToken nft2 = new UniqueToken(tokenId, serial2, null, ownerId, null, null);

        given(store.getUniqueToken(
                        new NftId(tokenId.shard(), tokenId.realm(), tokenId.num(), serial1), OnMissing.THROW))
                .willReturn(nft1);
        given(store.getUniqueToken(
                        new NftId(tokenId.shard(), tokenId.realm(), tokenId.num(), serial2), OnMissing.THROW))
                .willReturn(nft2);
        given(store.getToken(tokenId.asEvmAddress(), OnMissing.THROW)).willReturn(token);
        given(token.getTreasury()).willReturn(treasury);
        given(treasury.getId()).willReturn(ownerId);

        final var updatedNfts = updateSpender(store, ownerId, spenderId, tokenId, List.of(serial1, serial2));

        assertEquals(spenderId, updatedNfts.get(0).getSpender());
        assertEquals(spenderId, updatedNfts.get(1).getSpender());
    }
}
