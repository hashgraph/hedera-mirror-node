package com.hedera.mirror.web3.evm;

import javax.inject.Named;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.web3.evm.properties.EvmProperties;
import com.hedera.services.transaction.txns.validation.OptionValidator;

@Named
@RequiredArgsConstructor
public class InfrastructureFactory {

    private final EvmProperties evmProperties;

    public SimulatedMintLogic newMintLogic(
            final SimulatedBackingTokens simulatedBackingTokens, final
            OptionValidator validator) {
        return new SimulatedMintLogic(simulatedBackingTokens, evmProperties, validator);
    }
}
