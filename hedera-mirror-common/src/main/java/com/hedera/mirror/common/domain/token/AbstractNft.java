/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.common.domain.token;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.History;
import com.hedera.mirror.common.domain.UpsertColumn;
import com.hedera.mirror.common.domain.Upsertable;
import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.persistence.Column;
import jakarta.persistence.IdClass;
import jakarta.persistence.MappedSuperclass;
import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Data
@IdClass(AbstractNft.Id.class)
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractNft implements History {

    @UpsertColumn(coalesce = "case when deleted = true then null else coalesce({0}, e_{0}, {1}) end")
    private EntityId accountId;

    @Column(updatable = false)
    private Long createdTimestamp;

    @UpsertColumn(coalesce = "{0}")
    private EntityId delegatingSpender;

    private Boolean deleted;

    @Column(updatable = false)
    @ToString.Exclude
    private byte[] metadata;

    @jakarta.persistence.Id
    private long serialNumber;

    @UpsertColumn(coalesce = "{0}")
    private EntityId spender;

    @jakarta.persistence.Id
    private long tokenId;

    private Range<Long> timestampRange;

    @JsonIgnore
    public AbstractNft.Id getId() {
        return new Id(serialNumber, tokenId);
    }

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = 8679156797431231527L;

        private long serialNumber;
        private long tokenId;
    }
}
