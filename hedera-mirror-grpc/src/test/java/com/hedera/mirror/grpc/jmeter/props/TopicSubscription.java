package com.hedera.mirror.grpc.jmeter.props;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TopicSubscription {
    private final long topicId;
    private final long startTime;
    private final long endTime;
    private final long limit;
    private final long realmNum;
    private final int historicMessagesCount;
    private final int incomingMessageCount;
    private final int subscribeTimeoutSeconds;
    private final long milliSecWaitBefore;
}
