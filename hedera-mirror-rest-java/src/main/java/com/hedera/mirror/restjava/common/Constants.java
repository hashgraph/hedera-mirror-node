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

@UtilityClass
public class Constants {

    public static final String ACCOUNT_ID = "account.id";
    public static final long NANOS_PER_SECOND = 1_000_000_000L;
    public static final int NANO_DIGITS = 9;

    public static final String TOKEN_ID = "token.id";
}
