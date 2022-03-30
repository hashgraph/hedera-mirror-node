package com.hedera.mirror.common.domain.token;

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

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import javax.persistence.Convert;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.domain.Upsertable;
import com.hedera.mirror.common.domain.entity.EntityId;

@Data
@Entity
@NoArgsConstructor
@Upsertable
public class Nft {

    @JsonUnwrapped
    @EmbeddedId
    private NftId id;

    @Convert(converter = AccountIdConverter.class)
    private EntityId accountId;

    private Long allowanceGrantedTimestamp;

    private Long createdTimestamp;

    @Convert(converter = AccountIdConverter.class)
    private EntityId delegatingSpender;

    private Boolean deleted;

    private byte[] metadata;

    private Long modifiedTimestamp;

    @Convert(converter = AccountIdConverter.class)
    private EntityId spender;

    public Nft(long serialNumber, EntityId tokenId) {
        id = new NftId(serialNumber, tokenId);
    }
}
