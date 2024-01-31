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

package com.hedera.mirror.grpc.repository;

import com.hedera.mirror.common.domain.topic.TopicMessage;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface TopicMessageRepository extends CrudRepository<TopicMessage, Long>, TopicMessageRepositoryCustom {

    @Query(
            value =
                    """
       with related_transaction as (
         select entity_id as topic_id, consensus_timestamp
         from transaction
         where consensus_timestamp > ?1 and result = 22 and type = 27
         order by consensus_timestamp
         limit ?2
       )
       select t.*
       from topic_message as t
       join related_transaction using (topic_id, consensus_timestamp)
       order by t.consensus_timestamp
       """,
            nativeQuery = true)
    List<TopicMessage> findLatest(long consensusTimestamp, int limit);
}
