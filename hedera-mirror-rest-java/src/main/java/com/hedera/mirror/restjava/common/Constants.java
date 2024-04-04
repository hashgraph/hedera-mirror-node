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

import lombok.experimental.UtilityClass;
import org.apache.commons.codec.binary.Base32;

@UtilityClass
public class Constants {

    public static final String ACCOUNT_ID = "account.id";
    public static final Base32 BASE32 = new Base32();

    public static final int MAX_LIMIT = 100;
    public static final String DEFAULT_LIMIT = "25";

    public static final String HEX_PREFIX = "0x";

    public static final String OWNER = "owner";

    public static final long NANOS_PER_SECOND = 1000_000_000L;
    public static final int NANO_DIGITS = 9;

    public static final String TOKEN_ID = "token.id";
}
