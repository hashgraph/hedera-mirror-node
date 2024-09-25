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

package com.hedera.mirror.web3.state;

import static com.hedera.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.AccountID.AccountOneOfType;
import com.hedera.hapi.node.base.Key;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import lombok.CustomLog;
import lombok.experimental.UtilityClass;

@CustomLog
@UtilityClass
public class Utils {

    public static final long DEFAULT_AUTO_RENEW_PERIOD = 7776000L;

    public static Key parseKey(final byte[] keyBytes, final Long id) {
        try {
            if (keyBytes != null && keyBytes.length > 0) {
                return Key.PROTOBUF.parse(Bytes.wrap(keyBytes));
            }
        } catch (final ParseException e) {
            log.warn("Failed to parse key for account " + id);
        }

        return null;
    }

    public static AccountID convertCanonicalAccountIdFromEntity(final Entity entity) {
        if (entity == null) {
            return AccountID.DEFAULT;
        }

        if (entity.getEvmAddress() != null) {
            return new AccountID(
                    entity.getShard(),
                    entity.getRealm(),
                    new OneOf<>(
                            AccountOneOfType.ALIAS,
                            com.hedera.pbj.runtime.io.buffer.Bytes.wrap(entity.getEvmAddress())));
        }

        if (entity.getAlias() != null && entity.getAlias().length == EVM_ADDRESS_LENGTH) {
            return new AccountID(
                    entity.getShard(),
                    entity.getRealm(),
                    new OneOf<>(
                            AccountOneOfType.ALIAS, com.hedera.pbj.runtime.io.buffer.Bytes.wrap(entity.getAlias())));
        }

        return new AccountID(
                entity.getShard(), entity.getRealm(), new OneOf<>(AccountOneOfType.ACCOUNT_NUM, entity.getNum()));
    }
}
