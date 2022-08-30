package com.hedera.services.transaction.contracts.sources;

import org.hyperledger.besu.datatypes.Address;

import com.hedera.mirror.web3.evm.SimulatedAliasManager;
import com.hedera.mirror.web3.repository.TokenRepository;

public interface EvmSigsVerifier {

    /**
     * Determines if the target token has an active supply key given the cryptographic signatures
     * from the {@link com.hederahashgraph.api.proto.java.SignatureMap} that could be verified
     * asynchronously; plus the given recipient and contract of the current {@link
     * org.hyperledger.besu.evm.frame.MessageFrame}.
     *
     * <p>If the supply key includes a {@code contractID} key matching the contract address, or a
     * {@code delegatableContractId} key matching the recipient address, then those keys must be
     * treated as active for the purposes of this test.
     *
     * <p>Does <b>not</b> perform any synchronous signature verification.
     *
     * @param isDelegateCall a flag showing if the message represented by the active frame is
     *     invoked via {@code delegatecall}
     * @param token the address of the token to test for supply key activation
     * @param activeContract the address of the contract that should be signed in the key
     * @param aliasManager the alias manager that would resolve aliased addresses
     * @param tokenRepository token repository
     * @return whether the target account's key has an active signature
     */
    boolean hasActiveSupplyKey(
            boolean isDelegateCall,
            Address token,
            Address activeContract,
            SimulatedAliasManager aliasManager,
            TokenRepository tokenRepository);
}
