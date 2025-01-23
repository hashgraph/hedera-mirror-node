/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.common;

import com.hedera.mirror.web3.exception.InvalidParametersException;
import org.springframework.util.StringUtils;

public sealed interface TransactionIdOrHashParameter permits TransactionHashParameter, TransactionIdParameter {

    static TransactionIdOrHashParameter valueOf(String transactionIdOrHash) throws InvalidParametersException {
        if (!StringUtils.hasText(transactionIdOrHash)) {
            throw new InvalidParametersException("Missing transaction ID or hash");
        }

        TransactionIdOrHashParameter parameter;
        if ((parameter = TransactionHashParameter.valueOf(transactionIdOrHash)) != null) {
            return parameter;
        } else if ((parameter = TransactionIdParameter.valueOf(transactionIdOrHash)) != null) {
            return parameter;
        } else {
            throw new InvalidParametersException("Unsupported ID format: '%s'".formatted(transactionIdOrHash));
        }
    }
}
