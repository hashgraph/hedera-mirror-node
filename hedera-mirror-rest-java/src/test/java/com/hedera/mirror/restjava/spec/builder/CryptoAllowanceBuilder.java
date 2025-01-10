/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Named
class CryptoAllowanceBuilder
        extends AbstractEntityBuilder<CryptoAllowance, CryptoAllowance.CryptoAllowanceBuilder<?, ?>> {

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::cryptoAllowances;
    }

    @Override
    protected CryptoAllowance.CryptoAllowanceBuilder<?, ?> getEntityBuilder(SpecBuilderContext builderContext) {
        return CryptoAllowance.builder()
                .amount(0L)
                .amountGranted(0L)
                .owner(1000L)
                .payerAccountId(EntityId.of(101L))
                .spender(2000L)
                .timestampRange(Range.atLeast(0L));
    }

    @Override
    protected CryptoAllowance getFinalEntity(
            CryptoAllowance.CryptoAllowanceBuilder<?, ?> builder, Map<String, Object> account) {
        return builder.build();
    }
}
