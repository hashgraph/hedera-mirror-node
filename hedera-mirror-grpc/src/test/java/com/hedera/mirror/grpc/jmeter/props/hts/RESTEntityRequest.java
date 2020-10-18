package com.hedera.mirror.grpc.jmeter.props.hts;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RESTEntityRequest {
    private List<String> ids;
    private int retryLimit;
    private int retryInterval;
    private int expectedMessages;
}
