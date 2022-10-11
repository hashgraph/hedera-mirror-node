package com.hedera.mirror.web3.service;

public interface ApiContractService<I, O> {

    String getMethod();

    O get(I request);
}
