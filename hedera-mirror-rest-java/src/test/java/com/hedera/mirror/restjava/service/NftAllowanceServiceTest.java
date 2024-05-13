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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import com.hedera.mirror.restjava.common.EntityIdAliasParameter;
import com.hedera.mirror.restjava.common.EntityIdEvmAddressParameter;
import com.hedera.mirror.restjava.common.EntityIdNumParameter;
import com.hedera.mirror.restjava.common.EntityIdRangeParameter;
import com.hedera.mirror.restjava.common.RangeOperator;
import java.util.List;
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
                .ownerOrSpenderIds(List.of(new EntityIdRangeParameter(RangeOperator.GTE, accountId)))
                .tokenIds(List.of(new EntityIdRangeParameter(RangeOperator.GT, accountId)))
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
                .ownerOrSpenderIds(List.of(new EntityIdRangeParameter(RangeOperator.GTE, accountId)))
                .tokenIds(List.of(new EntityIdRangeParameter(RangeOperator.GT, accountId)))
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
                .ownerOrSpenderIds(List.of(new EntityIdRangeParameter(RangeOperator.GTE, ACCOUNT_ID)))
                .tokenIds(List.of(new EntityIdRangeParameter(RangeOperator.GT, ACCOUNT_ID)))
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
                .ownerOrSpenderIds(List.of(new EntityIdRangeParameter(RangeOperator.GTE, ACCOUNT_ID)))
                .tokenIds(List.of(new EntityIdRangeParameter(RangeOperator.GTE, ACCOUNT_ID)))
                .order(Sort.Direction.DESC)
                .build();

        var response = service.getNftAllowances(request);

        assertThat(response).containsExactly(nftAllowance1, nftAllowance2);
    }

    @Test
    void getNftAllowancesForGteOwner() {

        var nftAllowance1 = saveNftAllowance(ACCOUNT_ID, true);

        // Setting the account.id and token id to 2 less than allowance1 in order to test GTE.
        // This should return only the first allowance.
        domainBuilder
                .nftAllowance()
                .customize(e -> e.owner(ACCOUNT_ID.getId())
                        .spender(nftAllowance1.getSpender() - 2)
                        .tokenId(nftAllowance1.getTokenId() - 2))
                .persist();

        NftAllowanceRequest request = NftAllowanceRequest.builder()
                .isOwner(true)
                .limit(2)
                .accountId(new EntityIdNumParameter(ACCOUNT_ID))
                .ownerOrSpenderIds(
                        List.of(new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(nftAllowance1.getSpender()))))
                .tokenIds(
                        List.of(new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(nftAllowance1.getTokenId()))))
                .order(Sort.Direction.ASC)
                .build();
        var response = service.getNftAllowances(request);
        assertThat(response).containsExactly(nftAllowance1);
    }

    @Test
    void getNftAllowancesForGteSpender() {

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
                .ownerOrSpenderIds(
                        List.of(new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(nftAllowance1.getOwner()))))
                .tokenIds(
                        List.of(new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(nftAllowance1.getTokenId()))))
                .order(Sort.Direction.ASC)
                .build();
        var response = service.getNftAllowances(request);
        assertThat(response).containsExactlyInAnyOrder(nftAllowance1);
    }

    @Test
    void getNftAllowancesForInvalidRange() {

        var nftAllowance1 = saveNftAllowance(ACCOUNT_ID, false);

        domainBuilder
                .nftAllowance()
                .customize(e -> e.spender(ACCOUNT_ID.getId())
                        .owner(nftAllowance1.getOwner() - 2)
                        .tokenId(nftAllowance1.getTokenId() - 2))
                .persist();

        assertEmptyResponse(nftAllowance1, RangeOperator.GTE, RangeOperator.LTE);
        assertEmptyResponse(nftAllowance1, RangeOperator.GT, RangeOperator.LT);
    }

    @Test
    void getNftAllowancesForOptimizedRange() {

        var nftAllowance1 = saveNftAllowance(ACCOUNT_ID, false);

        var nftAllowance2 = domainBuilder
                .nftAllowance()
                .customize(e -> e.spender(ACCOUNT_ID.getId())
                        .owner(nftAllowance1.getOwner() + 2)
                        .tokenId(nftAllowance1.getTokenId() - 2))
                .persist();

        NftAllowanceRequest request = NftAllowanceRequest.builder()
                .isOwner(false)
                .limit(2)
                .accountId(new EntityIdNumParameter(ACCOUNT_ID))
                .ownerOrSpenderIds(List.of(
                        new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(nftAllowance1.getOwner())),
                        new EntityIdRangeParameter(RangeOperator.LTE, EntityId.of(nftAllowance1.getOwner()))))
                .tokenIds(
                        List.of(new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(nftAllowance1.getTokenId()))))
                .order(Sort.Direction.ASC)
                .build();
        var response = service.getNftAllowances(request);
        assertThat(response).containsExactly(nftAllowance1, nftAllowance2);
    }

    @Test
    void getNftAllowancesForTokenNeedsOwnerOrSpenderIdEq() {

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
                .ownerOrSpenderIds(
                        List.of(new EntityIdRangeParameter(RangeOperator.LT, EntityId.of(nftAllowance1.getOwner()))))
                .tokenIds(
                        List.of(new EntityIdRangeParameter(RangeOperator.LT, EntityId.of(nftAllowance1.getTokenId()))))
                .order(Sort.Direction.ASC)
                .build();
        assertThatThrownBy(() -> service.getNftAllowances(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "Single occurrence only supported.Requires the presence of an lte or eq account.id query");

        NftAllowanceRequest request1 = NftAllowanceRequest.builder()
                .isOwner(false)
                .limit(2)
                .accountId(new EntityIdNumParameter(ACCOUNT_ID))
                .ownerOrSpenderIds(
                        List.of(new EntityIdRangeParameter(RangeOperator.GT, EntityId.of(nftAllowance1.getOwner()))))
                .tokenIds(
                        List.of(new EntityIdRangeParameter(RangeOperator.GT, EntityId.of(nftAllowance1.getTokenId()))))
                .order(Sort.Direction.ASC)
                .build();
        assertThatThrownBy(() -> service.getNftAllowances(request1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "Single occurrence only supported.Requires the presence of an gte or eq account.id query");
    }

    @Test
    void getNftAllowancesForRepeatedParameter() {

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
                .ownerOrSpenderIds(List.of(
                        new EntityIdRangeParameter(RangeOperator.LT, EntityId.of(nftAllowance1.getOwner())),
                        new EntityIdRangeParameter(RangeOperator.LT, EntityId.of(nftAllowance1.getOwner() + 10))))
                .tokenIds(
                        List.of(new EntityIdRangeParameter(RangeOperator.LT, EntityId.of(nftAllowance1.getTokenId()))))
                .order(Sort.Direction.ASC)
                .build();
        assertThatThrownBy(() -> service.getNftAllowances(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Single occurrence only supported.");
    }

    @Test
    void getNftAllowancesEqAndRangeParameter() {

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
                .ownerOrSpenderIds(List.of(
                        new EntityIdRangeParameter(RangeOperator.LT, EntityId.of(nftAllowance1.getOwner())),
                        new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of(nftAllowance1.getOwner() + 10))))
                .tokenIds(
                        List.of(new EntityIdRangeParameter(RangeOperator.LT, EntityId.of(nftAllowance1.getTokenId()))))
                .order(Sort.Direction.ASC)
                .build();
        assertThatThrownBy(() -> service.getNftAllowances(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Can't support both range and equal for this parameter.");
    }

    @Test
    void getNftAllowancesForOwnerOrSpenderIdNotPresent() {

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
                .tokenIds(
                        List.of(new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(nftAllowance1.getTokenId()))))
                .order(Sort.Direction.ASC)
                .build();
        assertThatThrownBy(() -> service.getNftAllowances(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token.id parameter must have account.id present");
    }

    @Test
    void getNftAllowancesForInvalidOperatorPresent() {

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
                .ownerOrSpenderIds(
                        List.of(new EntityIdRangeParameter(RangeOperator.NE, EntityId.of(nftAllowance1.getSpender()))))
                .order(Sort.Direction.ASC)
                .build();
        assertThatThrownBy(() -> service.getNftAllowances(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid range operator ne. This operator is not supported");
    }

    private void assertEmptyResponse(NftAllowance nftAllowance1, RangeOperator op1, RangeOperator op2) {
        NftAllowanceRequest request = NftAllowanceRequest.builder()
                .isOwner(false)
                .limit(2)
                .accountId(new EntityIdNumParameter(ACCOUNT_ID))
                .ownerOrSpenderIds(List.of(
                        new EntityIdRangeParameter(op1, EntityId.of(nftAllowance1.getOwner())),
                        new EntityIdRangeParameter(op2, EntityId.of(nftAllowance1.getOwner() - 2))))
                .order(Sort.Direction.ASC)
                .build();
        var response = service.getNftAllowances(request);
        assertThat(response).isEmpty();
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
