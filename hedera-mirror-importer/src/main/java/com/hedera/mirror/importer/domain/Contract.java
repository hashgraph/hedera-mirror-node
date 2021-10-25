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

import javax.persistence.Convert;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.hedera.mirror.importer.converter.ContractIdConverter;
import com.hedera.mirror.importer.converter.FileIdConverter;
import com.hedera.mirror.importer.converter.UnknownIdConverter;

@Data
@javax.persistence.Entity
@NoArgsConstructor
@SuperBuilder
public class Contract extends AbstractEntity {

    @Convert(converter = FileIdConverter.class)
    private EntityId fileId;

    @Convert(converter = UnknownIdConverter.class)
    private EntityId obtainerId;

    @Convert(converter = ContractIdConverter.class)
    private EntityId parentId;
}
