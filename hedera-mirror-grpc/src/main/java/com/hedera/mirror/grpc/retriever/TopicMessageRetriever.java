/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.grpc.retriever;

import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import reactor.core.publisher.Flux;

/**
 * Retrieves historical topic messages. This is a cold publisher retrieving only when subscribed and completing once all
 * current results in the database are returned.
 */
public interface TopicMessageRetriever {

    String METRIC = "hedera_mirror_grpc_retriever";

    Flux<TopicMessage> retrieve(TopicMessageFilter filter, boolean throttled);
}
