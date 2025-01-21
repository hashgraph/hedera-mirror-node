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

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Named
class TopicMessageBuilder extends AbstractEntityBuilder<TopicMessage, TopicMessage.TopicMessageBuilder> {

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::topicMessages;
    }

    @Override
    protected TopicMessage.TopicMessageBuilder getEntityBuilder(SpecBuilderContext builderContext) {
        return TopicMessage.builder()
                .message("message".getBytes(StandardCharsets.UTF_8))
                .payerAccountId(EntityId.of(3L))
                .runningHash("running_hash".getBytes(StandardCharsets.UTF_8))
                .runningHashVersion(2);
    }

    @Override
    protected TopicMessage getFinalEntity(TopicMessage.TopicMessageBuilder builder, Map<String, Object> account) {
        return builder.build();
    }
}
