package com.hedera.mirror.test.e2e.acceptance.props;

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
import java.util.List;
import lombok.Data;

@Data
public class MirrorTransaction {
    @JsonProperty("consensus_timestamp")
    private String consensusTimestamp;

    @JsonProperty("transaction_hash")
    private String transactionHash;

    @JsonProperty("valid_start_timestamp")
    private String validStartTime;

    @JsonProperty("charged_tx_fee")
    private int chargedTxFee;

    @JsonProperty("memo_base64")
    private String memo;

    private String result;

    private String name;

    @JsonProperty("max_fee")
    private int maxFee;

    @JsonProperty("valid_duration_seconds")
    private String validDurationSeconds;

    private String node;

    @JsonProperty("transaction_id")
    private String transactionId;

    List<MirrorTransfer> transfers;

    @JsonProperty("token_transfers")
    List<MirrorTokenTransfer> tokenTransfers;
}
