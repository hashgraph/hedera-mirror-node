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
import static com.hedera.mirror.restjava.common.Constants.TOKEN_ID;

import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.rest.model.Links;
import com.hedera.mirror.rest.model.NftAllowancesResponse;
import com.hedera.mirror.restjava.common.EntityIdParameter;
import com.hedera.mirror.restjava.common.EntityIdRangeParameter;
import com.hedera.mirror.restjava.common.Utils;
import com.hedera.mirror.restjava.mapper.NftAllowanceMapper;
import com.hedera.mirror.restjava.service.EntityService;
import com.hedera.mirror.restjava.service.NftAllowanceRequest;
import com.hedera.mirror.restjava.service.NftAllowanceRequest.NftAllowanceRequestBuilder;
import com.hedera.mirror.restjava.service.NftAllowanceService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.util.Collection;
import java.util.HashMap;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@CustomLog
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounts/{id}/allowances")
@RestController
public class AllowancesController {

    private final NftAllowanceService service;
    private final EntityService entityService;
    private final NftAllowanceMapper accountMapper;

    @GetMapping(value = "/nfts")
    NftAllowancesResponse getNftAllowancesByAccountId(
            @PathVariable EntityIdParameter id,
            @RequestParam(name = ACCOUNT_ID, required = false) EntityIdRangeParameter accountId,
            @RequestParam(defaultValue = DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "asc") Sort.Direction order,
            @RequestParam(defaultValue = "true") boolean owner,
            @RequestParam(name = TOKEN_ID, required = false) EntityIdRangeParameter tokenId) {

        if (!entityService.lookup(id.value()).isPresent()) {
            throw new EntityNotFoundException("Id %s does not exist".formatted(id));
        }

        NftAllowanceRequestBuilder requestBuilder = NftAllowanceRequest.builder()
                .accountId(id)
                .isOwner(owner)
                .limit(limit)
                .order(order)
                .ownerOrSpenderId(accountId)
                .tokenId(tokenId);

        var serviceResponse = service.getNftAllowances(requestBuilder.build());

        NftAllowancesResponse response = new NftAllowancesResponse();
        response.setAllowances(accountMapper.map(serviceResponse));

        String next = null;
        if (!serviceResponse.isEmpty() && serviceResponse.size() == limit) {
            next = buildNextLink(owner, order, limit, response, serviceResponse);
        }
        response.links(new Links().next(next));
        return response;
    }

    private static String buildNextLink(
            boolean owner,
            Sort.Direction order,
            int limit,
            NftAllowancesResponse response,
            Collection<NftAllowance> serviceResponse) {

        ServletRequestAttributes servletRequestAttributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (servletRequestAttributes == null) {
            return null;
        }
        HttpServletRequest request = servletRequestAttributes.getRequest();
        HashMap<String, String> lastValues = new HashMap<>();
        HashMap<String, Boolean> included = new HashMap<>();
        if (response.getAllowances() != null) {
            if (owner) {
                lastValues.put(
                        ACCOUNT_ID,
                        response.getAllowances().get(serviceResponse.size() - 1).getSpender());

            } else {
                lastValues.put(
                        ACCOUNT_ID,
                        response.getAllowances().get(serviceResponse.size() - 1).getOwner());
            }
            included.put(ACCOUNT_ID, true);
            lastValues.put(
                    TOKEN_ID,
                    response.getAllowances().get(serviceResponse.size() - 1).getTokenId());
            included.put(TOKEN_ID, false);
        }
        return Utils.getPaginationLink(request, false, lastValues, included, order, limit);
    }
}
