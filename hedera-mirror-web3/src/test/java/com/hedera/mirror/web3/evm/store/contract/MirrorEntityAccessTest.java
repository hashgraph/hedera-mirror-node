package com.hedera.mirror.web3.evm.store.contract;

import static com.google.protobuf.ByteString.EMPTY;
import static com.hedera.mirror.web3.evm.util.EntityUtils.numFromEvmAddress;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.repository.EntityAccessRepository;

@ExtendWith(MockitoExtension.class)
class MirrorEntityAccessTest {
    private static final String HEX = "0x00000000000000000000000000000000000004e4";
    private static final Bytes BYTES = Bytes.fromHexString(HEX);
    private static final byte[] DATA = BYTES.toArrayUnsafe();
    private static final Address ADDRESS = Address.fromHexString(HEX);
    private static final Long ACCOUNT_NUM = numFromEvmAddress(ADDRESS.toArrayUnsafe());
    @Mock
    private EntityAccessRepository entityAccessRepository;

    @InjectMocks
    private MirrorEntityAccess mirrorEntityAccess;

    @Test
    void getBalance() {
        final long balance = 23L;
        when(entityAccessRepository.getBalance(ACCOUNT_NUM)).thenReturn(Optional.of(balance));
        final var result = mirrorEntityAccess.getBalance(ADDRESS);
        assertEquals(balance, result);
    }

    @Test
    void isExtant() {
        when(entityAccessRepository.existsById(ACCOUNT_NUM)).thenReturn(true);
        assertTrue(mirrorEntityAccess.isExtant(ADDRESS));
    }

    @Test
    void isTokenAccount() {
        when(entityAccessRepository.getType(ACCOUNT_NUM)).thenReturn(Optional.of(EntityType.TOKEN));
        assertTrue(mirrorEntityAccess.isTokenAccount(ADDRESS));
    }

    @Test
    void isNotATokenAccount() {
        when(entityAccessRepository.getType(ACCOUNT_NUM)).thenReturn(Optional.of(EntityType.ACCOUNT));
        assertFalse(mirrorEntityAccess.isTokenAccount(ADDRESS));
    }

    @Test
    void getAlias() {
        when(entityAccessRepository.getAlias(ACCOUNT_NUM)).thenReturn(Optional.of(DATA));
        final var result = mirrorEntityAccess.alias(ADDRESS);
        assertNotEquals(EMPTY, result);
    }

    @Test
    void getStorage() {
        when(entityAccessRepository.getStorage(ACCOUNT_NUM, BYTES.toArrayUnsafe())).thenReturn(Optional.of(DATA));
        final var result = UInt256.fromBytes(mirrorEntityAccess.getStorage(ADDRESS, BYTES));
        assertEquals(UInt256.fromHexString(HEX), result);
    }

    @Test
    void fetchCodeIfPresent() {
        when(entityAccessRepository.fetchContractCode(ACCOUNT_NUM)).thenReturn(Optional.of(DATA));
        final var result = mirrorEntityAccess.fetchCodeIfPresent(ADDRESS);
        assertEquals(BYTES, result);
    }
}
