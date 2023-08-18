/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.txns.validation.PureValidation.checkKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAUSE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.sigs.utils.ImmutableKeyUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Copied Logic type from hedera-services.
 * <p>
 * Differences with the original:
 * 1. Removed methods which are not needed currently - typeCheck, nonFungibleUniqueCheck, fungibleCommonTypeCheck, suppliesCheck,
 * supplyTypeCheck, nftSupplyKeyCheck
 */
public final class TokenListChecks {
    static Predicate<Key> adminKeyRemoval = ImmutableKeyUtils::signalsKeyRemoval;

    private TokenListChecks() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static boolean repeatsItself(final List<TokenID> tokens) {
        return new HashSet<>(tokens).size() < tokens.size();
    }

    @SuppressWarnings("java:S107")
    public static ResponseCodeEnum checkKeys(
            final boolean hasAdminKey,
            final Key adminKey,
            final boolean hasKycKey,
            final Key kycKey,
            final boolean hasWipeKey,
            final Key wipeKey,
            final boolean hasSupplyKey,
            final Key supplyKey,
            final boolean hasFreezeKey,
            final Key freezeKey,
            final boolean hasFeeScheduleKey,
            final Key feeScheduleKey,
            final boolean hasPauseKey,
            final Key pauseKey) {
        ResponseCodeEnum validity = checkAdminKey(hasAdminKey, adminKey);
        if (validity != OK) {
            return validity;
        }

        validity = checkKeyOfType(hasKycKey, kycKey, INVALID_KYC_KEY);
        if (validity != OK) {
            return validity;
        }

        validity = checkKeyOfType(hasWipeKey, wipeKey, INVALID_WIPE_KEY);
        if (validity != OK) {
            return validity;
        }

        validity = checkKeyOfType(hasSupplyKey, supplyKey, INVALID_SUPPLY_KEY);
        if (validity != OK) {
            return validity;
        }

        validity = checkKeyOfType(hasFreezeKey, freezeKey, INVALID_FREEZE_KEY);
        if (validity != OK) {
            return validity;
        }

        validity = checkKeyOfType(hasFeeScheduleKey, feeScheduleKey, INVALID_CUSTOM_FEE_SCHEDULE_KEY);
        if (validity != OK) {
            return validity;
        }

        validity = checkKeyOfType(hasPauseKey, pauseKey, INVALID_PAUSE_KEY);
        return validity;
    }

    private static ResponseCodeEnum checkAdminKey(final boolean hasAdminKey, final Key adminKey) {
        if (hasAdminKey && !adminKeyRemoval.test(adminKey)) {
            return checkKey(adminKey, INVALID_ADMIN_KEY);
        }
        return OK;
    }

    private static ResponseCodeEnum checkKeyOfType(final boolean hasKey, final Key key, final ResponseCodeEnum code) {
        if (hasKey) {
            return checkKey(key, code);
        }
        return OK;
    }
}
