package com.hedera.mirror.common.domain.entity;

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

import javax.persistence.Column;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import com.hedera.mirror.common.domain.Aliasable;

@Data
@javax.persistence.Entity
@NoArgsConstructor
@SuperBuilder
public class Entity extends AbstractEntity implements Aliasable  {

    @Column(updatable = false)
    @ToString.Exclude
    private byte[] alias;

    private Long ethereumNonce;

    private Boolean receiverSigRequired;

    @ToString.Exclude
    private byte[] submitKey;
}
