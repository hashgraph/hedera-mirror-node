package com.hedera.mirror.importer.config.condition;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AbstractConditionTest {

    @BeforeEach
    void beforeEach() {
        when(context.getEnvironment()).thenReturn(environment);
    }

    @Mock
    protected ConditionContext context;
    @Mock
    protected AnnotatedTypeMetadata metadata;
    @Mock
    protected Environment environment;

    protected void setProperty(String property, String value) {
        when(environment.getProperty(property))
                .thenReturn(value);
    }
}
