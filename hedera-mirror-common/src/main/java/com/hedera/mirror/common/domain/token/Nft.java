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

package com.hedera.mirror.common.domain.token;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.domain.UpsertColumn;
import com.hedera.mirror.common.domain.Upsertable;
import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Upsertable
public class Nft {

    @JsonUnwrapped
    @EmbeddedId
    private NftId id;

    @UpsertColumn(coalesce = "case when deleted = true then null else coalesce({0}, e_{0}, {1}) end")
    @Convert(converter = AccountIdConverter.class)
    private EntityId accountId;

    @Column(updatable = false)
    private Long createdTimestamp;

    @Convert(converter = AccountIdConverter.class)
    @UpsertColumn(coalesce = "{0}")
    private EntityId delegatingSpender;

    private Boolean deleted;

    @Column(updatable = false)
    private byte[] metadata;

    private Long modifiedTimestamp;

    @Convert(converter = AccountIdConverter.class)
    @UpsertColumn(coalesce = "{0}")
    private EntityId spender;

    public Nft(long serialNumber, EntityId tokenId) {
        id = new NftId(serialNumber, tokenId);
    }
}
