package com.hedera.mirror.web3.evm;

import java.util.Set;

//Interface for storing entities for speculative writes
public interface SimulatedBackingStore<K, A>{
    /**
     * Alerts this {@code BackingStore} it should reconstruct any auxiliary data structures based on
     * its underlying sources. Used in particular for reconnect.
     */
    default void rebuildFromSources() {
        /* No-op. */
    }

    /**
     * Gets a possibly mutable reference to the account with the specified id.
     *
     * @param id the id of the relevant account
     * @return a reference to the account, or null if it is missing
     */
    A getRef(K id);

    /**
     * Gets a reference to the account with the specified id which should not be mutated.
     *
     * @param id the id of the relevant account
     * @return a reference to the account
     */
    A getImmutableRef(K id);

    /**
     * Updates (or creates, if absent) the account with the given id to the accompanying account.
     *
     * @param id the id of the relevant account
     * @param entity the account that should have this id
     */
    void put(K id, A entity);

    /**
     * Frees the account with the given id for reclamation.
     *
     * @param id the id of the relevant account
     */
    void remove(K id);

    /**
     * Checks if the collection contains the account with the given id.
     *
     * @param id the account in question
     * @return a flag for existence
     */
    boolean contains(K id);

    /**
     * Returns the set of extant account ids. <b>This is a very computation-intensive operation.
     * <i>idSet</i> should only be called at state initialisation Avoid using this method in a
     * normal handleTransaction flow.</b>
     *
     * @return the set of extant account ids
     */
    Set<K> idSet();

    /**
     * Returns the count of extant entities stored in the correspondent collection
     *
     * @return the count of extant entities stored in the correspondent collection
     */
    long size();
}

