package com.hedera.mirror.importer.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.EntityId;

public interface AddressBookRepository extends CrudRepository<AddressBook, Long> {
    @Query(value = "select * from address_book where consensus_timestamp <= ?1 and file_id = ?2 order by " +
            "consensus_timestamp desc limit 1", nativeQuery = true)
    Optional<AddressBook> findLatestAddressBook(long consensusTimestamp, long encodedFileId);

    @Query("from AddressBook where consensusTimestamp <= ?1 and fileId = ?2 order by " +
            "consensusTimestamp asc")
    List<AddressBook> findLatestAddressBooks(long consensusTimestamp, EntityId fileId);
}
