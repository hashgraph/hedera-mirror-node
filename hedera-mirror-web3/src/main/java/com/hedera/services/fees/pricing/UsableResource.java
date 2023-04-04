package com.hedera.services.fees.pricing;

/** Represents the eight kinds of resources that may be consumed in the Hedera network. */
public enum UsableResource {
    /** Various types of fixed overhead. */
    CONSTANT,
    /** Consensus network bandwidth in bytes. */
    BPT,
    /** Signature verification. */
    VPT,
    /** Memory storage in byte-hours. */
    RBH,
    /** Disk storage in byte-hours. */
    SBH,
    /** EVM computation. */
    GAS,
    /** Node-specific bandwidth used to serve data from memory, in bytes. */
    BPR,
    /** Node-specific bandwidth used to serve data from disk, in bytes. */
    SBPR,
}
