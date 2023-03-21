package com.hedera.mirror.web3.evm.store.contract.precompile;

import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewGasCalculator;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class MirrorHTSPrecompiledContract extends EvmHTSPrecompiledContract {

    public MirrorHTSPrecompiledContract(
            EvmInfrastructureFactory infrastructureFactory) {
        super(infrastructureFactory);
    }

    @Override
    public Pair<Long, Bytes> computeCosted(
            final Bytes input,
            final MessageFrame frame,
            final ViewGasCalculator viewGasCalculator,
            final TokenAccessor tokenAccessor) {

        throw new UnsupportedOperationException("Precompile not supported");
    }

    @Override
    public String getName() {
        return "MirrorHTS";
    }
}
