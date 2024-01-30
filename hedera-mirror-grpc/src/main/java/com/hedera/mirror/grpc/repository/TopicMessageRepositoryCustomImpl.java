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

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Stream;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hibernate.jpa.HibernateHints;

@CustomLog
@Named
@RequiredArgsConstructor
public class TopicMessageRepositoryCustomImpl implements TopicMessageRepositoryCustom {

    private static final String CONSENSUS_TIMESTAMP = "consensusTimestamp";
    private static final String TOPIC_ID = "topicId";
    // make the cost estimation of using the index on (topic_id, consensus_timestamp) lower than that of
    // the primary key so pg planner will choose the better index when querying topic messages by id
    private static final String TOPIC_MESSAGES_BY_ID_QUERY_HINT = "set local random_page_cost = 0";

    private final EntityManager entityManager;
    private final TransactionRepository transactionRepository;

    @Override
    public Stream<TopicMessage> findByFilter(TopicMessageFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<TopicMessage> query = cb.createQuery(TopicMessage.class);
        Root<TopicMessage> root = query.from(TopicMessage.class);

        Predicate predicate = cb.and(
                cb.equal(root.get(TOPIC_ID), filter.getTopicId()),
                cb.greaterThanOrEqualTo(root.get(CONSENSUS_TIMESTAMP), filter.getStartTime()));

        if (filter.getEndTime() != null) {
            predicate = cb.and(predicate, cb.lessThan(root.get(CONSENSUS_TIMESTAMP), filter.getEndTime()));
        }

        query = query.select(root).where(predicate).orderBy(cb.asc(root.get(CONSENSUS_TIMESTAMP)));

        TypedQuery<TopicMessage> typedQuery = entityManager.createQuery(query);
        typedQuery.setHint(HibernateHints.HINT_READ_ONLY, true);

        if (filter.hasLimit()) {
            typedQuery.setMaxResults((int) filter.getLimit());
        }

        if (filter.getLimit() != 1) {
            // only apply the hint when limit is not 1
            entityManager.createNativeQuery(TOPIC_MESSAGES_BY_ID_QUERY_HINT).executeUpdate();
        }

        return typedQuery.getResultList().stream(); // getResultStream()'s cursor doesn't work with reactive streams
    }

    @Override
    public Stream<TopicMessage> findLatest(long consensusTimestamp, int limit) {
        var transactions = transactionRepository.findSuccessfulTransactionsByTypeAfterTimestamp(
                consensusTimestamp, limit, TransactionType.CONSENSUSSUBMITMESSAGE.getProtoId());
        if (transactions.isEmpty()) {
            return Stream.empty();
        }

        var timestamps = new ArrayList<Long>();
        var topicIds = new HashSet<EntityId>();
        for (var transaction : transactions) {
            topicIds.add(transaction.getEntityId());
            timestamps.add(transaction.getConsensusTimestamp());
        }

        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(TopicMessage.class);
        var root = query.from(TopicMessage.class);

        var predicate = cb.and(
                root.get(TOPIC_ID).in(topicIds), root.get(CONSENSUS_TIMESTAMP).in(timestamps));
        query = query.select(root).where(predicate).orderBy(cb.asc(root.get(CONSENSUS_TIMESTAMP)));
        var typedQuery = entityManager.createQuery(query);
        return typedQuery.getResultList().stream();
    }
}
