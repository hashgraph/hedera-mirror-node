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

import static com.hedera.mirror.restjava.common.EntityIdUtils.parseIdFromString;
import static com.hedera.mirror.restjava.common.RestUtils.*;

import com.hedera.mirror.rest.model.NftAllowancesResponse;
import com.hedera.mirror.restjava.common.Filter;
import com.hedera.mirror.restjava.common.FilterKey;
import com.hedera.mirror.restjava.mapper.NftAllowanceMapper;
import com.hedera.mirror.restjava.service.NftAllowanceRequest;
import com.hedera.mirror.restjava.service.NftAllowanceRequest.NftAllowanceRequestBuilder;
import com.hedera.mirror.restjava.service.NftAllowanceService;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@CustomLog
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounts")
@RestController
public class AccountController {

    private final NftAllowanceService service;
    private final NftAllowanceMapper accountMapper;

    @GetMapping(value = "/{accountId}/allowances/nfts")
    NftAllowancesResponse getNftAllowancesByAccountId(
            @PathVariable("accountId") @Valid String accountId,
            @RequestParam("account.id") @Valid Optional<String> accountIdQueryParam,
            @RequestParam("owner") @Valid Optional<Boolean> owner,
            @RequestParam("token.id") @Valid Optional<String> tokenId,
            @RequestParam("limit") @Valid Optional<Integer> limit,
            @RequestParam("order") @Valid Optional<String> order) {

        // validate accountId and check for what to pass here
        // get filters
        Filter filter = getFilter(FilterKey.ACCOUNT_ID, accountIdQueryParam.orElse(null));
        Filter tokenIdFilter = getFilter(FilterKey.TOKEN_ID, tokenId.orElse(null));
        Long accountPathParam = parseIdFromString(accountId)[2];

        Boolean isOwner = owner.orElse(true);
        NftAllowanceRequestBuilder requestBuilder = NftAllowanceRequest.builder()
                .limit(validateLimit(limit.orElse(DEFAULT_LIMIT)))
                .order(order.map(Sort.Direction::fromString).orElse(Sort.Direction.ASC))
                .isOwner(isOwner);
        // Setting both owner and spender Id to the account.id query parameter value.
        if (filter != null) {
            requestBuilder
                    .spenderId(getAccountNum(filter))
                    .ownerId(getAccountNum(filter))
                    .accountIdOperator(filter.getOperator());
        }

        // Owner value decides if owner or spender should be set to the accountId.
        if (isOwner != null) {
            requestBuilder =
                    isOwner ? requestBuilder.ownerId(accountPathParam) : requestBuilder.spenderId(accountPathParam);
        }

        if (tokenIdFilter != null) {
            requestBuilder.tokenId(getAccountNum(tokenIdFilter)).tokenIdOperator(tokenIdFilter.getOperator());
        }

        var serviceResponse = service.getNftAllowances(requestBuilder.build());

        NftAllowancesResponse response = new NftAllowancesResponse();
        response.setAllowances(accountMapper.map(serviceResponse));
        return response;
    }
}
