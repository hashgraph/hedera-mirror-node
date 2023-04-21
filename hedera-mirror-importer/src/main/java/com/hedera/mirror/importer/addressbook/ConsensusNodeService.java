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

package com.hedera.mirror.importer.addressbook;

import java.util.Collection;

/**
 * Maintains state about the current consensus nodes.
 */
public interface ConsensusNodeService {

    /**
     * Retrieves a list of consensus nodes. The data may be cached and not always reflect the current state of the
     * database.
     *
     * @return an unmodifiable list of consensus nodes
     */
    Collection<ConsensusNode> getNodes();

    /**
     * Requests that the service refreshes its node information. The implementation may choose to ignore this or
     * execute it lazily on the next request.
     */
    void refresh();
}
