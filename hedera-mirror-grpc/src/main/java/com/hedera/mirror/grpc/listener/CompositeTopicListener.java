package com.hedera.mirror.grpc.listener;

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

import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import com.hedera.mirror.grpc.listener.ListenerProperties.ListenerType;

@Named
@Log4j2
@Primary
@RequiredArgsConstructor
public class CompositeTopicListener implements TopicListener {

    private final ListenerProperties listenerProperties;
    private final PollingTopicListener pollingTopicListener;
    private final SharedPollingTopicListener sharedPollingTopicListener;

    @Override
    public Flux<TopicMessage> listen(TopicMessageFilter filter) {
        if (!listenerProperties.isEnabled()) {
            return Flux.empty();
        }

        return getTopicListener().listen(filter);
    }

    private TopicListener getTopicListener() {
        ListenerType type = listenerProperties.getType();

        switch (type) {
            case POLL:
                return pollingTopicListener;
            case SHARED_POLL:
                return sharedPollingTopicListener;
            default:
                throw new UnsupportedOperationException("Unknown listener type: " + type);
        }
    }
}
