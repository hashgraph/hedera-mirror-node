package com.hedera.mirror.test.e2e.acceptance.props;

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

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class MirrorTransaction {

    private List<MirrorAssessedCustomFee> assessedCustomFees = new ArrayList<>();

    private String consensusTimestamp;

    private String name;

    private String result;

    private boolean scheduled;

    private List<MirrorTokenTransfer> tokenTransfers;

    private String transactionId;

    private List<MirrorTransfer> transfers;

    private String validStartTimestamp;

}
