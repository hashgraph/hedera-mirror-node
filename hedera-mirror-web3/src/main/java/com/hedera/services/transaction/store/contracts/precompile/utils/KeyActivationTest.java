package com.hedera.services.transaction.store.contracts.precompile.utils;

import org.hyperledger.besu.datatypes.Address;

import com.hedera.mirror.web3.evm.SimulatedAliasManager;
import com.hedera.mirror.web3.repository.TokenRepository;

@FunctionalInterface
public interface KeyActivationTest {
    /**
     * Returns whether a key implicit in the target address is active, given an idealized message
     * frame in which:
     *
     * <ul>
     *   <li>The {@code recipient} address is the account receiving the call operation; and,
     *   <li>The {@code contract} address is the account with the code being executed; and,
     *   <li>Any {@code ContractID} or {@code delegatable_contract_id} key that matches the {@code
     *       activeContract} address should be considered active (modulo whether the recipient and
     *       contract imply a delegate call).
     * </ul>
     *
     * <p>Note the target address might not imply an account key, but e.g. a token supply key.
     *
     * @param isDelegateCall a flag showing if the message represented by the active frame is
     *     invoked via {@code delegatecall}
     * @param target an address with an implicit key understood by this implementation
     * @param activeContract the contract address that can activate a contract or delegatable
     *     contract key
     * @param aliasManager the alias manager that would resolve aliased addresses
     * @param tokenRepository token repository
     * @return whether the implicit key has an active signature in this context
     */
    boolean apply(
            boolean isDelegateCall,
            Address target,
            Address activeContract,
            SimulatedAliasManager aliasManager,
            TokenRepository tokenRepository);
}
