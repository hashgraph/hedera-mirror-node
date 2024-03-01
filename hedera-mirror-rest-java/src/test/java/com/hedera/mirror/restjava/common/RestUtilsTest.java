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

package com.hedera.mirror.restjava.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RestUtilsTest {

    @Test
    void getFilter() {
        var accountIdParam = "gte:1000";
        var actual = RestUtils.getFilter(FilterKey.ACCOUNT_ID, accountIdParam);
        Filter expected = new Filter(FilterKey.ACCOUNT_ID, RangeOperator.GTE, "1000");
        assertThat(expected)
                .isNotNull()
                .returns(actual.getKey(), Filter::getKey)
                .returns(actual.getOperator(), Filter::getOperator)
                .returns(actual.getValue(), Filter::getValue);
    }

    @Test
    void getFilterWithNoOperator() {
        var accountIdParam = "1000";
        var actual = RestUtils.getFilter(FilterKey.ACCOUNT_ID, accountIdParam);
        Filter expected = new Filter(FilterKey.ACCOUNT_ID, RangeOperator.EQ, "1000");
        assertThat(expected)
                .isNotNull()
                .returns(actual.getKey(), Filter::getKey)
                .returns(actual.getOperator(), Filter::getOperator)
                .returns(actual.getValue(), Filter::getValue);
    }

    @Test
    void getFilterwithNull() {
        var actual = RestUtils.getFilter(FilterKey.ACCOUNT_ID, null);
        assertThat(actual).isNull();
    }

    @Test
    void getAccountNum() {
        Filter filter = new Filter(FilterKey.ACCOUNT_ID, RangeOperator.EQ, "1000");
        assertThat(RestUtils.getAccountNum(filter)).isEqualTo(1000);
    }
}
