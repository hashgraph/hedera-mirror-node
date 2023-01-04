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

import static com.hedera.mirror.graphql.util.GraphqlUtils.toEntityId;
import static com.hedera.mirror.graphql.util.GraphqlUtils.validateOneOf;

import java.util.List;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.hedera.mirror.graphql.service.EntityService;
import com.hedera.mirror.graphql.viewmodel.Account;
import com.hedera.mirror.graphql.viewmodel.AccountInput;

@Controller
@CustomLog
@RequiredArgsConstructor
class AccountController {

    private final ModelMapper modelMapper;
    private final EntityService entityService;

    @QueryMapping
    Mono<Account> account(@Argument @Valid AccountInput filter) {
        final var alias = filter.getAlias();
        final var evmAddress = filter.getEvmAddress();
        final var entityId = filter.getEntityId();
        final var id = filter.getId();

        validateOneOf(alias, entityId, evmAddress, id);

        if (entityId != null) {
            return entityService.getAccountById(toEntityId(entityId))
                    .map(e -> modelMapper.map(e, Account.class));
        }

        return Mono.error(new IllegalStateException("Not implemented"));
    }

    @BatchMapping
    Flux<Account> autoRenewAccount(List<Account> accounts) {
        log.info("Loading autoRenewAccount: {}", accounts.stream().map(Account::getId)
                .collect(Collectors.toList()));
        return Flux.fromIterable(accounts).map(a -> account(a.getId() + "1"));
    }

    @BatchMapping
    Flux<Account> proxyAccount(List<Account> accounts) {
        log.info("Loading proxyAccount: {}", accounts.stream().map(Account::getId)
                .collect(Collectors.toList()));
        return Flux.fromIterable(accounts).map(a -> account(a.getId() + "1"));
    }

    private Account account(String id) {
        var account = new Account();
        account.setId(id);
        return account;
    }
}
