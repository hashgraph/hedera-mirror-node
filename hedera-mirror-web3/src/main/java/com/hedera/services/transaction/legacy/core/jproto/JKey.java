package com.hedera.services.transaction.legacy.core.jproto;

/** Maps to proto Key. */
public abstract class JKey {
    /**
     * Expected to return {@code false} if the key is empty
     *
     * @return whether the key is valid
     */
    public abstract boolean isValid();

    public abstract boolean isEmpty();

    public boolean hasContractID() {
        return false;
    }

    public boolean hasContractAlias() {
        return false;
    }

    public boolean hasDelegatableContractAlias() {
        return false;
    }

    public boolean hasDelegatableContractId() {
        return false;
    }

    public JContractIDKey getContractIDKey() {
        return null;
    }

    public JContractAliasKey getContractAliasKey() {
        return null;
    }

    public JDelegatableContractAliasKey getDelegatableContractAliasKey() {
        return null;
    }

    public JDelegatableContractIDKey getDelegatableContractIdKey() {
        return null;
    }
}
