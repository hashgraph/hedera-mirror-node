/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Named
public class ContractBuilder extends AbstractEntityBuilder<Contract, Contract.ContractBuilder<?, ?>> {
    private static final Map<String, String> ATTRIBUTE_NAME_MAP = Map.of("num", "id");

    public ContractBuilder() {
        super(Map.of(), ATTRIBUTE_NAME_MAP);
    }

    @Override
    protected Contract.ContractBuilder<?, ?> getEntityBuilder(SpecBuilderContext builderContext) {
        return Contract.builder();
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return () -> Optional.ofNullable(specSetup.contracts()).orElse(List.of()).stream()
                .filter(contract -> !isHistory(contract))
                .toList();
    }

    @Override
    protected Contract getFinalEntity(Contract.ContractBuilder<?, ?> builder, Map<String, Object> entityAttributes) {
        return builder.build();
    }
}
