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

package com.hedera.mirror.restjava.utils;

import java.util.Map;

public class Operator {

    public static final String LT = "lt";
    public static final String LTE = "lte";
    public static final String GT = "gt";
    public static final String GTE = "gte";

    public static final String EQ = "eq";
    public static final String NE = "ne";
    private static final Map<String, String> opsMap = Map.of(
            LT, "<",
            LTE, "<=",
            GT, ">",
            GTE, ">=",
            EQ, "=",
            NE, "!=");
}
