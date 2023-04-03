/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger.properties;

public enum TokenProperty {
    TOTAL_SUPPLY,
    ADMIN_KEY,
    FREEZE_KEY,
    KYC_KEY,
    PAUSE_KEY,
    SUPPLY_KEY,
    FEE_SCHEDULE_KEY,
    WIPE_KEY,
    IS_DELETED,
    IS_PAUSED,
    SYMBOL,
    NAME,
    TREASURY,
    ACC_FROZEN_BY_DEFAULT,
    ACC_KYC_GRANTED_BY_DEFAULT,
    EXPIRY,
    AUTO_RENEW_PERIOD,
    AUTO_RENEW_ACCOUNT,
    MEMO,
    LAST_USED_SERIAL_NUMBER,
    TOKEN_TYPE,
    SUPPLY_TYPE,
    MAX_SUPPLY,
    FEE_SCHEDULE,
    DECIMALS
}
