/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.fees.calculation;

import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.Map;

public interface UsagePricesProvider {
    /**
     * Returns the prices in tinyCents that are likely to be required to consume various resources while processing the
     * given operation at the given time. (In principle, the price schedules could change in the interim.)
     *
     * @param function the operation of interest
     * @param at       the expected consensus time for the operation
     * @return the estimated prices
     */
    Map<SubType, FeeData> pricesGiven(HederaFunctionality function, Timestamp at);

    /**
     * Returns the prices in tinyCents that are likely to be required to consume various resources while processing the
     * given operation at the given time. (In principle, the price schedules could change in the interim.)
     *
     * @param function the operation of interest
     * @param at       the expected consensus time for the operation
     * @return the estimated prices
     */
    FeeData defaultPricesGiven(HederaFunctionality function, Timestamp at);
}
