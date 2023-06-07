/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.web3.evm.account;

import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.services.utils.MiscUtils.isRecoveredEvmAddress;

import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.evm.store.contract.MirrorEntityAccess;
import com.hedera.mirror.web3.exception.EntityNotFoundException;
import com.hedera.mirror.web3.exception.InvalidParametersException;
import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hedera.services.jproto.JECDSASecp256k1Key;
import com.hedera.services.jproto.JKey;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

@RequiredArgsConstructor
public class MirrorEvmContractAliases extends HederaEvmContractAliases {
    private final MirrorEntityAccess mirrorEntityAccess;

    public static final UnaryOperator<byte[]> ADDRESS_RECOVERY_FN = EthSigsUtils::recoverAddressFromPubKey;
    final Map<Address, Address> aliases = new HashMap<>();

    public boolean maybeLinkEvmAddress(@Nullable final JKey key, final Address address) {
        return maybeLinkEvmAddress(key, address, ADDRESS_RECOVERY_FN);
    }

    boolean maybeLinkEvmAddress(
            @Nullable final JKey key, final Address address, final UnaryOperator<byte[]> addressRecovery) {
        final var evmAddress = tryAddressRecovery(key, addressRecovery);
        if (isRecoveredEvmAddress(evmAddress)) {
            link(Address.wrap(Bytes.wrap(evmAddress)), address);
            return true;
        } else {
            return false;
        }
    }

    @Nullable
    public static byte[] tryAddressRecovery(@Nullable final JKey key, final UnaryOperator<byte[]> addressRecovery) {
        if (key != null) {
            // Only compressed keys are stored at the moment
            final var keyBytes = key.getECDSASecp256k1Key();
            if (keyBytes.length == JECDSASecp256k1Key.ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH) {
                final var evmAddress = addressRecovery.apply(keyBytes);
                if (isRecoveredEvmAddress(evmAddress)) {
                    return evmAddress;
                }
            }
        }
        return null;
    }

    @Override
    public Address resolveForEvm(Address addressOrAlias) {
        // returning the zero address in cases when estimating contract creations
        if (addressOrAlias.equals(Address.ZERO)) {
            return addressOrAlias;
        }

        if (aliases.containsKey(addressOrAlias)) {
            return aliases.get(addressOrAlias);
        }

        final var entity = mirrorEntityAccess
                .findEntity(addressOrAlias)
                .orElseThrow(() -> new EntityNotFoundException("No such contract or token: " + addressOrAlias));

        final var entityId = entity.toEntityId();

        if (entity.getType() == EntityType.TOKEN) {
            final var bytes = Bytes.wrap(toEvmAddress(entityId));
            return Address.wrap(bytes);
        } else if (entity.getType() == EntityType.CONTRACT) {
            final var bytes =
                    Bytes.wrap(entity.getEvmAddress() != null ? entity.getEvmAddress() : toEvmAddress(entityId));
            return Address.wrap(bytes);
        } else {
            throw new InvalidParametersException("Not a contract or token: " + addressOrAlias);
        }
    }

    public void link(final Address alias, final Address address) {
        aliases.put(alias, address);
    }

    public void unlink(Address alias) {
        aliases.remove(alias);
    }

    public Map<Address, Address> getAliases() {
        return aliases;
    }
}
