/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2020 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record;

import com.hedera.mirror.importer.repository.EntityIdRepository;

import lombok.NoArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;

@Component
@NoArgsConstructor
public class EntityIdCacheLoader implements InitializingBean {
    @Resource
    EntityIdRepository entityIdRepository;

    @Override
    public void afterPropertiesSet() {
        entityIdRepository.findAll(); // Seed the cache
    }
}
