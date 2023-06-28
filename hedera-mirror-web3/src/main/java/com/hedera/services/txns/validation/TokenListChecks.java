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

import com.hederahashgraph.api.proto.java.TokenID;
import java.util.HashSet;
import java.util.List;

/**
 * Copied Logic type from hedera-services.
 *
 * Differences with the original:
 *  1. Removed methods which are not needed currently - typeCheck, nonFungibleUniqueCheck, fungibleCommonTypeCheck, suppliesCheck,
 *  supplyTypeCheck, checkKeys, checkAdminKey, checkKeyOfType, nftSupplyKeyCheck
 * */
public final class TokenListChecks {

    private TokenListChecks() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static boolean repeatsItself(final List<TokenID> tokens) {
        return new HashSet<>(tokens).size() < tokens.size();
    }
}
