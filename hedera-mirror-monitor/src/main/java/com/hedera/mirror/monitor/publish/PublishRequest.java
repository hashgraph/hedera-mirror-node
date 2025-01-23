/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.monitor.publish;

import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.mirror.monitor.NodeProperties;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Builder(toBuilder = true)
@Data
public class PublishRequest {
    private final boolean receipt;
    private final boolean sendRecord;
    private final PublishScenario scenario;
    private final Instant timestamp;
    private final Transaction<?> transaction;
    private NodeProperties node;
}
