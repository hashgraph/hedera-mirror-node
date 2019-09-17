package com.hedera.mirror.domain;

import lombok.Data;

@Data
public class Entity {
    private Long autoRenewPeriod;
    private String ed25519PublicKeyHex;
    private Long entityNum;
    private Long entityRealm;
    private Long entityShard;
    private Integer entityTypeId;
    private Long expTimeNanos;
    private Long expTimeNs;
    private Long expTimeSeconds;
    private boolean deleted;
    private Long id;
    private byte[] key;
    private Long proxyAccountId;
}
