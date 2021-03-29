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

import java.util.ArrayList;
import java.util.List;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import com.hedera.mirror.importer.converter.AccountIdConverter;

@Builder
@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
public class AccountBalanceFile implements StreamFile {

    @ToString.Exclude
    private byte[] bytes;

    @Id
    private Long consensusTimestamp;

    private Long count;

    @ToString.Exclude
    private String fileHash;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Transient
    private List<AccountBalance> items = new ArrayList<>();

    private Long loadEnd;

    private Long loadStart;

    private String name;

    @Convert(converter = AccountIdConverter.class)
    private EntityId nodeAccountId;

    @Override
    public Long getConsensusEnd() {
        return consensusTimestamp;
    }

    @Override
    public Long getConsensusStart() {
        return consensusTimestamp;
    }

    @Override
    public void setConsensusStart(Long timestamp) {
        consensusTimestamp = timestamp;
    }

    @Override
    public StreamType getType() {
        return StreamType.BALANCE;
    }
}
