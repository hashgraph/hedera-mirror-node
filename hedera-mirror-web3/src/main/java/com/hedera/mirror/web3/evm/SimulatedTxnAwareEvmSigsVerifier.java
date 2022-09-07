package com.hedera.mirror.web3.evm;


import static com.hedera.services.transaction.exception.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;

import com.swirlds.common.crypto.TransactionSignature;
import java.util.function.BiPredicate;
import javax.inject.Named;
import lombok.AllArgsConstructor;
import org.hyperledger.besu.datatypes.Address;
import org.jetbrains.annotations.NotNull;

import com.hedera.mirror.web3.repository.TokenRepository;
import com.hedera.services.transaction.contracts.sources.EvmSigsVerifier;
import com.hedera.services.transaction.legacy.core.jproto.JKey;

@Named
@AllArgsConstructor
public class SimulatedTxnAwareEvmSigsVerifier implements EvmSigsVerifier {
    //    private final ActivationTest activationTest;
//    private final BiPredicate<JKey, TransactionSignature> cryptoValidity;
    private final TokenRepository tokenRepository;

    //FUTURE WORK - supply key verification to be implemented
    @Override
    public boolean hasActiveSupplyKey(
            final boolean isDelegateCall,
            @NotNull final Address tokenAddress,
            @NotNull final Address activeContract,
            @NotNull final SimulatedAliasManager aliasManager,
            @NotNull final TokenRepository tokenRepository) {
//        final var tokenId = EntityIdUtils.tokenIdFromEvmAddress(tokenAddress);
        final var token = tokenRepository.findByAddress(tokenAddress.toArray()).orElse(null);
        validateTrue(token!=null, INVALID_TOKEN_ID);

        final var supplyKey = token.getSupplyKey();
//        final var supplyKey = (JKey) worldLedgers.tokens().get(tokenId, TokenProperty.SUPPLY_KEY);
        validateTrue(supplyKey != null, TOKEN_HAS_NO_SUPPLY_KEY);

        return true;
//        return isActiveInFrame(supplyKey, isDelegateCall, activeContract, aliasManager);
    }

    //FUTURE WORK - to be implemented
    private boolean isActiveInFrame(
            final JKey key,
            final boolean isDelegateCall,
            final Address activeContract,
            final SimulatedAliasManager aliases) {
//        final var pkToCryptoSigsFn = txnCtx.swirldsTxnAccessor().getRationalizedPkToCryptoSigFn();
//        return activationTest.test(
//                key, pkToCryptoSigsFn, validityTestFor(isDelegateCall, activeContract, aliases));
        return true;
    }

    //FUTURE WORK - sigs verifier logic to be implemented
    BiPredicate<JKey, TransactionSignature> validityTestFor(
            final boolean isDelegateCall,
            final Address activeContract,
            final SimulatedAliasManager aliases) {
        // Note that when this observer is used directly above in isActiveInFrame(), it will be
        // called  with each primitive key in the top-level Hedera key of interest, along with
        // that key's verified cryptographic signature (if any was available in the sigMap)
        return (key, sig) -> {
            if (key.hasDelegatableContractId() || key.hasDelegatableContractAlias()) {
                final var controllingId =
                        key.hasDelegatableContractId()
                                ? key.getDelegatableContractIdKey().getContractID()
                                : key.getDelegatableContractAliasKey().getContractID();
                return true;
//                final var controllingContract = aliases.currentAddress(controllingId);
//                return controllingContract.equals(activeContract);
            } else if (key.hasContractID() || key.hasContractAlias()) {
                final var controllingId =
                        key.hasContractID()
                                ? key.getContractIDKey().getContractID()
                                : key.getContractAliasKey().getContractID();
//                final var controllingContract = aliases.currentAddress(controllingId);
//                return !isDelegateCall && controllingContract.equals(activeContract);
                return true;
            } else {
                // Otherwise, apply the standard cryptographic validity test
//                return cryptoValidity.test(key, sig);
                return true;
            }
        };
    }
}
