package com.hedera.services.transaction.operation;

import com.hedera.services.transaction.operation.helpers.MerkleAccount;

import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;

public class SimulateExceptionalHaltReason {
    /**
     * Used when the EVM transaction accesses address that does not map to any existing
     * (non-deleted) account
     */
    public static final ExceptionalHaltReason INVALID_SOLIDITY_ADDRESS =
            SimulateExceptionalHalt.INVALID_SOLIDITY_ADDRESS;
    /**
     * Used when {@link SimulateSelfDestructOperation} is used and the beneficiary is specified to be
     * the same as the destructed account
     */
    public static final ExceptionalHaltReason SELF_DESTRUCT_TO_SELF =
            SimulateExceptionalHalt.SELF_DESTRUCT_TO_SELF;
    /**
     * Used when there is no active signature for a given {@link
     * com.hedera.services.transaction.operation.helpers.MerkleAccount} that has {@link
     * MerkleAccount#isReceiverSigRequired()} enabled and the account receives HBars
     */
    public static final ExceptionalHaltReason INVALID_SIGNATURE =
            SimulateExceptionalHalt.INVALID_SIGNATURE;
    /** Used when the target of a {@code selfdestruct} is a token treasury. */
    public static final ExceptionalHaltReason CONTRACT_IS_TREASURY =
            SimulateExceptionalHalt.CONTRACT_IS_TREASURY;
    /** Used when the target of a {@code selfdestruct} has positive fungible unit balances. */
    public static final ExceptionalHaltReason CONTRACT_STILL_OWNS_NFTS =
            SimulateExceptionalHalt.CONTRACT_STILL_OWNS_NFTS;
    /** Used when the target of a {@code selfdestruct} has positive balances. */
    public static final ExceptionalHaltReason TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES =
            SimulateExceptionalHalt.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;

    enum SimulateExceptionalHalt implements ExceptionalHaltReason {
        INVALID_SOLIDITY_ADDRESS("Invalid account reference"),
        SELF_DESTRUCT_TO_SELF("Self destruct to the same address"),
        CONTRACT_IS_TREASURY("Token treasuries cannot be deleted"),
        INVALID_SIGNATURE("Invalid signature"),
        TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES(
                "Accounts with positive fungible token balances cannot be deleted"),
        CONTRACT_STILL_OWNS_NFTS("Accounts who own nfts cannot be deleted");

        final String description;

        SimulateExceptionalHalt(final String description) {
            this.description = description;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }
}
