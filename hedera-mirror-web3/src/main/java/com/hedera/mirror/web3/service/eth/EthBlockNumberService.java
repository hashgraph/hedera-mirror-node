package com.hedera.mirror.web3.service.eth;

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

import javax.inject.Named;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.web3.controller.Web3Method;
import com.hedera.mirror.web3.repository.RecordFileRepository;
import com.hedera.mirror.web3.service.Web3Service;

@Named
@RequiredArgsConstructor
public class EthBlockNumberService implements Web3Service<Object, String> {

    private static final String DEFAULT = "0";
    private static final String PREFIX = "0x";

    private final RecordFileRepository recordFileRepository;

    @Override
    public Web3Method getMethod() {
        return Web3Method.ETH_BLOCKNUMBER;
    }

    @Override
    public String get(Object request) {
        return PREFIX + recordFileRepository.findLatestIndex().map(Long::toHexString).orElse(DEFAULT);
    }
}
