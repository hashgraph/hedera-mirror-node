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

package com.hedera.services.fees.calculation.usage.consensus;

import static com.hedera.services.hapi.utils.fees.FeeBuilder.FEE_MATRICES_CONST;

import com.hedera.services.fees.usage.state.UsageAccumulator;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;

public final class AdapterUtils {
    public static FeeData feeDataFrom(final UsageAccumulator usage) {
        final var usages = FeeData.newBuilder();

        final var network = FeeComponents.newBuilder()
                .setConstant(FEE_MATRICES_CONST)
                .setBpt(usage.getUniversalBpt())
                .setVpt(usage.getNetworkVpt())
                .setRbh(usage.getNetworkRbh());
        final var node = FeeComponents.newBuilder()
                .setConstant(FEE_MATRICES_CONST)
                .setBpt(usage.getUniversalBpt())
                .setVpt(usage.getNodeVpt())
                .setBpr(usage.getNodeBpr())
                .setSbpr(usage.getNodeSbpr());
        final var service = FeeComponents.newBuilder()
                .setConstant(FEE_MATRICES_CONST)
                .setRbh(usage.getServiceRbh())
                .setSbh(usage.getServiceSbh());
        return usages.setNetworkdata(network)
                .setNodedata(node)
                .setServicedata(service)
                .build();
    }
}
