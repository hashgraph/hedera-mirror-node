package com.hedera.mirror.web3.evm;

import javax.inject.Singleton;
import org.hyperledger.besu.datatypes.Address;

@Singleton
public class AliasesResolver {

    //FUTURE WORK implementation to be provided in separate PR
    Address resolveForEvm(final Address addressOrAlias) {
        return null;
    }
}
