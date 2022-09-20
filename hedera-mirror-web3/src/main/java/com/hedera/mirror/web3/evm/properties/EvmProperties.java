package com.hedera.mirror.web3.evm.properties;

import com.hedera.services.stream.proto.SidecarType;

import java.util.Set;

public interface EvmProperties {

    default int getChainId() {return 0;}

    default int getMaxGasRefundPercentage() {return 0;}

    default boolean areNftsEnabled(){return false;}

    default Set<SidecarType> enabledSidecars(){
        return Set.of();
    }

}
