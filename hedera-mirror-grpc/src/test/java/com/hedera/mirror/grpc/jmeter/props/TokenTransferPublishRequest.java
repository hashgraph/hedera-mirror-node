package com.hedera.mirror.grpc.jmeter.props;

import lombok.Builder;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.token.TokenId;

@Data
@Builder
@Log4j2
public class TokenTransferPublishRequest {
    private final int messagesPerBatchCount;
    private final TokenId tokenId;
    private final AccountId operatorId;
    private final AccountId recipientId;
    private final long tokenAmount;
}
