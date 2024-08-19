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

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.StakingRewardTransfer;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

@Named
class StakingRewardTransferBuilder
        extends AbstractEntityBuilder<StakingRewardTransfer, StakingRewardTransfer.StakingRewardTransferBuilder> {

    private static final Map<String, Function<Object, Object>> METHOD_PARAMETER_CONVERTERS =
            Map.of("accountId", ENTITY_ID_TO_LONG_CONVERTER);

    StakingRewardTransferBuilder() {
        super(METHOD_PARAMETER_CONVERTERS);
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::stakingRewardTransfers;
    }

    @Override
    protected StakingRewardTransfer.StakingRewardTransferBuilder getEntityBuilder() {
        return StakingRewardTransfer.builder().accountId(1001L).amount(100L).payerAccountId(EntityId.of(950L));
    }

    @Override
    protected StakingRewardTransfer getFinalEntity(
            StakingRewardTransfer.StakingRewardTransferBuilder builder, Map<String, Object> account) {
        return builder.build();
    }
}
