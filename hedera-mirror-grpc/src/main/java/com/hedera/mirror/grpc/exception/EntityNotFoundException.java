package com.hedera.mirror.grpc.exception;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.exception.MirrorNodeException;

public class EntityNotFoundException extends MirrorNodeException {

    private static final String MESSAGE = "%s %s does not exist";
    private static final long serialVersionUID = 809036847722840635L;

    public EntityNotFoundException(EntityId entityId) {
        super(String.format(MESSAGE, entityId.getType().toDisplayString(), entityId));
    }
}
