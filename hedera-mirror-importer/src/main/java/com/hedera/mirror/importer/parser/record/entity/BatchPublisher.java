/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.entity;

import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import io.micrometer.core.instrument.Timer;

public interface BatchPublisher extends RecordStreamFileListener {

    Timer.Builder PUBLISH_TIMER = Timer.builder("hedera.mirror.importer.publish.duration")
            .description("The amount of time it took to publish the domain entity")
            .tag("entity", TopicMessage.class.getSimpleName());
}
