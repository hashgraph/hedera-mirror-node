package com.hedera.mirror.parser.balance;

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

import com.hedera.mirror.MirrorProperties;
import com.hedera.mirror.domain.StreamType;
import com.hedera.mirror.parser.ParserProperties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import javax.inject.Named;
import javax.validation.constraints.Min;
import java.nio.file.Path;

@Data
@Named
@ConfigurationProperties("hedera.mirror.parser.balance")
public class BalanceParserProperties implements ParserProperties {

    private final MirrorProperties mirrorProperties;

    @Min(1)
    private int batchSize = 2000;

    private boolean enabled = true;

    @Min(1)
    private int fileBufferSize = 200_000;

    private boolean useTransactions = false;

    public Path getStreamPath() {
        return mirrorProperties.getDataPath().resolve(getStreamType().getPath());
    }
    @Override
    public StreamType getStreamType() {
        return StreamType.BALANCE;
    }
}
