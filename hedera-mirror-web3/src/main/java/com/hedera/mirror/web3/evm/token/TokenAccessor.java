package com.hedera.mirror.web3.evm.token;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;

//TODO to be deleted
public interface TokenAccessor {

    Optional<TokenNftInfo> evmNftInfo(final Address nft, long serialNo, final ByteString ledgerId);

    boolean isTokenAddress(final Address address);

    boolean isFrozen(final Address account, final Address token);

    boolean defaultFreezeStatus(final Address token);

    boolean defaultKycStatus(final Address token);

    boolean isKyc(final Address account, final Address token);

    Optional<List<CustomFee>> infoForTokenCustomFees(final Address token);

    TokenType typeOf(final Address token);

    Optional<TokenInfo> infoForToken(final Address token, final ByteString ledgerId);

    Key keyOf(final Address token, final Integer keyType);

    String nameOf(final Address token);

    String symbolOf(final Address token);

    long totalSupplyOf(final Address token);

    int decimalsOf(final Address token);

    long balanceOf(final Address account, final Address token);

    long staticAllowanceOf(final Address owner, final Address spender, final Address token);

    Address staticApprovedSpenderOf(final Address nft, long serialNo);

    boolean staticIsOperator(final Address owner, final Address operator, final Address token);

    Address ownerOf(final Address nft, long serialNo);

    Address canonicalAddress(final Address addressOrAlias);

    String metadataOf(final Address nft, long serialNo);
}
