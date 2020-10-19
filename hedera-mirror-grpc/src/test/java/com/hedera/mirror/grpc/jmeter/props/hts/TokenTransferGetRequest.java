package com.hedera.mirror.grpc.jmeter.props.hts;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TokenTransferGetRequest {
    private String restBaseUrl;
    private List<String> transactionIds;
    private int restRetryMax;
    private int restRetryBackoffMs;
    private int expectedMessages;
}
