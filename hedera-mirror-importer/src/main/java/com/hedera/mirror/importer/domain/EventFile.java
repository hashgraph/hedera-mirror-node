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

import com.hedera.mirror.importer.converter.AccountIdConverter;

@Data
@NoArgsConstructor
public class EventFile implements StreamFile {

    private Long count;

    private int fileVersion;

    private String hash;

    private Long loadEnd;

    private Long loadStart;

    private String name;

    @Convert(converter = AccountIdConverter.class)
    private EntityId nodeAccountId;

    private String previousHash;

    @Override
    public String getFileHash() {
        return hash;
    }
}
