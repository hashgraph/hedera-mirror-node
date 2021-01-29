package com.hedera.mirror.importer;

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

import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

// Clears all caches before each test is run (equivalent of @BeforeEach).
public class ResetCacheTestExecutionListener implements TestExecutionListener {
    @Override
    public void beforeTestMethod(TestContext testContext) {
        testContext.getApplicationContext().getBeansOfType(CacheManager.class).forEach(
                (cacheManagerName, cacheManager) ->
                        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear())
        );
    }
}
