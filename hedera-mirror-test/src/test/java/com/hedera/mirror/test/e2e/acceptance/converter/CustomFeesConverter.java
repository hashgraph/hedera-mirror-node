/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.test.e2e.acceptance.converter;

import com.hedera.hashgraph.sdk.CustomFee;
import com.hedera.hashgraph.sdk.CustomFixedFee;
import com.hedera.hashgraph.sdk.CustomFractionalFee;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.mirror.rest.model.TransactionDetailAllOfAssessedCustomFees;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum;
import com.hedera.mirror.test.e2e.acceptance.steps.TokenFeature;
import io.cucumber.java.DataTableType;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@RequiredArgsConstructor
public class CustomFeesConverter {

    private final AccountClient accountClient;
    private final TokenFeature tokenFeature;

    @DataTableType
    public TransactionDetailAllOfAssessedCustomFees mirrorAssessedCustomFee(Map<String, String> entry) {
        var collector = accountClient.getAccount(AccountNameEnum.valueOf(entry.get("collector")));

        var assessedCustomFee = new TransactionDetailAllOfAssessedCustomFees();
        assessedCustomFee.setAmount(Long.parseLong(entry.get("amount")));
        assessedCustomFee.setCollectorAccountId(collector.getAccountId().toString());
        assessedCustomFee.setTokenId(getToken(entry.get("token")));
        return assessedCustomFee;
    }

    @DataTableType
    public CustomFee customFee(Map<String, String> entry) {
        String amount = entry.get("amount");
        var collector = accountClient.getAccount(AccountNameEnum.valueOf(entry.get("collector")));

        if (StringUtils.isNotEmpty(amount)) {
            var fixedFee = new CustomFixedFee();
            fixedFee.setAmount(Long.parseLong(amount));
            fixedFee.setFeeCollectorAccountId(collector.getAccountId());
            fixedFee.setDenominatingTokenId(getTokenId(entry.get("token")));
            return fixedFee;
        } else {
            var fractionalFee = new CustomFractionalFee();
            fractionalFee.setNumerator(Long.parseLong(entry.get("numerator")));
            fractionalFee.setDenominator(Long.parseLong(entry.get("denominator")));
            fractionalFee.setFeeCollectorAccountId(collector.getAccountId());
            fractionalFee.setMax(getValueOrDefault(entry.get("maximum")));
            fractionalFee.setMin(getValueOrDefault(entry.get("minimum")));
            return fractionalFee;
        }
    }

    private String getToken(String tokenIndex) {
        return Optional.ofNullable(getTokenId(tokenIndex))
                .map(TokenId::toString)
                .orElse(null);
    }

    private TokenId getTokenId(String tokenIndex) {
        return StringUtils.isNotEmpty(tokenIndex) ? tokenFeature.getTokenId() : null;
    }

    private long getValueOrDefault(String value) {
        return StringUtils.isNotEmpty(value) ? Long.parseLong(value) : 0;
    }
}
