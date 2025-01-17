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

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.AbstractEntityStake;
import com.hedera.mirror.common.domain.entity.EntityStake;
import com.hedera.mirror.common.domain.entity.EntityStakeHistory;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Named
public class EntityStakeBuilder
        extends AbstractEntityBuilder<AbstractEntityStake, AbstractEntityStake.AbstractEntityStakeBuilder<?, ?>> {
    private static final long SECONDS_PER_DAY = 86400;

    @Override
    protected AbstractEntityStake.AbstractEntityStakeBuilder<?, ?> getEntityBuilder(SpecBuilderContext builderContext) {
        var builder = builderContext.isHistory() ? EntityStakeHistory.builder() : EntityStake.builder();
        return builder.endStakePeriod(1)
                .pendingReward(0)
                .stakedNodeIdStart(1)
                .stakedToMe(0)
                .stakeTotalStart(0);
    }

    @Override
    protected AbstractEntityStake getFinalEntity(
            AbstractEntityStake.AbstractEntityStakeBuilder<?, ?> builder, Map<String, Object> entityAttributes) {

        var entityStake = builder.build();
        if (entityStake.getTimestampRange() == null) {
            var seconds = SECONDS_PER_DAY * (entityStake.getEndStakePeriod() + 1);
            var lowerBound = seconds * DomainUtils.NANOS_PER_SECOND + 1;
            entityStake.setTimestampRange(Range.atLeast(lowerBound));
        }
        return entityStake;
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::entityStakes;
    }
}
