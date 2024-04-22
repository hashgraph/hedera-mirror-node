/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import com.hedera.mirror.restjava.common.EntityIdAliasParameter;
import com.hedera.mirror.restjava.common.EntityIdEvmAddressParameter;
import com.hedera.mirror.restjava.common.EntityIdNumParameter;
import com.hedera.mirror.restjava.common.EntityIdRangeParameter;
import com.hedera.mirror.restjava.common.RangeOperator;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;

@RequiredArgsConstructor
class NftAllowanceServiceTest extends RestJavaIntegrationTest {

    private final NftAllowanceService service;
    private static final EntityId ACCOUNT_ID = EntityId.of(1000L);

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getNftAllowancesForOrderAsc(boolean owner) {

        var nftAllowance1 = saveNftAllowance(ACCOUNT_ID, owner);
        var nftAllowance2 = saveNftAllowance(ACCOUNT_ID, owner);
        saveNftAllowance(ACCOUNT_ID, owner);
        saveNftAllowance(ACCOUNT_ID, owner);
        NftAllowanceRequest request = NftAllowanceRequest.builder()
                .isOwner(owner)
                .limit(2)
                .accountId(new EntityIdNumParameter(ACCOUNT_ID))
                .ownerOrSpenderId(new EntityIdRangeParameter(RangeOperator.GT, ACCOUNT_ID))
                .tokenId(new EntityIdRangeParameter(RangeOperator.GT, ACCOUNT_ID))
                .order(Sort.Direction.ASC)
                .build();
        var response = service.getNftAllowances(request);
        assertThat(response).containsExactly(nftAllowance1, nftAllowance2);
    }

    @Test
    void getNftAllowancesWithAlias() {
        var entity = domainBuilder.entity().persist();
        var accountId = entity.toEntityId();

        var nftAllowance1 = saveNftAllowance(accountId, true);
        var nftAllowance2 = saveNftAllowance(accountId, true);

        NftAllowanceRequest request = NftAllowanceRequest.builder()
                .isOwner(true)
                .limit(2)
                .accountId(new EntityIdAliasParameter(0, 0, entity.getAlias()))
                .ownerOrSpenderId(new EntityIdRangeParameter(RangeOperator.GT, accountId))
                .tokenId(new EntityIdRangeParameter(RangeOperator.GT, accountId))
                .order(Sort.Direction.ASC)
                .build();
        var response = service.getNftAllowances(request);
        assertThat(response).containsExactlyInAnyOrder(nftAllowance1, nftAllowance2);
    }

    @Test
    void getNftAllowancesWithEvmAddress() {
        var entity = domainBuilder.entity().persist();
        var accountId = entity.toEntityId();

        var nftAllowance1 = saveNftAllowance(accountId, true);
        var nftAllowance2 = saveNftAllowance(accountId, true);

        NftAllowanceRequest request = NftAllowanceRequest.builder()
                .isOwner(true)
                .limit(2)
                .accountId(new EntityIdEvmAddressParameter(0, 0, entity.getEvmAddress()))
                .ownerOrSpenderId(new EntityIdRangeParameter(RangeOperator.GT, accountId))
                .tokenId(new EntityIdRangeParameter(RangeOperator.GT, accountId))
                .order(Sort.Direction.ASC)
                .build();
        var response = service.getNftAllowances(request);
        assertThat(response).containsExactlyInAnyOrder(nftAllowance1, nftAllowance2);
    }

    @Test
    void getNftAllowancesForOrderDescOwner() {

        var id = ACCOUNT_ID.getId();

        var nftAllowance1 = domainBuilder
                .nftAllowance()
                .customize(e -> e.owner(id).spender(id + 100))
                .persist();
        var nftAllowance2 = domainBuilder
                .nftAllowance()
                .customize(e -> e.owner(id).spender(id + 50))
                .persist();

        NftAllowanceRequest request = NftAllowanceRequest.builder()
                .isOwner(true)
                .limit(2)
                .accountId(new EntityIdNumParameter(ACCOUNT_ID))
                .ownerOrSpenderId(new EntityIdRangeParameter(RangeOperator.GT, ACCOUNT_ID))
                .tokenId(new EntityIdRangeParameter(RangeOperator.GT, ACCOUNT_ID))
                .order(Sort.Direction.DESC)
                .build();

        var response = service.getNftAllowances(request);

        assertThat(response).containsExactly(nftAllowance1, nftAllowance2);
    }

