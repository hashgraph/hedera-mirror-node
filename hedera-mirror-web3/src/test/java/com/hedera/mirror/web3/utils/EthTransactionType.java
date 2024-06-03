package com.hedera.mirror.web3.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
enum EthTransactionType {
    LEGACY(0),
    EIP_2930(1),
    EIP_1559(2);

    private final int typeByte;
}
