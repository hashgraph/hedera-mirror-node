/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.tuweni.bytes.Bytes;
import org.springframework.util.StringUtils;

public record TransactionHashParameter(Bytes hash) implements TransactionIdOrHashParameter {

    private static final Pattern ETH_HASH_PATTERN = Pattern.compile("^(0x)?([0-9A-Fa-f]{64})$");

    public static TransactionHashParameter valueOf(String hash) {
        if (!isValidEthHash(hash)) {
            return null;
        }
        return new TransactionHashParameter(Bytes.fromHexString(hash));
    }

    private static boolean isValidEthHash(String hash) {
        if (!StringUtils.hasText(hash)) {
            return false;
        }

        Matcher matcher = ETH_HASH_PATTERN.matcher(hash);
        return matcher.matches();
    }
}
