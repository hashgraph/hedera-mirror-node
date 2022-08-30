package com.hedera.mirror.web3.evm;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import lombok.Data;
import org.hyperledger.besu.datatypes.Address;

import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.mirror.web3.repository.TokenRepository;

@Data
public class SimulatedBackingTokens implements SimulatedBackingStore<Address, SimulatedToken> {
    private Supplier<Map<Address, SimulatedToken>> delegate;
    private final NftRepository nftRepository;
    private final TokenRepository tokenRepository;
    private final EntityRepository entityRepository;
    private final SimulatedEntityAccess entityAccess;

    @Override
    public SimulatedToken getRef(Address id) {
        var simulatedToken = delegate.get().get(id);
        if(simulatedToken == null) {
            final var token = tokenRepository.findByAddress(id.toArray()).orElse(null);
            if(token!=null) {
                simulatedToken = new SimulatedToken(id, nftRepository, tokenRepository, entityRepository, entityAccess);
            }
        }

        return simulatedToken;
    }

    @Override
    public void put(Address id, SimulatedToken token) {
        final var tokens = delegate.get();
        if (!tokens.containsKey(id)) {
            tokens.put(id, token);
        }
    }

    @Override
    public boolean contains(Address id) {
        return delegate.get().containsKey(id);
    }

    @Override
    public void remove(Address id) {
        delegate.get().remove(id);
    }

    @Override
    public Set<Address> idSet() {
        return delegate.get().keySet();
    }

    @Override
    public long size() {
        return delegate.get().size();
    }

    @Override
    public SimulatedToken getImmutableRef(Address id) {
        final var simulatedToken = getRef(id);

        if (simulatedToken != null) {
            return simulatedToken.getSafeCopy();
        } else {
            return null;
        }
    }
}
