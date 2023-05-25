package com.hedera.services.fees.charging;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.mirror.web3.evm.store.accessor.AccountDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.DatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.EntityDatabaseAccessor;
import com.hedera.mirror.web3.repository.CryptoAllowanceRepository;
import com.hedera.mirror.web3.repository.NftAllowanceRepository;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.mirror.web3.repository.TokenAccountRepository;
import com.hedera.mirror.web3.repository.TokenAllowanceRepository;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.numbers.HederaAccountNumbers;
import com.hedera.services.numbers.MockAccountNumbers;
import com.hedera.services.store.models.Account;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeDistributionTest {
    private static final long BALANCE = 10L;
    private final GlobalDynamicProperties dynamicProperties = new MockGlobalDynamicProps();
    private final HederaAccountNumbers accountNums = new MockAccountNumbers();
    private final AccountID fundingAccountId = dynamicProperties.fundingAccount();
    private final Address fundingAddress = EntityIdUtils.asTypedEvmAddress(fundingAccountId);
    private final AccountID nodeRewardAccountId = STATIC_PROPERTIES.scopedAccountWith(accountNums.nodeRewardAccount());
    private final Address nodeRewardAddress = EntityIdUtils.asTypedEvmAddress(nodeRewardAccountId);
    private final AccountID stakingRewardAccountId = STATIC_PROPERTIES.scopedAccountWith(
            accountNums.stakingRewardAccount());
    private final Address stakingRewardAddress = EntityIdUtils.asTypedEvmAddress(stakingRewardAccountId);
    Entity fundingEntity = new Entity();
    Entity nodeRewardEntity = new Entity();
    Entity stakingRewardEntity = new Entity();
    @Mock
    private EntityDatabaseAccessor entityDatabaseAccessor;
    @Mock
    private TokenAccountRepository tokenAccountRepository;
    @Mock
    private NftRepository nftRepository;
    @Mock
    private CryptoAllowanceRepository cryptoAllowanceRepository;
    @Mock
    private TokenAllowanceRepository tokenAllowanceRepository;
    @Mock
    private NftAllowanceRepository nftAllowanceRepository;
    @InjectMocks
    private AccountDatabaseAccessor accountAccessor;

    @BeforeEach
    void setup() {
        fundingEntity.setBalance(BALANCE);
        fundingEntity.setShard(0L);
        fundingEntity.setRealm(0L);
        fundingEntity.setNum(fundingAccountId.getAccountNum());
        fundingEntity.setId(fundingAccountId.getAccountNum());
        when(entityDatabaseAccessor.get(fundingAddress)).thenReturn(Optional.of(fundingEntity));

        nodeRewardEntity.setBalance(BALANCE);
        nodeRewardEntity.setShard(0L);
        nodeRewardEntity.setRealm(0L);
        nodeRewardEntity.setNum(nodeRewardAccountId.getAccountNum());
        nodeRewardEntity.setId(nodeRewardAccountId.getAccountNum());
        when(entityDatabaseAccessor.get(nodeRewardAddress)).thenReturn(Optional.of(nodeRewardEntity));

        stakingRewardEntity.setBalance(BALANCE);
        stakingRewardEntity.setShard(0L);
        stakingRewardEntity.setRealm(0L);
        stakingRewardEntity.setNum(stakingRewardAccountId.getAccountNum());
        stakingRewardEntity.setId(stakingRewardAccountId.getAccountNum());
        when(entityDatabaseAccessor.get(stakingRewardAddress)).thenReturn(Optional.of(stakingRewardEntity));

        when(tokenAccountRepository.countByAccountIdAndAssociatedGroupedByBalanceIsPositive(anyLong())).thenReturn(
                List.of());
        when(nftRepository.countByAccountIdNotDeleted(any())).thenReturn(0L);
        when(cryptoAllowanceRepository.findByOwner(anyLong())).thenReturn(List.of());
        when(tokenAllowanceRepository.findByOwner(anyLong())).thenReturn(List.of());
        when(nftAllowanceRepository.findByOwnerAndApprovedForAllIsTrue(anyLong())).thenReturn(List.of());
    }

    @Test
    void distributeChargedFee() {
        // when
        final var accessors = new ArrayList<DatabaseAccessor<Address, ?>>();
        accessors.add(accountAccessor);
        final var sut = new StackedStateFrames<>(accessors);

        // Push 2 RW-frame caches
        sut.push();
        sut.push();

        FeeDistribution feeDistribution = new FeeDistribution(accountNums, dynamicProperties);
        feeDistribution.distributeChargedFee(10L, sut);

        // then
        final var accessor = sut.top().getAccessor(Account.class);
        final var newFundingAddressBalance = accessor.get(fundingAddress).orElseThrow().getBalance();
        final var newNodeRewardAddressBalance = accessor.get(nodeRewardAddress).orElseThrow().getBalance();
        final var newStakingRewardAddressBalance = accessor.get(nodeRewardAddress).orElseThrow().getBalance();

        // The initial balances of all 3 addresses is 10.
        // We distribute charged fee of 10.
        // nodeRewardPercent and stakingRewardPercent are both 10%
        // so nodeRewardAddress and stakingRewardAddress receive 1 token.
        // The remaining 8 are sent to the fundingAddress
        assertThat(newFundingAddressBalance).isEqualTo(18L);
        assertThat(newNodeRewardAddressBalance).isEqualTo(11L);
        assertThat(newStakingRewardAddressBalance).isEqualTo(11L);
    }
}
