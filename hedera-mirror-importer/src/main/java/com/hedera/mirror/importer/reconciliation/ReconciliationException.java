package com.hedera.mirror.importer.reconciliation;

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

import lombok.Getter;

import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.reconciliation.BalanceReconciliationService.ReconciliationStatus;

@Getter
class ReconciliationException extends ImporterException {

    private static final long serialVersionUID = -1037307345641558766L;

    private final ReconciliationStatus status;

    ReconciliationException(ReconciliationStatus status, Object... arguments) {
        super(String.format(status.getMessage(), arguments));
        this.status = status;
    }
}
