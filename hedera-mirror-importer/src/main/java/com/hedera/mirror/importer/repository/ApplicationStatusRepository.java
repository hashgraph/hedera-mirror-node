package com.hedera.mirror.importer.repository;

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

import javax.transaction.Transactional;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import com.hedera.mirror.importer.config.CacheConfiguration;
import com.hedera.mirror.importer.domain.ApplicationStatus;
import com.hedera.mirror.importer.domain.ApplicationStatusCode;

@Transactional
@CacheConfig(cacheNames = "application_status", cacheManager = CacheConfiguration.EXPIRE_AFTER_5M)
public interface ApplicationStatusRepository extends CrudRepository<ApplicationStatus, ApplicationStatusCode> {

    @Cacheable(sync = true)
    default String findByStatusCode(ApplicationStatusCode statusCode) {
        return findById(statusCode).map(ApplicationStatus::getStatusValue).orElse("");
    }

    @Modifying
    @CacheEvict(key = "#p0")
    @Query("update ApplicationStatus set statusValue = :value where statusCode = :code")
    void updateStatusValue(@Param("code") ApplicationStatusCode statusCode, @Param("value") String statusValue);
}
