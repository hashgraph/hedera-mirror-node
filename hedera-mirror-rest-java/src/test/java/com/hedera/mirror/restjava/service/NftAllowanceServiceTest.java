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

import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;

@RequiredArgsConstructor
public class NftAllowanceServiceTest extends RestJavaIntegrationTest {

    private final NftAllowanceService service;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getNftAllowancesForOrderAsc(boolean owner) {
        var accountId = 1000L;

        var nftAllowance1 = saveNftAllowance(accountId, owner);
        var nftAllowance2 = saveNftAllowance(accountId, owner);
        saveNftAllowance(accountId, owner);
        saveNftAllowance(accountId, owner);
        NftAllowanceRequest request = NftAllowanceRequest.builder()
                .isOwner(owner)
                .limit(2)
                .ownerId(accountId)
                .spenderId(1000L)
                .tokenId(1000L)
                .order(Sort.Direction.ASC)
                .build();
        var response = service.getNftAllowances(request);
        assertThat(response).containsExactlyInAnyOrder(nftAllowance1, nftAllowance2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getNftAllowancesForOrderDesc(boolean owner) {
        var accountId = 1000L;
        NftAllowance nftAllowance1, nftAllowance2;

        saveNftAllowance(accountId, owner);
        saveNftAllowance(accountId, owner);

        if (owner) {
            nftAllowance1 = domainBuilder
                    .nftAllowance()
                    .customize(e -> e.owner(accountId).spender(accountId + 100))
                    .persist();
            nftAllowance2 = domainBuilder
                    .nftAllowance()
                    .customize(e -> e.owner(accountId).spender(accountId + 50))
                    .persist();
        } else {
            nftAllowance1 = domainBuilder
                    .nftAllowance()
                    .customize(e -> e.spender(accountId).owner(accountId + 100))
                    .persist();
            nftAllowance2 = domainBuilder
                    .nftAllowance()
                    .customize(e -> e.spender(accountId).owner(accountId + 50))
                    .persist();
        }

        NftAllowanceRequest request = NftAllowanceRequest.builder()
                .isOwner(owner)
                .limit(2)
                .ownerId(accountId)
                .spenderId(1000L)
                .tokenId(1000L)
                .order(Sort.Direction.DESC)
                .build();

        var response = service.getNftAllowances(request);

        assertThat(response).containsExactly(nftAllowance1, nftAllowance2);
    }

    NftAllowance saveNftAllowance(long accountId, boolean owner) {
        if (owner) {
            return domainBuilder
                    .nftAllowance()
                    .customize(e -> e.owner(accountId))
                    .persist();
        } else {
            return domainBuilder
                    .nftAllowance()
                    .customize(e -> e.spender(accountId))
                    .persist();
        }
    }
}
