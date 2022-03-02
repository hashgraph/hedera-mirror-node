package com.hedera.mirror.importer.exception;

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

public class AliasNotFoundException extends ImporterException {

    private static final long serialVersionUID = 262691996461413516L;

    public AliasNotFoundException(String alias) {
        super(getMessage(alias));
    }

    private static String getMessage(String alias) {
        return String.format("Account with alias '%s' not found", alias);
    }
}
