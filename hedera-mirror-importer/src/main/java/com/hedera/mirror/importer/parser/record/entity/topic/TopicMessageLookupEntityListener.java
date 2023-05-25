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

package com.hedera.mirror.importer.parser.record.entity.topic;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.common.domain.topic.TopicMessageLookup;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.db.TimePartitionService;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.parser.batch.BatchPersister;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.CustomLog;
import org.springframework.core.annotation.Order;

@CustomLog
@Named
@Order(1)
@ConditionOnEntityRecordParser
public class TopicMessageLookupEntityListener implements EntityListener, RecordStreamFileListener {

    private static final long FILE_CLOSE_INTERVAL_SECS =
            StreamType.RECORD.getFileCloseInterval().toSeconds();
    private static final String TOPIC_MESSAGE_TABLE_NAME = "topic_message";

    private final BatchPersister batchPersister;
    private final EntityProperties entityProperties;
    private final Map<TopicMessageLookup.Id, TopicMessageLookup> topicMessageLookups;
    private final TimePartitionService timePartitionService;

    public TopicMessageLookupEntityListener(
            BatchPersister batchPersister,
            EntityProperties entityProperties,
            TimePartitionService timePartitionService) {
        this.batchPersister = batchPersister;
        this.entityProperties = entityProperties;
        this.timePartitionService = timePartitionService;

        topicMessageLookups = new HashMap<>();
    }

    @Override
    public boolean isEnabled() {
        var persistProperties = entityProperties.getPersist();
        if (!(persistProperties.isTopics() && persistProperties.isTopicMessageLookups())) {
            return false;
        }

        var partitions = timePartitionService.getTimePartitions(TOPIC_MESSAGE_TABLE_NAME);
        return !partitions.isEmpty();
    }

    @Override
    public void onTopicMessage(TopicMessage topicMessage) throws ImporterException {
        // round down the seconds part of the consensus timestamp to achieve better cache hit rate
        long seconds =
                Instant.ofEpochSecond(0, topicMessage.getConsensusTimestamp()).getEpochSecond();
        long fromSeconds = seconds - (seconds % FILE_CLOSE_INTERVAL_SECS);
        long fromTimestamp = DomainUtils.convertToNanosMax(fromSeconds, 0L);
        long toTimestamp = DomainUtils.convertToNanosMax(fromSeconds + FILE_CLOSE_INTERVAL_SECS, 0L);
        var partitions =
                timePartitionService.getOverlappingTimePartitions(TOPIC_MESSAGE_TABLE_NAME, fromTimestamp, toTimestamp);

        for (var partition : partitions) {
            if (partition.getTimestampRange().contains(topicMessage.getConsensusTimestamp())) {
                var topicMessageLookup = TopicMessageLookup.from(partition.getName(), topicMessage);
                topicMessageLookups.merge(
                        topicMessageLookup.getId(), topicMessageLookup, this::mergeTopicMessageLookup);
                break;
            }
        }
    }

    @Override
    public void onStart() throws ImporterException {
        cleanup();
    }

    @Override
    public void onEnd(RecordFile recordFile) throws ImporterException {
        try {
            if (!isEnabled() || recordFile == null) {
                return;
            }

            persistTopicMessageLookups();
        } finally {
            cleanup();
        }
    }

    @Override
    public void onError() {
        cleanup();
    }

    private void cleanup() {
        topicMessageLookups.clear();
    }

    private TopicMessageLookup mergeTopicMessageLookup(TopicMessageLookup cached, TopicMessageLookup newValue) {
        cached.setSequenceNumberRange(cached.getSequenceNumberRange().span(newValue.getSequenceNumberRange()));
        cached.setTimestampRange(cached.getTimestampRange().span(newValue.getTimestampRange()));
        return cached;
    }

    private void persistTopicMessageLookups() {
        if (topicMessageLookups.isEmpty()) {
            return;
        }

        batchPersister.persist(topicMessageLookups.values());
    }
}
