package com.hedera.mirror.graphql.controller;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
import lombok.CustomLog;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.hedera.mirror.graphql.viewmodel.Account;

@Controller
@CustomLog
class AccountController {

//    AccountController(BatchLoaderRegistry registry) {
//        registry.forTypePair(String.class, Account.class)
//                .registerBatchLoader((ids, env) -> {
//                            log.info("Accounts: {}", ids);
//                            return Flux.fromIterable(ids).map(a -> {
//                                var b = new Account();
//                                b.setId(a + "1");
//                                return b;
//                            });
//                        }
//                );
//    }

    @QueryMapping
    Flux<Account> accounts() {
        var account = new Account();
        account.setId("0.0.2");
        var account2 = new Account();
        account2.setId("0.0.3");
        var account3 = new Account();
        account3.setId("0.0.2");
        return Flux.just(account, account2, account3);
    }

    @SchemaMapping
    Mono<Account> autoRenewAccount(Account account) {
        var account2 = new Account();
        account2.setId("0.0.4");
        return Mono.just(account2);
    }

//    @SchemaMapping
//    public CompletableFuture<Account> author(Account account, DataLoader<String, Account> loader) {
//        return loader.load(account.getId());
//    }

    //@BatchMapping
    Flux<Account> proxyAccount(List<Account> accounts) {
        log.info("Accounts: {}", accounts);
        return Flux.fromIterable(accounts).map(a -> {
            var b = new Account();
            b.setId(a.getId() + "1");
            return b;
        });
    }
}
