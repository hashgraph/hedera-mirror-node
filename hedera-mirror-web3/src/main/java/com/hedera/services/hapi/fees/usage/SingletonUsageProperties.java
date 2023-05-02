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

package com.hedera.services.hapi.fees.usage;

import static com.hedera.services.hapi.utils.fees.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.LONG_SIZE;

@SuppressWarnings("java:S6548")
public enum SingletonUsageProperties implements UsageProperties {
    USAGE_PROPERTIES;

    @Override
    public int accountAmountBytes() {
        return LONG_SIZE + BASIC_ENTITY_ID_SIZE;
    }

    @Override
    public int nftTransferBytes() {
        return LONG_SIZE + 2 * BASIC_ENTITY_ID_SIZE;
    }

    @Override
    public long legacyReceiptStorageSecs() {
        return 180;
    }
}
