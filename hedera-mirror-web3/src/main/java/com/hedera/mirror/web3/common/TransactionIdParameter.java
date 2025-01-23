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

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.exception.InvalidParametersException;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record TransactionIdParameter(EntityId payerAccountId, Instant validStart)
        implements TransactionIdOrHashParameter {

    private static final Pattern TRANSACTION_ID_PATTERN =
            Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)-(\\d{1,19})-(\\d{1,9})$");

    public static TransactionIdParameter valueOf(String transactionId) throws InvalidParametersException {
        if (transactionId == null) {
            return null;
        }

        Matcher matcher = TRANSACTION_ID_PATTERN.matcher(transactionId);
        if (!matcher.matches()) {
            return null;
        }

        try {
            long shard = Long.parseLong(matcher.group(1));
            long realm = Long.parseLong(matcher.group(2));
            long num = Long.parseLong(matcher.group(3));
            long seconds = Long.parseLong(matcher.group(4));
            int nanos = Integer.parseInt(matcher.group(5));

            EntityId entityId = EntityId.of(shard, realm, num);
            Instant validStart = Instant.ofEpochSecond(seconds, nanos);

            return new TransactionIdParameter(entityId, validStart);
        } catch (Exception e) {
            throw new InvalidParametersException(e.getMessage());
        }
    }
}
