package com.hedera.mirror.api.contract.service.eth;

import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor
public class TxnCallBody {

    EthParams ethParams;
    String tag;
}
