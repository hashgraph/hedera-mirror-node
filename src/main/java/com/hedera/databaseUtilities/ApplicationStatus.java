package com.hedera.databaseUtilities;

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

import com.hedera.mirror.CacheConfiguration;
import com.hedera.mirror.domain.ApplicationStatusPojo;
import com.hedera.mirror.domain.ApplicationStatusCode;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.*;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import javax.transaction.Transactional;

@Transactional
@CacheConfig(cacheNames = "application_status", cacheManager = CacheConfiguration.EXPIRE_AFTER_5M)
public interface ApplicationStatus extends CrudRepository<ApplicationStatusPojo, ApplicationStatusCode> {

	String EMPTY_HASH = Hex.encodeHexString(new byte[48]);

	@Cacheable(sync = true)
	default String findByStatusCode(ApplicationStatusCode statusCode) {
		return findById(statusCode).map(ApplicationStatusPojo::getStatusValue).orElse("");
	}

	@Modifying
	@CacheEvict(key = "#statusCode")
	@Query("update ApplicationStatusPojo set statusValue = ?2 where statusCode = ?1")
	void updateStatusValue(ApplicationStatusCode statusCode, String statusValue);

	default String getBypassEventHashMismatchUntilAfter() {
		return findByStatusCode(ApplicationStatusCode.EVENT_HASH_MISMATCH_BYPASS_UNTIL_AFTER);
	}

	default void updateBypassEventHashMismatchUntilAfter(String bypassUntilAfter) {
		updateStatusValue(ApplicationStatusCode.EVENT_HASH_MISMATCH_BYPASS_UNTIL_AFTER, bypassUntilAfter);
	}

	default String getBypassRecordHashMismatchUntilAfter() {
		return findByStatusCode(ApplicationStatusCode.RECORD_HASH_MISMATCH_BYPASS_UNTIL_AFTER);
	}

	default void updateBypassRecordHashMismatchUntilAfter(String bypassUntilAfter) {
		updateStatusValue(ApplicationStatusCode.RECORD_HASH_MISMATCH_BYPASS_UNTIL_AFTER, bypassUntilAfter);
	}

	default String getLastProcessedEventHash() {
		return findByStatusCode(ApplicationStatusCode.LAST_PROCESSED_EVENT_HASH);
	}

	default void updateLastProcessedEventHash(String hash) {
		if (StringUtils.isNotBlank(hash) && !hash.equals(EMPTY_HASH)) {
			updateStatusValue(ApplicationStatusCode.LAST_PROCESSED_EVENT_HASH, hash);
		}
	}

	default String getLastProcessedRecordHash() {
		return findByStatusCode(ApplicationStatusCode.LAST_PROCESSED_RECORD_HASH);
	}

	default void updateLastProcessedRecordHash(String hash) {
		if (StringUtils.isNotBlank(hash) && !hash.equals(EMPTY_HASH)) {
			updateStatusValue(ApplicationStatusCode.LAST_PROCESSED_RECORD_HASH, hash);
		}
	}

	default String getLastValidDownloadedBalanceFileName() {
		return findByStatusCode(ApplicationStatusCode.LAST_VALID_DOWNLOADED_BALANCE_FILE);
	}

	default void updateLastValidDownloadedBalanceFileName(String name) {
		updateStatusValue(ApplicationStatusCode.LAST_VALID_DOWNLOADED_BALANCE_FILE, name);
	}

	default String getLastValidDownloadedEventFileHash() {
		return findByStatusCode(ApplicationStatusCode.LAST_VALID_DOWNLOADED_EVENT_FILE_HASH);
	}

	default void updateLastValidDownloadedEventFileHash(String hash) {
		updateStatusValue(ApplicationStatusCode.LAST_VALID_DOWNLOADED_EVENT_FILE_HASH, hash);
	}

	default String getLastValidDownloadedEventFileName() {
		return findByStatusCode(ApplicationStatusCode.LAST_VALID_DOWNLOADED_EVENT_FILE);
	}

	default void updateLastValidDownloadedEventFileName(String name) {
		updateStatusValue(ApplicationStatusCode.LAST_VALID_DOWNLOADED_EVENT_FILE, name);
	}

	default String getLastValidDownloadedRecordFileName() {
		return findByStatusCode(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE);
	}

	default void updateLastValidDownloadedRecordFileName(String name) {
		updateStatusValue(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE, name);
	}

	default String getLastValidDownloadedRecordFileHash() {
		return findByStatusCode(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE_HASH);
	}

	default void updateLastValidDownloadedRecordFileHash(String hash) {
		updateStatusValue(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE_HASH, hash);
	}
}
