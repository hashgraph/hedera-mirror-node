/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.common.domain.job;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReconciliationStatus {
    UNKNOWN(""),
    RUNNING(""),
    SUCCESS(""),
    FAILURE_CRYPTO_TRANSFERS("Crypto transfers did not reconcile in range (%d, %d]: %s"),
    FAILURE_FIFTY_BILLION("Balance file %s does not add up to 50B: %d"),
    FAILURE_TOKEN_TRANSFERS("Token transfers did not reconcile in range (%d, %d]: %s"),
    FAILURE_UNKNOWN("Unknown error");

    private final String message;
}
