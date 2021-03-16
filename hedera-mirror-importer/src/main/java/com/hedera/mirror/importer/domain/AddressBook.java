package com.hedera.mirror.importer.domain;

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

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.persistence.CascadeType;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import com.hedera.mirror.importer.converter.FileIdConverter;

@Builder(toBuilder = true)
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class AddressBook {
    // consensusTimestamp + 1ns of transaction containing final fileAppend operation
    @Id
    private Long startConsensusTimestamp;

    // consensusTimestamp of transaction containing final fileAppend operation of next address book
    private Long endConsensusTimestamp;

    @Convert(converter = FileIdConverter.class)
    private EntityId fileId;

    private Integer nodeCount;

    @ToString.Exclude
    private byte[] fileData;

    @ToString.Exclude
    @Transient
    @Getter(lazy = true)
    private final Map<String, PublicKey> nodeAccountIDPubKeyMap = this.getEntries()
            .stream()
            .collect(Collectors
                    .toMap(AddressBookEntry::getNodeAccountIdString, AddressBookEntry::getPublicKeyAsObject));

    @OneToMany(cascade = {CascadeType.ALL}, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "consensusTimestamp")
    private List<AddressBookEntry> entries = new ArrayList<>();

    public Set<EntityId> getNodeSet() {
        return entries.stream()
                .map(AddressBookEntry::getNodeAccountId)
                .collect(Collectors.toSet());
    }
}
