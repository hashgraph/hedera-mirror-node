/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.store.contracts.precompile;

import com.hedera.services.store.contracts.precompile.utils.PrecompileMapperUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class PrecompileMapperErc {

    public static final String UNSUPPORTED_ERROR = "Precompile not supported for non-static frames";
    private static final Map<Integer, Precompile> abiConstantToPrecompile = new HashMap<>();
    private static final Set<Integer> precompileSelectors = PrecompileMapperUtils.ERC_PRECOMPILE_SELECTORS;

    public PrecompileMapperErc(final Set<Precompile> precompiles) {
        for (final Precompile precompile : precompiles) {
            for (final Integer selector : precompile.getFunctionSelectors()) {
                abiConstantToPrecompile.put(selector, precompile);
            }
        }
    }

    public Optional<Precompile> lookup(final int functionSelector) {
        final var precompile = abiConstantToPrecompile.get(functionSelector);
        // Possible solution here - set evm version in ContractCallContext
        // we already have record file in contract call context
        // if only hts is supported in the specified block
        // we need to return Optional.empty();
        // if both are supported we use the mapper as it is currently working
        if (precompile != null) {
            return Optional.of(precompile);
        }
        // TODO If the function selector is not mapped but is from the list of HTS precompiles, throw an exception until
        // the given precompile is supported
        else if (precompileSelectors.contains(functionSelector)) {
            throw new UnsupportedOperationException(UNSUPPORTED_ERROR);
        } else {
            return Optional.empty();
        }
    }
}
