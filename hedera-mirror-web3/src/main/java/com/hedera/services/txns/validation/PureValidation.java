/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.txns.validation;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.jproto.JKey;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.commons.codec.DecoderException;

/**
 * Copied type from hedera-services.
 * <p>
 * Differences with the original:
 * 1. Removed methods which are not needed currently -queryableFileStatus, queryableAccountOrContractStatus,
 * queryableAccountStatus, internalQueryableAccountStatus, queryableContractStatus, queryableContractStatus,
 * chronologyStatus, asCoercedInstant, isValidStakedId
 */
public final class PureValidation {
    private PureValidation() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static ResponseCodeEnum checkKey(final Key key, final ResponseCodeEnum failure) {
        try {
            final var fcKey = JKey.mapKey(key);
            if (!fcKey.isValid()) {
                return failure;
            }
            return OK;
        } catch (DecoderException e) {
            return failure;
        }
    }
}
