package com.hedera.mirror.api.contract.service.eth;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor
public class EthParams {

    private Optional<String> from;
    private String to;
    private Optional<Integer> gas;
    private Optional<Integer> gasPrice;
    private Optional<Integer> value;
    private Optional<String> data;
}
