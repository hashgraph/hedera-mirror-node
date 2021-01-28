package com.hedera.mirror.importer.parser.record;

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

import static org.junit.jupiter.api.Assertions.*;

import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.parser.record.entity.EntityRecordItemListener;
import com.hedera.mirror.importer.parser.record.entity.sql.SqlEntityListener;
import com.hedera.mirror.importer.parser.record.pubsub.PubSubRecordItemListener;
import com.hedera.mirror.importer.parser.record.pubsub.PubSubRecordStreamFileListener;

@SpringBootTest(properties = {
        "hedera.mirror.importer.parser.record.enabled=false",
        "hedera.mirror.importer.parser.record.entity.enabled=true",
        "hedera.mirror.importer.parser.record.pubsub.enabled=true"})
public class RecordParserDisabledTest extends IntegrationTest {
    @Resource
    ApplicationContext context;

    @Test
    public void testNoRecordParserBeans() {
        assertBeanNotPresent(RecordFileParser.class);
        assertBeanNotPresent(PubSubRecordItemListener.class);
        assertBeanNotPresent(PubSubRecordStreamFileListener.class);
        assertBeanNotPresent(EntityRecordItemListener.class);
        assertBeanNotPresent(SqlEntityListener.class);
    }

    private void assertBeanNotPresent(Class clazz) {
        assertThrows(NoSuchBeanDefinitionException.class, () -> {
            context.getBean(clazz);
        });
    }
}

