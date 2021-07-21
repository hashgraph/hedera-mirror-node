package com.hedera.mirror.test.e2e.acceptance.converter;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import io.cucumber.java.DataTableType;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.CustomFee;
import com.hedera.hashgraph.sdk.CustomFixedFee;
import com.hedera.hashgraph.sdk.CustomFractionalFee;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorAssessedCustomFee;
import com.hedera.mirror.test.e2e.acceptance.steps.TokenFeature;

@RequiredArgsConstructor
public class CustomFeesConverter {

    private final TokenFeature tokenFeature;

    @DataTableType
    public MirrorAssessedCustomFee mirrorAssessedCustomFee(Map<String, String> entry) {
        MirrorAssessedCustomFee assessedCustomFee = new MirrorAssessedCustomFee();

        assessedCustomFee.setAmount(Long.parseLong(entry.get("amount")));
        assessedCustomFee.setCollectorAccountId(tokenFeature
                .getRecipientAccountId(Integer.parseInt(entry.get("collector"))).toString());
        assessedCustomFee.setTokenId(getToken(entry.get("token")));

        return assessedCustomFee;
    }

    @DataTableType
    public CustomFee customFee(Map<String, String> entry) {
        String amount = entry.get("amount");
        AccountId collector = tokenFeature.getRecipientAccountId(Integer.parseInt(entry.get("collector")));

        if (Strings.isNotEmpty(amount)) {
            // fixed fee
            CustomFixedFee fixedFee = new CustomFixedFee();

            fixedFee.setAmount(Long.parseLong(amount));
            fixedFee.setFeeCollectorAccountId(collector);
            fixedFee.setDenominatingTokenId(getTokenId(entry.get("token")));

            return fixedFee;
        } else {
            CustomFractionalFee fractionalFee = new CustomFractionalFee();

            fractionalFee.setNumerator(Long.parseLong(entry.get("numerator")));
            fractionalFee.setDenominator(Long.parseLong(entry.get("denominator")));
            fractionalFee.setFeeCollectorAccountId(collector);
            fractionalFee.setMax(getValueOrDefault(entry.get("maximum")));
            fractionalFee.setMin(getValueOrDefault(entry.get("minimum")));

            return fractionalFee;
        }
    }

    private String getToken(String tokenIndex) {
        return Optional.ofNullable(getTokenId(tokenIndex)).map(TokenId::toString).orElse(null);
    }

    private TokenId getTokenId(String tokenIndex) {
        return Strings.isNotEmpty(tokenIndex) ? tokenFeature.getTokenId(Integer.parseInt(tokenIndex)) : null;
    }

    private long getValueOrDefault(String value) {
        return Strings.isNotEmpty(value) ? Long.parseLong(value) : 0;
    }
}
