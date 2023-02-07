package com.hedera.mirror.common.converter;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import java.math.BigInteger;
import org.apache.commons.lang3.ArrayUtils;

public class WeiBarTinyBarConverter {

    public static final WeiBarTinyBarConverter INSTANCE = new WeiBarTinyBarConverter();
    public static final Long WEIBARS_TO_TINYBARS = 10_000_000_000L;
    public static final BigInteger WEIBARS_TO_TINYBARS_BIGINT = BigInteger.valueOf(WEIBARS_TO_TINYBARS);

    public byte[] convert(byte[] weibar, boolean signed) {
        if (ArrayUtils.isEmpty(weibar)) {
            return weibar;
        }

        var bigInteger = signed ? new BigInteger(weibar) : new BigInteger(1, weibar);
        return bigInteger
                .divide(WEIBARS_TO_TINYBARS_BIGINT)
                .toByteArray();
    }

    public Long convert(Long weibar) {
        return weibar == null ? null : weibar / WEIBARS_TO_TINYBARS;
    }
}
