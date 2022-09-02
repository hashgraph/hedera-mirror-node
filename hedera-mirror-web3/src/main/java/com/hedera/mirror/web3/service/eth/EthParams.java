package com.hedera.mirror.web3.service.eth;

import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor
public class EthParams {

    String from;
    String to;
    String gas;
    String gasPrice;
    String value;
    String data;
}
