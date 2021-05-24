package com.hedera.mirror.monitor.subscribe.controller;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.hedera.mirror.monitor.subscribe.MirrorSubscriber;
import com.hedera.mirror.monitor.subscribe.SubscriberProtocol;
import com.hedera.mirror.monitor.subscribe.Subscription;
import com.hedera.mirror.monitor.subscribe.SubscriptionStatus;

@Log4j2
@RequestMapping("/api/v1/subscriber")
@RequiredArgsConstructor
@RestController
class SubscriberController {

    private final MirrorSubscriber mirrorSubscriber;

    @GetMapping
    Flux<Subscription> subscriptions(@RequestParam Optional<SubscriberProtocol> protocol,
                                     @RequestParam Optional<List<SubscriptionStatus>> status) {
        return mirrorSubscriber.subscriptions()
                .filter(s -> protocol.isPresent() ? protocol.get() == s.getProtocol() : true)
                .filter(s -> status.isPresent() ? status.get().contains(s.getStatus()) : true)
                .switchIfEmpty(Mono.error(new NoSuchElementException()));
    }

    @GetMapping("/{name}")
    Flux<Subscription> subscription(@PathVariable String name,
                                    @RequestParam Optional<List<SubscriptionStatus>> status) {
        return subscriptions(Optional.empty(), status)
                .filter(subscription -> subscription.getName().equals(name))
                .switchIfEmpty(Mono.error(new NoSuchElementException()));
    }

    @GetMapping("/{name}/{id}")
    Mono<Subscription> subscription(@PathVariable String name, @PathVariable int id) {
        return subscription(name, Optional.empty())
                .filter(s -> s.getId() == id)
                .last();
    }

    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Not found")
    @ExceptionHandler(NoSuchElementException.class)
    void notFound() {
    }
}
