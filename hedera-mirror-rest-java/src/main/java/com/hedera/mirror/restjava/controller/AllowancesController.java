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
import static com.hedera.mirror.restjava.common.Constants.MIN_LIMIT;
import static com.hedera.mirror.restjava.common.Constants.TOKEN_ID;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.rest.model.Error;
import com.hedera.mirror.rest.model.ErrorStatus;
import com.hedera.mirror.rest.model.ErrorStatusMessagesInner;
import com.hedera.mirror.rest.model.Links;
import com.hedera.mirror.rest.model.NftAllowancesResponse;
import com.hedera.mirror.restjava.common.EntityIdRangeParameter;
import com.hedera.mirror.restjava.common.Utils;
import com.hedera.mirror.restjava.exception.InvalidInputException;
import com.hedera.mirror.restjava.exception.InvalidParametersException;
import com.hedera.mirror.restjava.mapper.NftAllowanceMapper;
import com.hedera.mirror.restjava.service.NftAllowanceRequest;
import com.hedera.mirror.restjava.service.NftAllowanceRequest.NftAllowanceRequestBuilder;
import com.hedera.mirror.restjava.service.NftAllowanceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Collection;
import java.util.HashMap;
import java.util.NoSuchElementException;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@CustomLog
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounts/{id}/allowances")
@RestController
public class AllowancesController {

    private final NftAllowanceService service;
    private final NftAllowanceMapper accountMapper;

    @GetMapping(value = "/nfts")
    NftAllowancesResponse getNftAllowancesByAccountId(
            @PathVariable EntityIdRangeParameter id,
            @RequestParam(name = ACCOUNT_ID, required = false) EntityIdRangeParameter accountId,
            @RequestParam(defaultValue = "true") boolean owner,
            @RequestParam(name = TOKEN_ID, required = false) EntityIdRangeParameter tokenId,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) @Min(MIN_LIMIT) @Max(MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "asc") String order) {

        long accountPathParam = id.getValue().getNum();
        Sort.Direction orderDirection;
        try {
            orderDirection = Sort.Direction.fromString(order);
        } catch (IllegalArgumentException e) {
            throw new InvalidParametersException("order parameter must have a valid direction");
        }
        NftAllowanceRequestBuilder requestBuilder =
                NftAllowanceRequest.builder().limit(limit).order(orderDirection).isOwner(owner);

        if (accountId == null && tokenId != null) {
            throw new InvalidParametersException("token.id parameter must have account.id present");
        }

        // Setting both owner and spender Id to the account.id query parameter value.
        if (accountId != null && accountId.getValue() != null) {
            requestBuilder
                    .spenderId(accountId.value().getId())
                    .ownerId(accountId.value().getId())
                    .accountIdOperator(accountId.operator());
        }

        // Owner value decides if owner or spender should be set to the accountId.
        requestBuilder = owner ? requestBuilder.ownerId(accountPathParam) : requestBuilder.spenderId(accountPathParam);

        if (tokenId != null) {
            requestBuilder.tokenId(tokenId.value().getId()).tokenIdOperator(tokenId.operator());
        }

        var serviceResponse = service.getNftAllowances(requestBuilder.build());

        NftAllowancesResponse response = new NftAllowancesResponse();
        response.setAllowances(accountMapper.map(serviceResponse));

        String next = null;
        if (!serviceResponse.isEmpty() && serviceResponse.size() == limit) {
            next = buildNextLink(owner, orderDirection, limit, response, serviceResponse);
        }
        response.links(new Links().next(next));
        return response;
    }

    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Not found")
    @ExceptionHandler(NoSuchElementException.class)
    void notFound() {
        // Currently no scenario is throwing this error
    }

    @ExceptionHandler
    @ResponseStatus(BAD_REQUEST)
    private Error inputValidationError(final InvalidInputException e) {
        log.warn("Input validation error: {}", e.getMessage());
        var errorMessage = new ErrorStatusMessagesInner();
        errorMessage.setMessage(e.getMessage());
        var errorStatus = new ErrorStatus().addMessagesItem(errorMessage);
        var error = new Error();
        return error.status(errorStatus);
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
