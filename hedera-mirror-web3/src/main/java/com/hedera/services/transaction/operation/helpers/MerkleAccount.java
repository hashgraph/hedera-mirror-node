package com.hedera.services.transaction.operation.helpers;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.common.merkle.utility.Keyed;

public class MerkleAccount extends PartialNaryMerkleInternal implements MerkleInternal, Keyed<EntityNum> {

    @Override
    public MerkleInternal copy() {
        return null;
    }

    @Override
    public long getClassId() {
        return 0;
    }

    @Override
    public int getVersion() {
        return 0;
    }

    @Override
    public EntityNum getKey() {
        return null;
    }

    @Override
    public void setKey(EntityNum entityNum) {}

    @Override
    public int getNumberOfChildren() {
        return 0;
    }

    @Override
    public <T extends MerkleNode> T getChild(int i) {
        return null;
    }

    @Override
    public void setChild(int index, MerkleNode child) {
        MerkleInternal.super.setChild(index, child);
    }

    @Override
    public void setChild(int i, MerkleNode merkleNode, MerkleRoute merkleRoute, boolean b) {}

    @Override
    public boolean isLeaf() {
        return false;
    }

    @Override
    public boolean isInternal() {
        return MerkleInternal.super.isInternal();
    }

    @Override
    public MerkleLeaf asLeaf() {
        return MerkleInternal.super.asLeaf();
    }

    @Override
    public MerkleInternal asInternal() {
        return MerkleInternal.super.asInternal();
    }

    @Override
    public <T extends MerkleNode> T cast() {
        return MerkleInternal.super.cast();
    }

    public long getExpiry() {
        return 0;
    }

    @Override
    public void reserve() {}

    @Override
    public boolean tryReserve() {
        return false;
    }

    @Override
    public void release() {}

    @Override
    public boolean isDestroyed() {
        return false;
    }

    @Override
    public int getReservationCount() {
        return 0;
    }

    @Override
    public Hash getHash() {
        return null;
    }

    @Override
    public void setHash(Hash hash) {}

    @Override
    public MerkleRoute getRoute() {
        return null;
    }

    @Override
    public void setRoute(MerkleRoute merkleRoute) {}
}
