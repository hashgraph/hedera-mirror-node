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

package com.hedera.services.fees.calculation;

import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.repository.PricesAndFeesRepository;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MirrorBasicUsagePrices implements UsagePricesProvider {
    private static final EntityId FEE_SCHEDULE_ENTITY_ID = new EntityId(0L, 0L, 111L, EntityType.FILE);
    private static final Logger log = LogManager.getLogger(MirrorBasicUsagePrices.class);

    private static final long DEFAULT_FEE = 100_000L;

    private static final FeeComponents DEFAULT_PROVIDER_RESOURCE_PRICES = FeeComponents.newBuilder()
            .setMin(DEFAULT_FEE)
            .setMax(DEFAULT_FEE)
            .setConstant(0)
            .setBpt(0)
            .setVpt(0)
            .setRbh(0)
            .setSbh(0)
            .setGas(0)
            .setTv(0)
            .setBpr(0)
            .setSbpr(0)
            .build();
    public static final Map<SubType, FeeData> DEFAULT_RESOURCE_PRICES = Map.of(
            DEFAULT,
            FeeData.newBuilder()
                    .setNetworkdata(DEFAULT_PROVIDER_RESOURCE_PRICES)
                    .setNodedata(DEFAULT_PROVIDER_RESOURCE_PRICES)
                    .setServicedata(DEFAULT_PROVIDER_RESOURCE_PRICES)
                    .build());

    private Timestamp currFunctionUsagePricesExpiry;
    private Timestamp nextFunctionUsagePricesExpiry;

    private EnumMap<HederaFunctionality, Map<SubType, FeeData>> currFunctionUsagePrices;
    private EnumMap<HederaFunctionality, Map<SubType, FeeData>> nextFunctionUsagePrices;

    private final PricesAndFeesRepository pricesAndFeesRepository;

    @Inject
    public MirrorBasicUsagePrices(PricesAndFeesRepository pricesAndFeesRepository) {
        this.pricesAndFeesRepository = pricesAndFeesRepository;
    }

    @Override
    public FeeData defaultPricesGiven(final HederaFunctionality function, final Timestamp at) {
        this.updateFeeSchedules(at.getSeconds());
        return pricesGiven(function, at).get(DEFAULT);
    }

    public void updateFeeSchedules(final long now) {
        byte[] feeScheduleFile = new byte[0];
        if (now > 0) {
            feeScheduleFile = pricesAndFeesRepository.getFeeSchedule(now);
        }

        try {
            final var schedules = CurrentAndNextFeeSchedule.parseFrom(feeScheduleFile);
            this.updateFeeSchedules(schedules);
        } catch (InvalidProtocolBufferException e) {
            log.warn(
                    "Corrupt fee schedules file at {}, may require remediation!", FEE_SCHEDULE_ENTITY_ID.toString(), e);
            throw new IllegalStateException(String.format("Fee schedule %s is corrupt!", FEE_SCHEDULE_ENTITY_ID));
        }
    }

    private void updateFeeSchedules(CurrentAndNextFeeSchedule schedules) {
        final var currentSchedule = schedules.getCurrentFeeSchedule();
        this.currFunctionUsagePrices = getUsagePricesEnumMap(currentSchedule);
        this.currFunctionUsagePricesExpiry = getUsagePricesExpiry(currentSchedule);

        final var nextSchedule = schedules.getNextFeeSchedule();
        this.nextFunctionUsagePrices = getUsagePricesEnumMap(nextSchedule);
        this.nextFunctionUsagePricesExpiry = getUsagePricesExpiry(nextSchedule);
    }

    private EnumMap<HederaFunctionality, Map<SubType, FeeData>> getUsagePricesEnumMap(FeeSchedule feeSchedule) {
        return feeSchedule.getTransactionFeeScheduleList().stream()
                .collect(Collectors.toMap(
                        TransactionFeeSchedule::getHederaFunctionality,
                        fs -> getFeesMap(fs.getFeesList()),
                        (l, r) -> {
                            throw new IllegalArgumentException("Duplicate keys " + l + "and " + r + ".");
                        },
                        () -> new EnumMap<>(HederaFunctionality.class)));
    }

    private Map<SubType, FeeData> getFeesMap(List<FeeData> feeData) {
        return feeData.stream().collect(Collectors.toMap(FeeData::getSubType, Function.identity()));
    }

    private Timestamp getUsagePricesExpiry(FeeSchedule feeSchedule) {
        return Timestamp.newBuilder()
                .setSeconds(feeSchedule.getExpiryTime().getSeconds())
                .build();
    }

    private Map<SubType, FeeData> pricesGiven(final HederaFunctionality function, final Timestamp at) {
        try {
            final Map<HederaFunctionality, Map<SubType, FeeData>> functionUsagePrices = applicableUsagePrices(at);
            final Map<SubType, FeeData> usagePrices = functionUsagePrices.get(function);
            Objects.requireNonNull(usagePrices);
            return usagePrices;
        } catch (final Exception e) {
            log.debug(
                    "Default usage price will be used, no specific usage prices available for" + " function {} @ {}!",
                    function,
                    Instant.ofEpochSecond(at.getSeconds(), at.getNanos()));
        }
        return DEFAULT_RESOURCE_PRICES;
    }

    private Map<HederaFunctionality, Map<SubType, FeeData>> applicableUsagePrices(final Timestamp at) {
        if (onlyNextScheduleApplies(at)) {
            return nextFunctionUsagePrices;
        } else {
            return currFunctionUsagePrices;
        }
    }

    private boolean onlyNextScheduleApplies(final Timestamp at) {
        return at.getSeconds() >= currFunctionUsagePricesExpiry.getSeconds()
                && at.getSeconds() < nextFunctionUsagePricesExpiry.getSeconds();
    }
}
