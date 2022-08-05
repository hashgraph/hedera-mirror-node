package com.hedera.mirror.web3.service.eth;

import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor
public class EthParams {

    private String from;
    private String to;
    private String gas;
    private String gasPrice;
    private String value;
    private String data;
}
