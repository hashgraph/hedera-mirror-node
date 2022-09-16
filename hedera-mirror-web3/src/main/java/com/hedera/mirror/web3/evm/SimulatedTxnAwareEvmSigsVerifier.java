package com.hedera.mirror.web3.evm;

import static com.hedera.services.transaction.exception.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import javax.inject.Named;
import lombok.AllArgsConstructor;
import org.hyperledger.besu.datatypes.Address;
import org.jetbrains.annotations.NotNull;
import com.hedera.mirror.web3.repository.TokenRepository;
import com.hedera.services.transaction.contracts.sources.EvmSigsVerifier;

@Named
@AllArgsConstructor
public class SimulatedTxnAwareEvmSigsVerifier implements EvmSigsVerifier {

    //FUTURE WORK - supply key verification to be implemented
    @Override
    public boolean hasActiveSupplyKey(
            final boolean isDelegateCall,
            @NotNull final Address tokenAddress,
            @NotNull final Address activeContract,
            @NotNull final SimulatedAliasManager aliasManager,
            @NotNull final TokenRepository tokenRepository) {
        final var token = tokenRepository.findByAddress(tokenAddress.toArray()).orElse(null);
        validateTrue(token!=null, INVALID_TOKEN_ID);

        final var supplyKey = token.getSupplyKey();
        validateTrue(supplyKey != null, TOKEN_HAS_NO_SUPPLY_KEY);

        return true;
    }
}