    @Test
    void getNftAllowancesForOrderDescSpender() {

        var id = ACCOUNT_ID.getId();

        var nftAllowance1 = domainBuilder
                .nftAllowance()
                .customize(e -> e.spender(id).owner(id + 100))
                .persist();
        var nftAllowance2 = domainBuilder
                .nftAllowance()
                .customize(e -> e.spender(id).owner(id + 50))
                .persist();

        NftAllowanceRequest request = NftAllowanceRequest.builder()
                .isOwner(false)
                .limit(2)
                .accountId(new EntityIdNumParameter(ACCOUNT_ID))
                .ownerOrSpenderId(new EntityIdRangeParameter(RangeOperator.GT, ACCOUNT_ID))
                .tokenId(new EntityIdRangeParameter(RangeOperator.GT, ACCOUNT_ID))
                .order(Sort.Direction.DESC)
                .build();

        var response = service.getNftAllowances(request);

        assertThat(response).containsExactly(nftAllowance1, nftAllowance2);
    }

    @Test
    void getNftAllowancesForGteOwner() {
        var accountId = EntityId.of(1001L);

        var nftAllowance1 = saveNftAllowance(accountId, true);

        // Setting the account.id and token id to 2 less than allowance1 in order to test GTE.
        // This should return only the first allowance.
        domainBuilder
                .nftAllowance()
                .customize(e -> e.owner(accountId.getId())
                        .spender(nftAllowance1.getSpender() - 2)
                        .tokenId(nftAllowance1.getTokenId() - 2))
                .persist();

        NftAllowanceRequest request = NftAllowanceRequest.builder()
                .isOwner(true)
                .limit(2)
                .accountId(new EntityIdNumParameter(accountId))
                .ownerOrSpenderId(
                        new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(nftAllowance1.getSpender())))
                .tokenId(new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(nftAllowance1.getTokenId())))
                .order(Sort.Direction.ASC)
                .build();
        var response = service.getNftAllowances(request);
        assertThat(response).containsExactly(nftAllowance1);
    }

    @Test
    void getNftAllowancesForGteSpender() {
        var accountId = EntityId.of(1001L);

        var nftAllowance1 = saveNftAllowance(ACCOUNT_ID, false);

        domainBuilder
                .nftAllowance()
                .customize(e -> e.spender(ACCOUNT_ID.getId())
                        .owner(nftAllowance1.getOwner() - 2)
                        .tokenId(nftAllowance1.getTokenId() - 2))
                .persist();

        NftAllowanceRequest request = NftAllowanceRequest.builder()
                .isOwner(false)
                .limit(2)
                .accountId(new EntityIdNumParameter(ACCOUNT_ID))
                .ownerOrSpenderId(new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(nftAllowance1.getOwner())))
                .tokenId(new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(nftAllowance1.getTokenId())))
                .order(Sort.Direction.ASC)
                .build();
        var response = service.getNftAllowances(request);
        assertThat(response).containsExactlyInAnyOrder(nftAllowance1);
    }

    @Test
    void getNftAllowancesForOwnerOrSpenderIdNotPresent() {
        var accountId = EntityId.of(1001L);

        var nftAllowance1 = saveNftAllowance(ACCOUNT_ID, false);

        domainBuilder
                .nftAllowance()
                .customize(e -> e.spender(ACCOUNT_ID.getId())
                        .owner(nftAllowance1.getOwner() - 2)
                        .tokenId(nftAllowance1.getTokenId() - 2))
                .persist();

        NftAllowanceRequest request = NftAllowanceRequest.builder()
                .isOwner(false)
                .limit(2)
                .accountId(new EntityIdNumParameter(ACCOUNT_ID))
                .tokenId(new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(nftAllowance1.getTokenId())))
                .order(Sort.Direction.ASC)
                .build();
        assertThrows(IllegalArgumentException.class, () -> service.getNftAllowances(request));
    }

    @Test
    void getNftAllowancesForInvalidOperatorPresent() {
        var accountId = EntityId.of(1001L);

        var nftAllowance1 = saveNftAllowance(ACCOUNT_ID, false);

        domainBuilder
                .nftAllowance()
                .customize(e -> e.spender(ACCOUNT_ID.getId())
                        .owner(nftAllowance1.getOwner() - 2)
                        .tokenId(nftAllowance1.getTokenId() - 2))
                .persist();

        NftAllowanceRequest request = NftAllowanceRequest.builder()
                .isOwner(false)
                .limit(2)
                .accountId(new EntityIdNumParameter(ACCOUNT_ID))
                .ownerOrSpenderId(new EntityIdRangeParameter(RangeOperator.NE, EntityId.of(nftAllowance1.getSpender())))
                .order(Sort.Direction.ASC)
                .build();
        assertThrows(IllegalArgumentException.class, () -> service.getNftAllowances(request));
    }

    NftAllowance saveNftAllowance(EntityId accountId, boolean owner) {
        if (owner) {
            return domainBuilder
                    .nftAllowance()
                    .customize(e -> e.owner(accountId.getId()))
                    .persist();
        } else {
            return domainBuilder
                    .nftAllowance()
                    .customize(e -> e.spender(accountId.getId()))
                    .persist();
        }
    }
}
