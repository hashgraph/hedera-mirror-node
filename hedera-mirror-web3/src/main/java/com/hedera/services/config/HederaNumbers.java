/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.config;

import static com.hedera.services.context.properties.PropertyNames.HEDERA_REALM;
import static com.hedera.services.context.properties.PropertyNames.HEDERA_SHARD;

import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.PropertySource;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class HederaNumbers {
    private final PropertySource properties;

    private long realm = Long.MIN_VALUE;
    private long shard = Long.MIN_VALUE;

    @Inject
    public HederaNumbers(@CompositeProps final PropertySource properties) {
        this.properties = properties;
    }

    public long realm() {
        if (realm == Long.MIN_VALUE) {
            realm = properties.getLongProperty(HEDERA_REALM);
        }
        return realm;
    }

    public long shard() {
        if (shard == Long.MIN_VALUE) {
            shard = properties.getLongProperty(HEDERA_SHARD);
        }
        return shard;
    }
}
