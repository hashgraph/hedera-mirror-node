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

package com.hedera.mirror.common.domain.addressbook;

import com.hedera.mirror.common.converter.FileIdConverter;
import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder(toBuilder = true)
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
public class AddressBook {
    // consensusTimestamp + 1ns of transaction containing final fileAppend operation
    @Id
    private Long startConsensusTimestamp;

    // consensusTimestamp of transaction containing final fileAppend operation of next address book
    private Long endConsensusTimestamp;

    @EqualsAndHashCode.Exclude
    @Builder.Default
    @OneToMany(
            cascade = {CascadeType.ALL},
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    @JoinColumn(name = "consensusTimestamp")
    private List<AddressBookEntry> entries = new ArrayList<>();

    @ToString.Exclude
    private byte[] fileData;

    @Convert(converter = FileIdConverter.class)
    private EntityId fileId;

    private Integer nodeCount;
}
