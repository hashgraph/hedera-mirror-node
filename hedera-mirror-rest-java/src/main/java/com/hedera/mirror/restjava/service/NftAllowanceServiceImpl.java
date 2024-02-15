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

import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.restjava.repository.NftAllowanceRepository;
import jakarta.inject.Named;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Named
@RequiredArgsConstructor
public class NftAllowanceServiceImpl implements NftAllowanceService {

    private final NftAllowanceRepository repository;

    public List<NftAllowance> getNftAllowances(NftAllowanceRequest request) {

        var ownerId = request.getOwnerId();
        var limit = request.getLimit();
        var order = request.getOrder();
        var spenderId = request.getSpenderId();
        var tokenId = request.getTokenId();

        Pageable pageable;
        // Set the value depending on the owner flag
        if (request.isOwner()) {
            pageable = PageRequest.of(0, limit, Sort.by(order, "spender").and(Sort.by(order, "token_id")));
            return repository.findByOwnerAndFilterBySpenderAndToken(ownerId, spenderId, tokenId, pageable);
        } else {
            pageable = PageRequest.of(0, limit, Sort.by(order, "owner").and(Sort.by(order, "token_id")));
            return repository.findBySpenderAndFilterByOwnerAndToken(spenderId, ownerId, tokenId, pageable);
        }
    }
}
