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

package com.hedera.mirror.importer.parser.balance;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.parser.AbstractParserProperties;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component("balanceParserProperties")
@Data
@Validated
@ConfigurationProperties("hedera.mirror.importer.parser.balance")
public class BalanceParserProperties extends AbstractParserProperties {

    public BalanceParserProperties() {
        queueCapacity = 0;
        retry.setMaxAttempts(3);
        transactionTimeout = Duration.ofSeconds(300);
    }

    @Min(1)
    private int batchSize = 200_000;

    @Min(1)
    private int fileBufferSize = 200_000;

    @Override
    public StreamType getStreamType() {
        return StreamType.BALANCE;
    }
}
