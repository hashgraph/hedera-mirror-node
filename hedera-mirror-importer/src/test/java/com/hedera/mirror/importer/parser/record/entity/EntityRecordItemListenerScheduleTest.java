package com.hedera.mirror.importer.parser.record.entity;

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

import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenID;
import javax.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.repository.ScheduleRepository;
import com.hedera.mirror.importer.repository.ScheduleSignatureRepository;

public class EntityRecordItemListenerScheduleTest extends AbstractEntityRecordItemListenerTest {

    private static final Key TOKEN_REF_KEY = keyFromString(
            "0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92");
    private static final TokenID TOKEN_ID = TokenID.newBuilder().setShardNum(0).setRealmNum(0).setTokenNum(2).build();
    private static final long CREATE_TIMESTAMP = 1L;
    private static final long UPDATE_TIMESTAMP = 5L;
    private static final long EXECUTE_TIMESTAMP = 500L;

    @Resource
    protected ScheduleRepository scheduleRepository;

    @Resource
    protected ScheduleSignatureRepository scheduleSignatureRepository;

    @BeforeEach
    void before() {
        entityProperties.getPersist().setSchedules(true);
    }

    @Test
    void scheduleCreate() {

    }

    @Test
    void scheduleUpdate() {

    }

    @Test
    void scheduleExecute() {

    }

    @Test
    void scheduleSignSingleRecord() {

    }

    @Test
    void scheduleE2E() {

    }
}
