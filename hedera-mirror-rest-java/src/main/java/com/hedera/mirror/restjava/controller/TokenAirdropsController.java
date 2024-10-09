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

import static com.hedera.mirror.restjava.common.Constants.ACCOUNT_ID;
import static com.hedera.mirror.restjava.common.Constants.DEFAULT_LIMIT;
import static com.hedera.mirror.restjava.common.Constants.MAX_LIMIT;
import static com.hedera.mirror.restjava.common.Constants.RECEIVER_ID;
import static com.hedera.mirror.restjava.common.Constants.SENDER_ID;
import static com.hedera.mirror.restjava.common.Constants.SERIAL_NUMBER;
import static com.hedera.mirror.restjava.common.Constants.TOKEN_ID;
import static com.hedera.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType.OUTSTANDING;
import static com.hedera.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType.PENDING;
import static com.hedera.mirror.restjava.jooq.domain.Tables.TOKEN_AIRDROP;

import com.google.common.collect.ImmutableSortedMap;
import com.hedera.mirror.rest.model.TokenAirdrop;
import com.hedera.mirror.rest.model.TokenAirdropsResponse;
import com.hedera.mirror.restjava.common.EntityIdParameter;
import com.hedera.mirror.restjava.common.EntityIdRangeParameter;
import com.hedera.mirror.restjava.common.LinkFactory;
import com.hedera.mirror.restjava.common.NumberRangeParameter;
import com.hedera.mirror.restjava.dto.TokenAirdropRequest;
import com.hedera.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType;
import com.hedera.mirror.restjava.mapper.TokenAirdropMapper;
import com.hedera.mirror.restjava.service.Bound;
import com.hedera.mirror.restjava.service.TokenAirdropService;
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
@RequestMapping("/api/v1/accounts/{id}/airdrops")
@RequiredArgsConstructor
@RestController
public class TokenAirdropsController {

    private static final String DEFAULT_SERIAL_NUMBER = "0L";
    private static final long DEFAULT_SERIAL_VALUE = 0L;
    private static final Function<TokenAirdrop, Map<String, String>> EXTRACTOR = tokenAirdrop -> {
        var serialNumber = tokenAirdrop.getSerialNumber();
        return ImmutableSortedMap.of(
                RECEIVER_ID, tokenAirdrop.getReceiverId(),
                SENDER_ID, tokenAirdrop.getSenderId(),
                SERIAL_NUMBER, serialNumber == null ? DEFAULT_SERIAL_NUMBER : serialNumber.toString(),
                TOKEN_ID, tokenAirdrop.getTokenId());
    };

    private final LinkFactory linkFactory;
    private final TokenAirdropMapper tokenAirdropMapper;
    private final TokenAirdropService service;

    @GetMapping(value = "/outstanding")
    TokenAirdropsResponse getOutstandingAirdrops(
            @PathVariable EntityIdParameter id,
            @RequestParam(defaultValue = DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "asc") Sort.Direction order,
            @RequestParam(name = RECEIVER_ID, required = false) @Size(max = 2) EntityIdRangeParameter[] receiverIds,
            @RequestParam(name = SERIAL_NUMBER, required = false) @Size(max = 2) NumberRangeParameter[] serialNumbers,
            @RequestParam(name = TOKEN_ID, required = false) @Size(max = 2) EntityIdRangeParameter[] tokenIds) {
        return processRequest(id, receiverIds, limit, order, serialNumbers, tokenIds, OUTSTANDING);
    }

    @GetMapping(value = "/pending")
    TokenAirdropsResponse getPendingAirdrops(
            @PathVariable EntityIdParameter id,
            @RequestParam(defaultValue = DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "asc") Sort.Direction order,
            @RequestParam(name = SENDER_ID, required = false) @Size(max = 2) EntityIdRangeParameter[] senderIds,
            @RequestParam(name = SERIAL_NUMBER, required = false) @Size(max = 2) NumberRangeParameter[] serialNumbers,
            @RequestParam(name = TOKEN_ID, required = false) @Size(max = 2) EntityIdRangeParameter[] tokenIds) {
        return processRequest(id, senderIds, limit, order, serialNumbers, tokenIds, PENDING);
    }

    @SuppressWarnings("java:S107")
    private TokenAirdropsResponse processRequest(
            EntityIdParameter id,
            EntityIdRangeParameter[] entityIds,
            int limit,
            Sort.Direction order,
            NumberRangeParameter[] serialNumbers,
            EntityIdRangeParameter[] tokenIds,
            AirdropRequestType type) {
        var entityIdsBound = new Bound(entityIds, true, ACCOUNT_ID, type.getPrimaryField());
        var request = TokenAirdropRequest.builder()
                .accountId(id)
                .entityIds(entityIdsBound)
                .limit(limit)
                .order(order)
                .serialNumbers(new Bound(serialNumbers, false, SERIAL_NUMBER, TOKEN_AIRDROP.SERIAL_NUMBER))
                .tokenIds(new Bound(tokenIds, false, TOKEN_ID, TOKEN_AIRDROP.TOKEN_ID))
                .type(type)
                .build();

        var response = service.getAirdrops(request);
        var airdrops = tokenAirdropMapper.map(response);
        var sort = getSort(airdrops, order, type.getParameter());
        var pageable = PageRequest.of(0, limit, sort);
        var links = linkFactory.create(airdrops, pageable, EXTRACTOR);
        return new TokenAirdropsResponse().airdrops(airdrops).links(links);
    }

    private Sort getSort(List<TokenAirdrop> airdrops, Sort.Direction order, String primarySortField) {
        if (!airdrops.isEmpty()) {
            var lastSerial = airdrops.getLast().getSerialNumber();
            if (lastSerial == null || lastSerial == DEFAULT_SERIAL_VALUE) {
                // If no serial present, the next link should be based off of the token id
                return Sort.by(order, primarySortField, TOKEN_ID);
            }
        }

        return Sort.by(order, primarySortField, TOKEN_ID, SERIAL_NUMBER);
    }
}
