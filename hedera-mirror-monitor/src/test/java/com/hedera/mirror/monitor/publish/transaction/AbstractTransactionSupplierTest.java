package com.hedera.mirror.monitor.publish.transaction;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.ScheduleId;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TopicId;

public abstract class AbstractTransactionSupplierTest {
    protected static final AccountId ACCOUNT_ID = AccountId.fromString("0.0.3");

    protected static final AccountId ACCOUNT_ID_2 = AccountId.fromString("0.0.4");

    protected static final Hbar MAX_TRANSACTION_FEE_HBAR = Hbar.fromTinybars(1_000_000_000);

    protected static final Hbar ONE_TINYBAR = Hbar.fromTinybars(1);

    protected static final ScheduleId SCHEDULE_ID = ScheduleId.fromString("0.0.30");

    protected static final TokenId TOKEN_ID = TokenId.fromString("0.0.10");

    protected static final TopicId TOPIC_ID = TopicId.fromString("0.0.20");
}
