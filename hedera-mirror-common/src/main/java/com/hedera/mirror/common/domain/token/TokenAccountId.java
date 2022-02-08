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

import java.io.Serializable;
import javax.persistence.Convert;
import javax.persistence.Embeddable;

import com.hedera.mirror.common.domain.entity.EntityId;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.converter.TokenIdConverter;

/**
 * TokenAccount embedded Id. This needs to exist as a separate class to ensure JPAMetaModelEntityProcessor picks it up
 */
@AllArgsConstructor
@Data
@Embeddable
@NoArgsConstructor
public class TokenAccountId implements Serializable {
    private static final long serialVersionUID = -4069569824910871771L;

    @Convert(converter = TokenIdConverter.class)
    private EntityId tokenId;

    @Convert(converter = AccountIdConverter.class)
    private EntityId accountId;

    private long modifiedTimestamp;
}
