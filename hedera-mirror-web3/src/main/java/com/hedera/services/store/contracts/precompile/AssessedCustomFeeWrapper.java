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

package com.hedera.services.store.contracts.precompile;

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.Arrays;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class AssessedCustomFeeWrapper {

    private final TokenID token;
    private final AccountID account;
    private final long units;
    private final AccountID[] effPayerAccounts;

    public AssessedCustomFeeWrapper(
            final AccountID account, final TokenID token, final long units, final AccountID[] effPayerAccounts) {
        this.account = account;
        this.token = token;
        this.units = units;
        this.effPayerAccounts = effPayerAccounts;
    }

    public AssessedCustomFeeWrapper(final AccountID account, final long units, final AccountID[] effPayerAccounts) {
        this.token = null;
        this.account = account;
        this.units = units;
        this.effPayerAccounts = effPayerAccounts;
    }

    public boolean isForHbar() {
        return token == null;
    }

    public TokenID token() {
        return token;
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(AssessedCustomFeeWrapper.class)
                .add("token", token == null ? "ℏ" : token)
                .add("account", account)
                .add("units", units)
                .add("effective payer accounts", Arrays.toString(effPayerAccounts))
                .toString();
    }
}
