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
import com.hedera.mirror.restjava.common.EntityIdParameter;
import com.hedera.mirror.restjava.common.EntityIdRangeParameter;
import com.hedera.mirror.restjava.common.RangeOperator;
import com.hedera.mirror.restjava.exception.InvalidParametersException;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;

@RequiredArgsConstructor
public class NftAllowanceServiceTest extends RestJavaIntegrationTest {

    private final NftAllowanceService service;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getNftAllowancesForOrderAsc(boolean owner) {
        var accountId = EntityId.of(1000L);

        var nftAllowance1 = saveNftAllowance(accountId, owner);
        var nftAllowance2 = saveNftAllowance(accountId, owner);
        saveNftAllowance(accountId, owner);
        saveNftAllowance(accountId, owner);
        NftAllowanceRequest request = NftAllowanceRequest.builder()
                .isOwner(owner)
                .limit(2)
                .accountId(new EntityIdParameter(accountId))
                .ownerOrSpenderId(new EntityIdRangeParameter(RangeOperator.GT, accountId))
                .tokenId(new EntityIdRangeParameter(RangeOperator.GT, accountId))
                .order(Sort.Direction.ASC)
                .build();
        var response = service.getNftAllowances(request);
        assertThat(response).containsExactlyInAnyOrder(nftAllowance1, nftAllowance2);
    }

    @Test
    void getNftAllowancesForOrderDescOwner() {
        var accountId = EntityId.of(1000L);
        var id = accountId.getId();

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
                .accountId(new EntityIdParameter(accountId))
                .ownerOrSpenderId(new EntityIdRangeParameter(RangeOperator.GT, accountId))
                .tokenId(new EntityIdRangeParameter(RangeOperator.GT, accountId))
                .order(Sort.Direction.DESC)
                .build();

        var response = service.getNftAllowances(request);

        assertThat(response).containsExactlyInAnyOrder(nftAllowance1, nftAllowance2);
    }

    @Test
    void getNftAllowancesForOrderDescSpender() {
        var accountId = EntityId.of(1000L);
        var id = accountId.getId();

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
                .accountId(new EntityIdParameter(accountId))
                .ownerOrSpenderId(new EntityIdRangeParameter(RangeOperator.GT, accountId))
                .tokenId(new EntityIdRangeParameter(RangeOperator.GT, accountId))
                .order(Sort.Direction.DESC)
                .build();

        var response = service.getNftAllowances(request);

        assertThat(response).containsExactlyInAnyOrder(nftAllowance1, nftAllowance2);
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
                .accountId(new EntityIdParameter(accountId))
                .ownerOrSpenderId(
                        new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(nftAllowance1.getSpender())))
                .tokenId(new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(nftAllowance1.getTokenId())))
                .order(Sort.Direction.ASC)
                .build();
        var response = service.getNftAllowances(request);
        assertThat(response).containsExactlyInAnyOrder(nftAllowance1);
    }

    @Test
    void getNftAllowancesForGteSpender() {
        var accountId = EntityId.of(1001L);

        var nftAllowance1 = saveNftAllowance(accountId, false);

        domainBuilder
                .nftAllowance()
                .customize(e -> e.spender(accountId.getId())
                        .owner(nftAllowance1.getOwner() - 2)
                        .tokenId(nftAllowance1.getTokenId() - 2))
                .persist();

        NftAllowanceRequest request = NftAllowanceRequest.builder()
                .isOwner(false)
                .limit(2)
                .accountId(new EntityIdParameter(accountId))
                .ownerOrSpenderId(
                        new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(nftAllowance1.getSpender())))
                .tokenId(new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(nftAllowance1.getTokenId())))
                .order(Sort.Direction.ASC)
                .build();
        var response = service.getNftAllowances(request);
        assertThat(response).containsExactlyInAnyOrder(nftAllowance1);
    }

    @Test
    void getNftAllowancesForOwnerOrSpenderIdNotPresent() {
        var accountId = EntityId.of(1001L);

        var nftAllowance1 = saveNftAllowance(accountId, false);

        domainBuilder
                .nftAllowance()
                .customize(e -> e.spender(accountId.getId())
                        .owner(nftAllowance1.getOwner() - 2)
                        .tokenId(nftAllowance1.getTokenId() - 2))
                .persist();

        NftAllowanceRequest request = NftAllowanceRequest.builder()
                .isOwner(false)
                .limit(2)
                .accountId(new EntityIdParameter(accountId))
                .ownerOrSpenderId(EntityIdRangeParameter.EMPTY)
                .tokenId(new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of(nftAllowance1.getTokenId())))
                .order(Sort.Direction.ASC)
                .build();
        assertThrows(InvalidParametersException.class, () -> service.getNftAllowances(request));
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
