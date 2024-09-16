/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.utils;

import com.hedera.hapi.node.base.SemanticVersion;
import java.util.Comparator;

public class EvmVersionsComparator {

    public static int compare(final SemanticVersion version1, final SemanticVersion version2) {
        if (version1 == version2) {
            return 0;
        }

        return Comparator.comparing(SemanticVersion::major)
                .thenComparing(SemanticVersion::minor)
                .thenComparing(SemanticVersion::patch)
                .thenComparing(SemanticVersion::pre, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(SemanticVersion::build, Comparator.nullsFirst(Comparator.naturalOrder()))
                .compare(version1, version2);
    }
}
