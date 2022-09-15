package com.hedera.mirror.web3.evm;

import java.util.HashMap;
import java.util.Map;
import org.hyperledger.besu.datatypes.Address;

import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.mirror.web3.repository.TokenRepository;

public class SimulatedBackingTokens {
    private final Map<Address, SimulatedToken> delegate = new HashMap<>();
    private final NftRepository nftRepository;
    private final TokenRepository tokenRepository;
    private final EntityRepository entityRepository;
    private final SimulatedEntityAccess entityAccess;

    public SimulatedBackingTokens(NftRepository nftRepository, TokenRepository tokenRepository,
                                  EntityRepository entityRepository, SimulatedEntityAccess entityAccess) {
        this.nftRepository = nftRepository;
        this.tokenRepository = tokenRepository;
        this.entityRepository = entityRepository;
        this.entityAccess = entityAccess;
    }

    public SimulatedToken getRef(Address id) {
        var simulatedToken = delegate.get(id);
        if(simulatedToken == null) {
            final var token = tokenRepository.findByAddress(id.toArray()).orElse(null);
            if(token!=null) {
                simulatedToken = new SimulatedToken(id, nftRepository, tokenRepository, entityRepository, entityAccess);
                delegate.put(id, simulatedToken);
            }
        }
        return simulatedToken;
    }

    public SimulatedToken getImmutableRef(Address id) {
        final var simulatedToken = getRef(id);

        if (simulatedToken != null) {
            return simulatedToken.getSafeCopy();
        } else {
            return null;
        }
    }
}
