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

package com.hedera.mirror.web3.evm.store.contract.precompile;

import com.hedera.services.store.contracts.precompile.Precompile;
import jakarta.inject.Named;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Named
public class PrecompileMapper {

    public static final String UNSUPPORTED_ERROR = "Precompile not supported for non-static frames";
    private final Map<Integer, Precompile> abiConstantToPrecompile = new HashMap<>();
    private final Set<Integer> precompileSelectors = new HashSet<>();

    public PrecompileMapper(final Set<Precompile> precompiles, final Set<Integer> precompileSelectors) {
        for (Precompile precompile : precompiles) {
            for (Integer selector : precompile.getFunctionSelectors()) {
                abiConstantToPrecompile.put(selector, precompile);
            }
        }
        this.precompileSelectors.addAll(precompileSelectors);
    }

    public Optional<Precompile> lookup(int functionSelector) {
        final var precompile = abiConstantToPrecompile.get(functionSelector);
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
