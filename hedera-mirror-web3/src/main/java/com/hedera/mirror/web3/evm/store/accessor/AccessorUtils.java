/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.store.accessor;

import com.hedera.mirror.common.domain.entity.Entity;
import java.sql.Date;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;

@UtilityClass
public class AccessorUtils {
    public static final long DEFAULT_EXPIRY_TIMESTAMP =
            TimeUnit.MILLISECONDS.toNanos(Date.valueOf("2100-1-1").getTime());

    public static Long getEntityExpiration(Entity entity) {
        if (entity.getExpirationTimestamp() != null) {
            return entity.getExpirationTimestamp();
        }

        if (entity.getCreatedTimestamp() != null && entity.getAutoRenewPeriod() != null) {
            return entity.getCreatedTimestamp() + TimeUnit.SECONDS.toNanos(entity.getAutoRenewPeriod());
        }

        return DEFAULT_EXPIRY_TIMESTAMP;
    }
}
