package com.hedera.mirror.web3.evm.store.contract.precompile;

import com.hedera.services.store.contracts.precompile.Precompile;

import javax.inject.Named;

@Named
public class PrecompileFactory {

    private static final String UNSUPPORTED_ERROR_MESSAGE = "Precompile not supported for non-static frames";
    private static final int NON_EXISTING_ABI = 0x00000000;

    public Precompile lookup(int functionSelector) {
        //Temporary case for non-existing ABI, until we implement our first Precompile
        return switch (functionSelector) {
            case NON_EXISTING_ABI -> null;
            default -> throw new UnsupportedOperationException(UNSUPPORTED_ERROR_MESSAGE);
        };
    }
}
