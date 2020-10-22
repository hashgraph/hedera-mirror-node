package com.hedera.mirror.test.e2e.acceptance.response;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import com.hedera.mirror.test.e2e.acceptance.props.MirrorKey;

@Data
public class MirrorTokenResponse {
    @JsonProperty("admin_key")
    private MirrorKey adminKey;

    @JsonProperty("auto_renew_account")
    private String autoRenewAccount;

    @JsonProperty("auto_renew_period")
    private String autoRenewPeriod;

    @JsonProperty("created_timestamp")
    private String createdTimestamp;

    private String decimals;

    @JsonProperty("expiry_timestamp")
    private String expiry_timestamp;

    @JsonProperty("freeze_default")
    private boolean freezeDefault;

    @JsonProperty("freeze_key")
    private MirrorKey freezeKey;

    @JsonProperty("initial_supply")
    private String initialSupply;

    @JsonProperty("modified_timestamp")
    private String modifiedTimestamp;

    private String name;

    @JsonProperty("kyc_key")
    private MirrorKey kycKey;

    @JsonProperty("supply_key")
    private MirrorKey supplyKey;

    private String symbol;

    @JsonProperty("token_id")
    private String tokenId;

    @JsonProperty("total_supply")
    private String totalSupply;

    @JsonProperty("treasury_account_id")
    private String treasuryAccountId;

    @JsonProperty("wipe_key")
    private MirrorKey wipeKey;
}
