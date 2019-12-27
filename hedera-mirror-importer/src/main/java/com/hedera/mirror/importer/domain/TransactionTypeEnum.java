package com.hedera.mirror.importer.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TransactionTypeEnum {

    CONTRACTCALL(7),
    CONTRACTCREATEINSTANCE(8),
    CONTRACTUPDATEINSTANCE(9),
    CRYPTOADDCLAIM(10),
    CRYPTOCREATEACCOUNT(11),
    CRYPTODELETE(12),
    CRYPTODELETECLAIM(13),
    CRYPTOTRANSFER(14),
    CRYPTOUPDATEACCOUNT(15),
    FILEAPPEND(16),
    FILECREATE(17),
    FILEDELETE(18),
    FILEUPDATE(19),
    SYSTEMDELETE(20),
    SYSTEMUNDELETE(21),
    CONTRACTDELETEINSTANCE(22),
    FREEZE(23),
    CONSENSUSCREATETOPIC(24),
    CONSENSUSUPDATETOPIC(25),
    CONSENSUSDELETETOPIC(26),
    CONSENSUSSUBMITMESSAGE(27);

    private final int protoId;

    public static TransactionTypeEnum of(int protoId) {
        for (TransactionTypeEnum transactionTypeEnum : values()) {
            if (transactionTypeEnum.getProtoId() == protoId) {
                return transactionTypeEnum;
            }
        }
        return null;
    }
}
