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
import javax.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.EntityId;

public interface AddressBookRepository extends CrudRepository<AddressBook, Long> {
    @Query("from AddressBook where consensusTimestamp <= ?1 and fileId = ?2 and isComplete = true order by " +
            "consensusTimestamp asc")
    List<AddressBook> findCompleteAddressBooks(long consensusTimestamp, EntityId fileId);

    Optional<AddressBook> findTopByFileIdAndIsCompleteIsTrueOrderByConsensusTimestampDesc(EntityId fileId);

    Optional<AddressBook> findTopByFileIdAndIsCompleteIsFalseOrderByConsensusTimestampDesc(EntityId fileId);

    @Modifying
    @Transactional
    @Query("update AddressBook set endConsensusTimestamp = ?1 where consensusTimestamp = ?2")
    void updateEndConsensusTimestamp(long consensusTimestamp, long endConsensusTimestamp);
}
