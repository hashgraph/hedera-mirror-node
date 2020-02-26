package com.hedera.mirror.grpc.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import java.time.Instant;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import com.hedera.mirror.grpc.domain.TopicMessage;

public interface TopicMessageRepository extends ReactiveCrudRepository<TopicMessage, Instant>,
        TopicMessageRepositoryCustom {

    @Query("select * from topic_message where consensus_timestamp > :consensusTimestamp " +
            "order by consensus_timestamp asc limit :limit")
    Flux<TopicMessage> findLatest(long consensusTimestamp, long limit);
}
