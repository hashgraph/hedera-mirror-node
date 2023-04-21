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

package com.hedera.mirror.monitor.subscribe.rest.response;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.hedera.mirror.monitor.converter.StringToInstantDeserializer;
import com.hedera.mirror.monitor.publish.transaction.TransactionType;
import java.time.Instant;
import lombok.Data;

@Data
public class MirrorTransaction {

    @JsonDeserialize(using = StringToInstantDeserializer.class)
    private Instant consensusTimestamp;

    private TransactionType name;

    private String result;

    private boolean scheduled;

    @JsonDeserialize(using = StringToInstantDeserializer.class)
    private Instant validStartTimestamp;
}
