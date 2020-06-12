package com.hedera.mirror.importer.domain;

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

import java.time.Instant;
import java.util.List;
import lombok.Data;

import com.hedera.mirror.importer.fileencoding.event.Transaction;
import com.hedera.mirror.importer.util.Utility;

@Data
public class EventItem {
    private Instant consensusTimeStamp;
    private Long consensusOrder;
    private Long creatorId;
    private Long creatorSeq;
    private Instant timeCreated;
    private Long otherId;
    private Long otherSeq;
    private Long selfParentGen;
    private Long otherParentGen;
    private byte[] selfParentHash;
    private byte[] otherParentHash;
    private byte[] signature;
    private byte[] hash;
    private List<Transaction> transactions;

    @Override
    public String toString() {
        return String.format("EventItem: consensusTimeStamp: %s, consensusOrder: %s, creatorId: %s, creatorSeq: %s, " +
                        "timeCreated: %s, otherId: %s, otherSeq: %s, selfParentGen: %s, otherParentGen: %s, " +
                        "selfParentHash: %s, otherParentHash: %s, signature: %s, hash: %s, transactions: %s",
                consensusTimeStamp, consensusOrder, creatorId, creatorSeq, otherId, timeCreated, otherSeq,
                selfParentGen, otherParentGen, Utility.bytesToHex(selfParentHash), Utility.bytesToHex(otherParentHash),
                Utility.bytesToHex(signature), Utility.bytesToHex(hash), transactions);
    }
}

