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

import static com.hedera.mirror.restjava.common.Constants.DEFAULT_LIMIT;
import static com.hedera.mirror.restjava.common.Constants.MAX_LIMIT;
import static com.hedera.mirror.restjava.common.Constants.RECEIVER_ID;
import static com.hedera.mirror.restjava.common.Constants.TOKEN_ID;

import com.google.common.collect.ImmutableSortedMap;
import com.hedera.mirror.rest.model.TokenAirdrop;
import com.hedera.mirror.rest.model.TokenAirdropsResponse;
import com.hedera.mirror.restjava.common.EntityIdParameter;
import com.hedera.mirror.restjava.common.EntityIdRangeParameter;
import com.hedera.mirror.restjava.common.LinkFactory;
import com.hedera.mirror.restjava.dto.TokenAirdropRequest;
import com.hedera.mirror.restjava.mapper.TokenAirdropsMapper;
import com.hedera.mirror.restjava.service.TokenAirdropService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
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
@RequestMapping("/api/v1/accounts/{id}/airdrops")
@RequiredArgsConstructor
@RestController
public class TokenAirdropsController {
    private static final Function<TokenAirdrop, Map<String, String>> EXTRACTOR = tokenAirdrop -> ImmutableSortedMap.of(
            RECEIVER_ID, tokenAirdrop.getReceiverId(),
            TOKEN_ID, tokenAirdrop.getTokenId());

    private final LinkFactory linkFactory;
    private final TokenAirdropsMapper tokenMapper;
    private final TokenAirdropService service;

    @GetMapping(value = "/outstanding")
    TokenAirdropsResponse getOutstandingAirdrops(
            @PathVariable EntityIdParameter id,
            @RequestParam(defaultValue = DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "asc") Sort.Direction order,
            @RequestParam(name = RECEIVER_ID, required = false) EntityIdRangeParameter receiverId,
            @RequestParam(name = TOKEN_ID, required = false) EntityIdRangeParameter tokenId) {
        var request = TokenAirdropRequest.builder()
                .accountId(id)
                .entityId(receiverId)
                .limit(limit)
                .order(order)
                .tokenId(tokenId)
                .build();
        var response = service.getOutstandingAirdrops(request);
        var airdrops = tokenMapper.map(response);
        var sort = Sort.by(order, RECEIVER_ID, TOKEN_ID);
        var pageable = PageRequest.of(0, limit, sort);
        var links = linkFactory.create(airdrops, pageable, EXTRACTOR);
        return new TokenAirdropsResponse().airdrops(airdrops).links(links);
    }
}
