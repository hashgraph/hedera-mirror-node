package com.hedera.mirror.web3.service.eth;

import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor
public class EthRpcCallBody {

    String block;
    String data;
    String from;
    String gas;
    String gasPrice;
    String to;
    String value;
}
