package com.hedera.mirror.web3.evm.store.contract.precompile;

import com.hedera.services.store.contracts.precompile.Precompile;

import javax.inject.Named;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Named
public class PrecompileFactory {

    private final Map<Integer, Precompile> abiConstantToPrecompile = new HashMap<>();

    public PrecompileFactory(final Set<Precompile> precompiles) {
        for(Precompile precompile : precompiles) {
            for(Integer selector: precompile.getFunctionSelectors()) {
                abiConstantToPrecompile.put(selector, precompile);
            }
        }
    }

    public Precompile lookup(int functionSelector) {
        return abiConstantToPrecompile.getOrDefault(functionSelector, null);
    }
}
