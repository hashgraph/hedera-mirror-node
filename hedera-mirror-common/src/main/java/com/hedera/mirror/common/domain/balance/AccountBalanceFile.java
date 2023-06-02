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

package com.hedera.mirror.common.domain.balance;

import com.hedera.mirror.common.domain.StreamFile;
import com.hedera.mirror.common.domain.StreamType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import reactor.core.publisher.Flux;

@Builder(toBuilder = true)
@Data
@Entity
@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@NoArgsConstructor
public class AccountBalanceFile implements StreamFile<AccountBalance> {

    @ToString.Exclude
    private byte[] bytes;

    @Id
    private Long consensusTimestamp;

    private Long count;

    @ToString.Exclude
    private String fileHash;

    @Builder.Default
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Transient
    private Flux<AccountBalance> items = Flux.empty();

    private Long loadEnd;

    private Long loadStart;

    private String name;

    private Long nodeId;

    private int timeOffset;

    @Override
    public StreamFile<AccountBalance> copy() {
        return this.toBuilder().build();
    }

    @Override
    public Long getConsensusStart() {
        return consensusTimestamp;
    }

    @Override
    public Long getConsensusEnd() {
        return getConsensusStart();
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
