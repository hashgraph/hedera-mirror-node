/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.store.accessor;

import com.hedera.mirror.common.domain.entity.AbstractTokenAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.web3.evm.store.DatabaseBackedStateFrame.DatabaseAccessIncorrectKeyTypeException;
import com.hedera.mirror.web3.repository.TokenAllowanceRepository;
import jakarta.inject.Named;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class TokenAllowanceDatabaseAccessor extends DatabaseAccessor<Object, TokenAllowance> {

    private final TokenAllowanceRepository tokenAllowanceRepository;

    @Override
    public @NonNull Optional<TokenAllowance> get(@NonNull Object key, final Optional<Long> timestamp) {
        if (key instanceof AbstractTokenAllowance.Id id) {
            return timestamp
                    .map(t -> tokenAllowanceRepository.findByOwnerSpenderTokenAndTimestamp(
                            id.getOwner(), id.getSpender(), id.getTokenId(), t))
                    .orElseGet(() -> tokenAllowanceRepository.findById(id));
        }
        throw new DatabaseAccessIncorrectKeyTypeException("Accessor for class %s failed to fetch by key of type %s"
                .formatted(TokenAllowance.class.getTypeName(), key.getClass().getTypeName()));
    }
}
