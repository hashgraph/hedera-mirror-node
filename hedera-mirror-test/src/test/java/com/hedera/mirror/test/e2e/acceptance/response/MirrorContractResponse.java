package com.hedera.mirror.test.e2e.acceptance.response;

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

import lombok.Data;

import com.hedera.mirror.test.e2e.acceptance.props.MirrorTimestampRange;

@Data
public class MirrorContractResponse {
    private Integer autoRenewPeriod;
    private String contractId;
    private String createdTimestamp;
    private boolean deleted;
    private String expirationTimestamp;
    private String fileId;
    private String memo;
    private String obtainerId;
    private String proxyAccountId;
    private String solidityAddress;
    private String creatorAccountId;
    private String executedTimestamp;
    private String payerAccountId;
    private String scheduleId;
    private MirrorTimestampRange timestamp;
}
