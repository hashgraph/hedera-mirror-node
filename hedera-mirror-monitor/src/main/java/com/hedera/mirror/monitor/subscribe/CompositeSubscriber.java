/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.monitor.subscribe;

import com.hedera.mirror.monitor.publish.PublishResponse;
import jakarta.inject.Named;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

@Named
@Primary
@RequiredArgsConstructor
@SuppressWarnings("unchecked")
public class CompositeSubscriber implements MirrorSubscriber {

    private final Collection<MirrorSubscriber> subscribers;

    @Override
    public void onPublish(PublishResponse response) {
        subscribers.forEach(s -> s.onPublish(response));
    }

    @Override
    public Flux<SubscribeResponse> subscribe() {
        return Flux.fromIterable(subscribers).flatMap(MirrorSubscriber::subscribe);
    }

    @Override
    public Flux<Scenario<?, ?>> getSubscriptions() {
        return Flux.fromIterable(subscribers).flatMap(MirrorSubscriber::getSubscriptions);
    }
}
