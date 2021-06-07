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

import java.io.Serializable;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.hedera.mirror.importer.converter.TokenIdConverter;

/**
 * Token table embedded Id. This needs to exist as a separate class to ensure JPAMetaModelEntityProcessor picks it up
 */
@Data
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class TokenId implements Serializable {
    private static final long serialVersionUID = -4595724698253758379L;

    @Convert(converter = TokenIdConverter.class)
    private EntityId tokenId;
}
