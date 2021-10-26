package com.hedera.mirror.importer.domain;

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

import static com.hedera.mirror.importer.parser.record.transactionhandler.EntityOperation.CREATE;
import static com.hedera.mirror.importer.parser.record.transactionhandler.EntityOperation.DELETE;
import static com.hedera.mirror.importer.parser.record.transactionhandler.EntityOperation.NONE;
import static com.hedera.mirror.importer.parser.record.transactionhandler.EntityOperation.UPDATE;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.importer.parser.record.transactionhandler.EntityOperation;

@Getter
@RequiredArgsConstructor
public enum TransactionTypeEnum {

    UNKNOWN(-1, NONE),
    CONTRACTCALL(7, NONE),
    CONTRACTCREATEINSTANCE(8, CREATE),
    CONTRACTUPDATEINSTANCE(9, UPDATE),
    CRYPTOADDLIVEHASH(10, NONE),
    CRYPTOCREATEACCOUNT(11, CREATE),
    CRYPTODELETE(12, DELETE),
    CRYPTODELETELIVEHASH(13, NONE),
    CRYPTOTRANSFER(14, NONE),
    CRYPTOUPDATEACCOUNT(15, UPDATE),
    FILEAPPEND(16, NONE),
    FILECREATE(17, CREATE),
    FILEDELETE(18, DELETE),
    FILEUPDATE(19, UPDATE),
    SYSTEMDELETE(20, DELETE),
    SYSTEMUNDELETE(21, UPDATE),
    CONTRACTDELETEINSTANCE(22, DELETE),
    FREEZE(23, NONE),
    CONSENSUSCREATETOPIC(24, CREATE),
    CONSENSUSUPDATETOPIC(25, UPDATE),
    CONSENSUSDELETETOPIC(26, DELETE),
    CONSENSUSSUBMITMESSAGE(27, NONE),
    UNCHECKEDSUBMIT(28, NONE),
    TOKENCREATION(29, CREATE),
    TOKENFREEZE(31, NONE),
    TOKENUNFREEZE(32, NONE),
    TOKENGRANTKYC(33, NONE),
    TOKENREVOKEKYC(34, NONE),
    TOKENDELETION(35, DELETE),
    TOKENUPDATE(36, UPDATE),
    TOKENMINT(37, NONE),
    TOKENBURN(38, NONE),
    TOKENWIPE(39, NONE),
    TOKENASSOCIATE(40, NONE),
    TOKENDISSOCIATE(41, NONE),
    SCHEDULECREATE(42, CREATE),
    SCHEDULEDELETE(43, DELETE),
    SCHEDULESIGN(44, NONE),
    TOKENFEESCHEDULEUPDATE(45, NONE),
    TOKENPAUSE(46, NONE),
    TOKENUNPAUSE(47, NONE);

    private static final Map<Integer, TransactionTypeEnum> idMap = Arrays.stream(values())
            .collect(Collectors.toMap(TransactionTypeEnum::getProtoId, Function.identity()));

    private final int protoId;
    private final EntityOperation entityOperation;

    public static TransactionTypeEnum of(int protoId) {
        return idMap.getOrDefault(protoId, UNKNOWN);
    }
}
