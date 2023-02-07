package com.hedera.mirror.web3.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.web3.controller.ContractController;

@RequiredArgsConstructor
//@Named
public class GasPerSecondMetrics implements MeterBinder {

    private final ContractController contractController;

    @Override
    public void bindTo(MeterRegistry registry) {
//        AtomicInteger atomicInteger = new AtomicInteger(3);
//        registry.gauge("gas.per.second", atomicInteger.get());
//        registry.gauge("gas.per.second", contractController::getAccumulatedGasUsed, g -> g.hashCode());

        Gauge.builder("gas.per.second", contractController, ContractController::getAccumulatedGasUsed) //
                .register(registry);
    }
}
