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

import java.util.stream.Stream;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.grpc.converter.InstantToLongConverter;
import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;

@Log4j2
@Named
@RequiredArgsConstructor
public class TopicMessageRepositoryCustomImpl implements TopicMessageRepositoryCustom {

    private final EntityManager entityManager;
    private final InstantToLongConverter converter;

    @Override
    public Stream<TopicMessage> findByFilter(TopicMessageFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<TopicMessage> query = cb.createQuery(TopicMessage.class);
        Root<TopicMessage> root = query.from(TopicMessage.class);

        Predicate predicate = cb.and(
                cb.equal(root.get("realmNum"), filter.getRealmNum()),
                cb.equal(root.get("topicNum"), filter.getTopicNum()),
                cb.greaterThanOrEqualTo(root.get("consensusTimestamp"), converter.convert(filter.getStartTime()))
        );

        if (filter.getEndTime() != null) {
            predicate = cb.and(predicate, cb
                    .lessThan(root.get("consensusTimestamp"), converter.convert(filter.getEndTime())));
        }

        query = query.select(root).where(predicate).orderBy(cb.asc(root.get("consensusTimestamp")));

        TypedQuery<TopicMessage> typedQuery = entityManager.createQuery(query);
        if (filter.hasLimit()) {
            typedQuery.setMaxResults((int) filter.getLimit());
        }
        return typedQuery.getResultList().stream(); // getResultStream()'s cursor doesn't work with reactive streams
    }
}
