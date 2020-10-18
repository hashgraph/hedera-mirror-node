package com.hedera.mirror.grpc.jmeter.props.hts;

import lombok.Builder;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.token.TokenId;

@Data
@Builder
@Log4j2
//TODO Tried to extend, caused issues with the builder, look into
public class TokenTransferPublishAndRetrieveRequest {
    private final int messagesPerBatchCount;
    private final TokenId tokenId;
    private final AccountId operatorId;
    private final AccountId recipientId;
    private final long tokenAmount;

    private String restBaseUrl;
    private int restRetryMax;
    private int restRetryBackoffMs;
    private final int statusPrintIntervalMinutes;
}
