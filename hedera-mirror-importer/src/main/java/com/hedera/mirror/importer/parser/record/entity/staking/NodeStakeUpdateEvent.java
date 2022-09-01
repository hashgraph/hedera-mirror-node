package com.hedera.mirror.importer.parser.record.entity.staking;

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

import java.io.Serial;
import org.springframework.context.ApplicationEvent;

public class NodeStakeUpdateEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = -1825194602305052810L;

    /**
     * Create a new {@code NodeStakeUpdateEvent}.
     *
     * @param source the object on which the event initially occurred or with which the event is associated (never
     *               {@code null})
     */
    public NodeStakeUpdateEvent(Object source) {
        super(source);
    }
}
