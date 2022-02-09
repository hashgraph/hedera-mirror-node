package com.hedera.mirror.grpc.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.common.domain.addressbook.AddressBookEntry;
import com.hedera.mirror.grpc.config.CacheConfiguration;

public interface AddressBookEntryRepository extends CrudRepository<AddressBookEntry, AddressBookEntry.Id> {

    @Cacheable(cacheManager = CacheConfiguration.ADDRESS_BOOK_ENTRY_CACHE, cacheNames = "address_book_entry",
            unless = "#result == null or #result.size() == 0")
    @Query(value = "select * from address_book_entry where consensus_timestamp = ? and node_id >= ? " +
            "order by node_id asc limit ?", nativeQuery = true)
    List<AddressBookEntry> findByConsensusTimestampAndNodeId(long consensusTimestamp, long nodeId, int limit);
}
