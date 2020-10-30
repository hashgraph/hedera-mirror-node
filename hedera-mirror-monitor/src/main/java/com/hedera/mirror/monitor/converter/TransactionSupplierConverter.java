package com.hedera.mirror.monitor.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.convert.converter.Converter;

import com.hedera.mirror.monitor.scenario.ScenarioProperties;
import com.hedera.mirror.monitor.supplier.TransactionSupplier;

public class TransactionSupplierConverter implements Converter<ScenarioProperties, TransactionSupplier> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public TransactionSupplier convert(ScenarioProperties scenarioProperties) {
        return objectMapper
                .convertValue(scenarioProperties.getProperties(), scenarioProperties.getType().getSupplier());
    }
}
