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

package com.hedera.mirror.restjava.controller;

import static com.hedera.mirror.restjava.common.ParameterNames.ACCOUNT_ID;
import static com.hedera.mirror.restjava.common.ParameterNames.TOKEN_ID;

import com.google.common.collect.ImmutableSortedMap;
import com.hedera.mirror.rest.model.NftAllowance;
import com.hedera.mirror.rest.model.NftAllowancesResponse;
import com.hedera.mirror.restjava.common.EntityIdParameter;
import com.hedera.mirror.restjava.common.EntityIdRangeParameter;
import com.hedera.mirror.restjava.common.LinkFactory;
import com.hedera.mirror.restjava.dto.NftAllowanceRequest;
import com.hedera.mirror.restjava.mapper.NftAllowanceMapper;
import com.hedera.mirror.restjava.service.Bound;
import com.hedera.mirror.restjava.service.NftAllowanceService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CustomLog
@RequestMapping("/api/v1/accounts/{id}/allowances")
@RequiredArgsConstructor
@RestController
public class AllowancesController {

    private static final String DEFAULT_LIMIT = "25";
    private static final Map<Boolean, Function<NftAllowance, Map<String, String>>> EXTRACTORS = Map.of(
            true,
            nftAllowance -> ImmutableSortedMap.of(
                    ACCOUNT_ID, nftAllowance.getSpender(),
                    TOKEN_ID, nftAllowance.getTokenId()),
            false,
            nftAllowance -> ImmutableSortedMap.of(
                    ACCOUNT_ID, nftAllowance.getOwner(),
                    TOKEN_ID, nftAllowance.getTokenId()));
    private static final int MAX_LIMIT = 100;

    private final LinkFactory linkFactory;
    private final NftAllowanceService service;
    private final NftAllowanceMapper nftAllowanceMapper;

    @GetMapping(value = "/nfts")
    NftAllowancesResponse getNftAllowances(
            @PathVariable EntityIdParameter id,
            @RequestParam(name = ACCOUNT_ID, required = false) @Size(max = 2) List<EntityIdRangeParameter> accountIds,
            @RequestParam(defaultValue = DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "asc") Sort.Direction order,
            @RequestParam(defaultValue = "true") boolean owner,
            @RequestParam(name = TOKEN_ID, required = false) @Size(max = 2) List<EntityIdRangeParameter> tokenIds) {

        var request = NftAllowanceRequest.builder()
                .accountId(id)
                .isOwner(owner)
                .limit(limit)
                .order(order)
                .ownerOrSpenderIds(new Bound(accountIds, true, ACCOUNT_ID))
                .tokenIds(new Bound(tokenIds, false, TOKEN_ID))
                .build();

        var serviceResponse = service.getNftAllowances(request);
        var allowances = nftAllowanceMapper.map(serviceResponse);

        var sort = Sort.by(order, ACCOUNT_ID, TOKEN_ID);
        var pageable = PageRequest.of(0, limit, sort);
        var links = linkFactory.create(allowances, pageable, EXTRACTORS.get(owner));

        return new NftAllowancesResponse().allowances(allowances).links(links);
    }
}
