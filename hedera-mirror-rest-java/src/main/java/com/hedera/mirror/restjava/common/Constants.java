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

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Constants {

    public static final int MAX_LIMIT = 100;
    public static final String DEFAULT_LIMIT = "25";
    public static final String ENTITY_ID_REGEX = "^(((\\d{1,5})\\.)?((\\d{1,5})\\.)?(\\d{1,10}))$";

    public static final int EVM_ADDRESS_MIN_LENGTH = 40;

    public static final String EVM_ADDRESS_REGEX = "^(((0x)?[A-Fa-f0-9]{40})|((\\d{1,10}\\.){0,2}[A-Fa-f0-9]{40}))$";

    public static final Pattern ENTITY_ID_PATTERN = Pattern.compile(ENTITY_ID_REGEX);
    public static final Pattern EVM_ADDRESS_PATTERN = Pattern.compile(EVM_ADDRESS_REGEX);

    public static final String ACCOUNT_ID = "account.id";
    public static final String TOKEN_ID = "token.id";

    public static final String ORDER = "order";
    public static final String OWNER = "owner";
    public static final String LIMIT = "limit";

    public static final long NANOS_PER_SECOND = 1000_000_000L;
}
