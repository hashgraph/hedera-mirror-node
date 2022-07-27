package com.hedera.services.transaction.operation.context;

public interface TransactionContext {
        /**
         * Records the beneficiary of an account (or contract) deleted in the current transaction.
         *
         * @param accountNum the number of a deleted account
         * @param beneficiaryNum the number of its beneficiary
         */
        void recordBeneficiaryOfDeleted(long accountNum, long beneficiaryNum);
}
