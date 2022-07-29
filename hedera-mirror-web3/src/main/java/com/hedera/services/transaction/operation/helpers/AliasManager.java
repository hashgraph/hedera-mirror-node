/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.transaction.operation.helpers;

import com.google.protobuf.ByteString;
import javax.inject.Inject;
import java.util.Map;
import java.util.function.Supplier;

//FUTURE WORK
public class AliasManager {
    private final Supplier<Map<ByteString, EntityNum>> aliases;

    @Inject
    public AliasManager(final Supplier<Map<ByteString, EntityNum>> aliases) {
        this.aliases = aliases;
    }

    public void link(final ByteString alias, final EntityNum num) {
        curAliases().put(alias, num);
    }

    private Map<ByteString, EntityNum> curAliases() {
        return aliases.get();
    }
}
