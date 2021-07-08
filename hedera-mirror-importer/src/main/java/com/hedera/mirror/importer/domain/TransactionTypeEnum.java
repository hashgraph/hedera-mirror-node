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

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TransactionTypeEnum {

    UNKNOWN(-1),
    CONTRACTCALL(7),
    CONTRACTCREATEINSTANCE(8),
    CONTRACTUPDATEINSTANCE(9),
    CRYPTOADDLIVEHASH(10),
    CRYPTOCREATEACCOUNT(11),
    CRYPTODELETE(12),
    CRYPTODELETELIVEHASH(13),
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
    CONSENSUSSUBMITMESSAGE(27),
    UNCHECKEDSUBMIT(28),
    TOKENCREATION(29),
    TOKENFREEZE(31),
    TOKENUNFREEZE(32),
    TOKENGRANTKYC(33),
    TOKENREVOKEKYC(34),
    TOKENDELETION(35),
    TOKENUPDATE(36),
    TOKENMINT(37),
    TOKENBURN(38),
    TOKENWIPE(39),
    TOKENASSOCIATE(40),
    TOKENDISSOCIATE(41),
    SCHEDULECREATE(42),
    SCHEDULEDELETE(43),
    SCHEDULESIGN(44),
    TOKENFEESCHEDULEUPDATE(45);

    private static final Map<Integer, TransactionTypeEnum> idMap = Arrays.stream(values())
            .collect(Collectors.toMap(TransactionTypeEnum::getProtoId, Function.identity()));

    private final int protoId;

    public static TransactionTypeEnum of(int protoId) {
        return idMap.getOrDefault(protoId, UNKNOWN);
    }
}
