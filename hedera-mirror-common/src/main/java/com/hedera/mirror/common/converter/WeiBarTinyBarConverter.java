package com.hedera.mirror.common.converter;

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

import java.math.BigInteger;

public class WeiBarTinyBarConverter {
    public static final WeiBarTinyBarConverter INSTANCE = new WeiBarTinyBarConverter();
    public static final Long WEIBARS_TO_TINYBARS = 10_000_000_000L;
    public static final BigInteger WEIBARS_TO_TINYBARS_BIGINT = BigInteger.valueOf(WEIBARS_TO_TINYBARS);

    public byte[] weiBarToTinyBar(byte[] weibar) {
        return weibar == null ? null : new BigInteger(weibar).divide(WEIBARS_TO_TINYBARS_BIGINT).toByteArray();
    }

    public Long weiBarToTinyBar(Long weibar) {
        return weibar == null ? null : weibar / WEIBARS_TO_TINYBARS;
    }
}
