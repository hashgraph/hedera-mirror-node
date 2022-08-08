package com.hedera.mirror.web3.service.eth;

import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor
public class TxnCallBody {

    EthParams ethParams;
    String tag;
}
