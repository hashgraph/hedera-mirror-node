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

package com.hedera.mirror.importer.converter;

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;

import com.hedera.mirror.common.domain.entity.EntityId;
import lombok.SneakyThrows;
import org.springframework.boot.configurationprocessor.json.JSONObject;

public abstract class AbstractFeeConverter {

    protected static final String ALL_COLLECTORS_ARE_EXEMPT = "all_collectors_are_exempt";
    protected static final String AMOUNT = "amount";
    protected static final String COLLECTOR_ACCOUNT_ID = "collector_account_id";
    protected static final String DENOMINATING_TOKEN_ID = "denominating_token_id";
    protected static final String DENOMINATOR = "denominator";
    protected static final String FALLBACK_FEE = "fallback_fee";
    protected static final String MAXIMUM_AMOUNT = "maximum_amount";
    protected static final String MINIMUM_AMOUNT = "minimum_amount";
    protected static final String NET_OF_TRANSFERS = "net_of_transfers";

    @SneakyThrows
    protected EntityId getCollectorAccountId(JSONObject item) {
        return item.isNull(COLLECTOR_ACCOUNT_ID) ? null : EntityId.of(item.getLong(COLLECTOR_ACCOUNT_ID), ACCOUNT);
    }

    @SneakyThrows
    protected EntityId getDenominatingTokenId(JSONObject item) {
        return item.isNull(DENOMINATING_TOKEN_ID) ? null : EntityId.of(item.getLong(DENOMINATING_TOKEN_ID), TOKEN);
    }
}
