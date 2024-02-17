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

package com.hedera.mirror.restjava.common;

import org.springframework.data.domain.Sort;

public class Utils {

    private static final String TOKEN_ID = "token_id";
    private static final String OWNER = "owner";
    private static final String SPENDER = "spender";

    public static final Sort OWNER_TOKEN_ASC_ORDER = sort(Sort.Direction.ASC, OWNER);
    public static final Sort OWNER_TOKEN_DESC_ORDER = sort(Sort.Direction.DESC, OWNER);

    public static final Sort SPENDER_TOKEN_ASC_ORDER = sort(Sort.Direction.ASC, SPENDER);
    public static final Sort SPENDER_TOKEN_DESC_ORDER = sort(Sort.Direction.DESC, SPENDER);

    private static Sort sort(Sort.Direction direction, String account) {
        return Sort.by(direction, account).and(Sort.by(direction, TOKEN_ID));
    }
}
