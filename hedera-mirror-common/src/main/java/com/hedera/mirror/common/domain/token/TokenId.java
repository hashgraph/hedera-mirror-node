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

import com.hedera.mirror.common.converter.TokenIdConverter;
import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token table embedded Id. This needs to exist as a separate class to ensure JPAMetaModelEntityProcessor picks it up
 */
@Data
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class TokenId implements Serializable {
    private static final long serialVersionUID = -4595724698253758379L;

    @SuppressWarnings("java:S1700")
    @Convert(converter = TokenIdConverter.class)
    private EntityId tokenId;
}
