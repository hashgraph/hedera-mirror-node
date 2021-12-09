package com.hedera.mirror.web3.service;

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

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Named;

import com.hedera.mirror.web3.controller.Web3Method;

@Named
public class Web3ServiceFactory {

    private final Map<Web3Method, Web3Service<?, ?>> services;

    public Web3ServiceFactory(Collection<Web3Service<?, ?>> services) {
        this.services = services.stream().collect(Collectors.toMap(Web3Service::getMethod, Function.identity()));
    }

    public <I, O> Web3Service<I, O> lookup(Web3Method method) {
        Web3Service<I, O> web3Service = (Web3Service<I, O>) services.get(method);

        if (web3Service == null) {
            throw new IllegalStateException("Missing implementation for method " + method);
        }

        return web3Service;
    }
}
