package com.hedera.mirror.web3.transaction.properties;

import com.hederahashgraph.api.proto.java.AccountID;

public class ConfigurationProperties {

    public int chainId() {
        return 0;
    }

    public AccountID fundingAccount() {
        return AccountID.newBuilder().build();
    }

    public boolean shouldEnableTraceability() {
        return true;
    }

    public int maxGasRefundPercentage() {
        return 0;
    }
}
