package com.hedera.services.transaction.operation.helpers;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import com.hedera.services.transaction.ethereum.EthTxSigs;

import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.commons.codec.DecoderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.datatypes.Address;
import org.jetbrains.annotations.Nullable;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static com.hedera.services.transaction.operation.helpers.EntityNum.MISSING_NUM;
import static com.hedera.services.transaction.operation.util.MiscUtils.forEach;
import static com.swirlds.common.utility.CommonUtils.hex;

public class AliasManager {
    private static final Logger log = LogManager.getLogger(AliasManager.class);
    public static final int EVM_ADDRESS_LEN = 20;
    private static byte[] mirrorPrefix = null;

    private static final String NON_TRANSACTIONAL_MSG =
            "Base alias manager does not buffer changes";
    private static final UnaryOperator<byte[]> ADDRESS_RECOVERY_FN =
            EthTxSigs::recoverAddressFromPubKey;

    private final Supplier<Map<ByteString, EntityNum>> aliases;

    @Inject
    public AliasManager(final Supplier<Map<ByteString, EntityNum>> aliases) {
        this.aliases = aliases;
    }

    public boolean isMirror(final byte[] address) {
        return false;
    }

    public void link(final ByteString alias, final EntityNum num) {
        curAliases().put(alias, num);
    }

    public void unlink(final ByteString alias) {
        curAliases().remove(alias);
    }

    /**
     * Ensures an alias is no longer in use, returning whether it previously was.
     *
     * @param alias the alias to forget
     * @return whether it was present
     */
    public boolean forgetAlias(final ByteString alias) {
        if (alias.isEmpty()) {
            return false;
        }
        return curAliases().remove(alias) != null;
    }

    /**
     * Returns the entityNum for the given alias
     *
     * @param alias alias of the accountId
     * @return EntityNum mapped to the given alias.
     */
    public EntityNum lookupIdBy(final ByteString alias) {
        return curAliases().getOrDefault(alias, MISSING_NUM);
    }

    private Map<ByteString, EntityNum> curAliases() {
        return aliases.get();
    }

    @VisibleForTesting
    Map<ByteString, EntityNum> getAliases() {
        return curAliases();
    }
}
