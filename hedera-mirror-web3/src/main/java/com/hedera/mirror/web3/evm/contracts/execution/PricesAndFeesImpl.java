package com.hedera.mirror.web3.evm.contracts.execution;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.time.Instant;
import javax.inject.Named;
import lombok.NoArgsConstructor;

import com.hedera.services.evm.contracts.execution.PricesAndFeesProvider;

@Named
@NoArgsConstructor
public class PricesAndFeesImpl implements PricesAndFeesProvider {

    @Override
    public long currentGasPrice(Instant now, HederaFunctionality function) {
        return 120000000L;
    }
}

