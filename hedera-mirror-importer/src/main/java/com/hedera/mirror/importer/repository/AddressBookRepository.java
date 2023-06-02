/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.repository;

import com.hedera.mirror.common.domain.addressbook.AddressBook;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface AddressBookRepository extends CrudRepository<AddressBook, Long> {
    @Query(
            value = "select * from address_book where start_consensus_timestamp <= ?1 and file_id = ?2 order by "
                    + "start_consensus_timestamp desc limit 1",
            nativeQuery = true)
    Optional<AddressBook> findLatest(long consensusTimestamp, long encodedFileId);
}
