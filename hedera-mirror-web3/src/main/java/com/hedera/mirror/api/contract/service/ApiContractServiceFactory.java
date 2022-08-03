package com.hedera.mirror.api.contract.service;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ApiContractServiceFactory {

    private final Map<String, ApiContractService<?, ?>> services;

    public ApiContractServiceFactory(Collection<ApiContractService<?, ?>> services) {
        this.services = services.stream().collect(Collectors.toMap(ApiContractService::getMethod, Function.identity()));
    }

    public boolean isValid(String method) {
        return services.containsKey(method);
    }

    public <I, O> ApiContractService<I, O> lookup(String method) {
        return (ApiContractService<I, O>) services.get(method);
    }
}
