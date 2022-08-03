package com.hedera.mirror.api.contract.service;

public interface ApiContractService<I, O> {

    String getMethod();

    O get(I request);
}
