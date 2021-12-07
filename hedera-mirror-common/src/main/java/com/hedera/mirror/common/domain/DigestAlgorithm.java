package com.hedera.mirror.common.domain;

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

import lombok.Getter;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;

@Getter
public enum DigestAlgorithm {
    SHA384("SHA-384", 48, 0x58ff811b);

    private final String name;
    private final int size;
    private final int type; // as defined in the stream file v5 format document
    private final String emptyHash;

    DigestAlgorithm(String name, int size, int type) {
        this.name = name;
        this.size = size;
        this.type = type;
        this.emptyHash = Hex.encodeHexString(new byte[size]);
    }

    public boolean isHashEmpty(String hash) {
        return StringUtils.isEmpty(hash) || hash.equals(emptyHash);
    }
}
