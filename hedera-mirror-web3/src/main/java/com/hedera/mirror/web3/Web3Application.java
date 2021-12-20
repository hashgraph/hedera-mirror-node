package com.hedera.mirror.web3;

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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.nativex.hint.NativeHint;
import reactor.core.scheduler.Schedulers;

@ConfigurationPropertiesScan
@EntityScan("com.hedera.mirror.common.domain")
@NativeHint(options = "--trace-object-instantiation=io.netty.handler.ssl.BouncyCastleAlpnSslUtils")
@SpringBootApplication
public class Web3Application {

    public static void main(String[] args) {
        Schedulers.enableMetrics();
        SpringApplication.run(Web3Application.class, args);
    }
}
