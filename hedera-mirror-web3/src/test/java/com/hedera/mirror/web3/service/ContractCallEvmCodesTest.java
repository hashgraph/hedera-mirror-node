/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.web3j.TestWeb3jService;
import com.hedera.mirror.web3.web3j.TestWeb3jService.Web3jTestConfiguration;
import com.hedera.mirror.web3.web3j.generated.EthCall;
import com.hedera.mirror.web3.web3j.generated.EvmCodes;
import com.hedera.mirror.web3.web3j.generated.EvmCodes.G1Point;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.Import;

@Import(Web3jTestConfiguration.class)
@RequiredArgsConstructor
class ContractCallEvmCodesTest extends Web3IntegrationTest {

    private final TestWeb3jService testWeb3jService;

    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @BeforeEach
    void beforeAll() {
        domainBuilder.recordFile().persist();
    }

    @Test
    void chainId() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var chainId = contract.call_chainId().send();
        assertThat(chainId).isEqualTo(mirrorNodeEvmProperties.chainIdBytes32().toBigInteger());
    }

    @Test
    void ECRECPrecompiledContract() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_recoverAddress().send();
        assertThat(result).isNotEmpty();
    }

    @Test
    void SHA256PrecompiledContract() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_calculateSHA256().send();
        assertThat(Bytes.wrap(result).toHexString())
                .isEqualTo("0xe93bb3ba29b71e2623d3d6e4c0f266c41cb005259e8cad8c4d04f966053ac712");
    }

    @Test
    void RIPEMD160PrecompiledContract() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_calculateRIPEMD160().send();
        assertThat(Bytes.wrap(result).toHexString())
                .isEqualTo("0x000000000000000000000000c4861db52a25298b7ba404b6f8a65d2d6473c1a9");
    }

    @Test
    void IDPrecompiledContract() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_identity().send();
        assertThat(Bytes.wrap(result).toHexString()).contains("48656c6c6f2c20576f726c64");
    }

    @Test
    void BigIntegerModularExponentiationPrecompiledContract() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_modExp().send();
        assertThat(result).isEqualTo(BigInteger.valueOf(4));
    }

    @Test
    void AltBN128AddPrecompiledContract() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_addPoints().send();
        assertThat(result)
                .isEqualTo(new G1Point(
                        new BigInteger("1368015179489954701390400359078579693043519447331113978918064868415326638035"),
                        new BigInteger(
                                "9918110051302171585080402603319702774565515993150576347155970296011118125764")));
    }

    @Test
    void AltBN128MulPrecompiledContract() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_multiplyPoints().send();
        assertThat(result)
                .isEqualTo(new G1Point(
                        new BigInteger("3353031288059533942658390886683067124040920775575537747144343083137631628272"),
                        new BigInteger(
                                "19321533766552368860946552437480515441416830039777911637913418824951667761761")));
    }

    @Test
    void AltBN128PairingPrecompiledContract() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_pairingCheck().send();
        assertThat(result).isEqualTo(true);
    }

    @Test
    void BLAKE2BFPrecompileContract() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_blake2().send();
        String expectedResultHexString =
                "0xba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6fdbffa2d17d87c5392aab792dc252d5de4533cc9518d38aa8dbf1925ab92386edd4009923";
        List<byte[]> expetedList = convertBlake2HexToList(expectedResultHexString.substring(2));
        assertThat(result.get(0)).isEqualTo(expetedList.get(0));
        assertThat(result.get(1)).isEqualTo(expetedList.get(1));
    }

    @Test
    void getBlockPrevrandao() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_getBlockPrevrandao().send();
        assertThat(result).isNotNull();
        assertTrue(result.compareTo(BigInteger.ZERO) > 0);
    }

    @Test
    void getBlockHashReturnsCorrectHash() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        final var recordFileForBlockHash = domainBuilder.recordFile().persist();
        // We need to wait enough time for the record file to be processed
        Thread.sleep(500);
        var result = contract.call_getBlockHash(BigInteger.valueOf(recordFileForBlockHash.getIndex()))
                .send();
        var expectedResult =
                hexStringToByteArray(recordFileForBlockHash.getHash().substring(0, 64));
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    void getGenesisBlockHashReturnsCorrectBlock() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        final var genesisRecordFileForBlockHash =
                domainBuilder.recordFile().customize(f -> f.index(0L)).persist();
        var result = contract.call_getBlockHash(BigInteger.ZERO).send();
        var expectedResult =
                hexStringToByteArray(genesisRecordFileForBlockHash.getHash().substring(0, 64));
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    void getLatestBlockHashIsNotEmpty() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_getLatestBlockHash().send();
        var expectedResult = hexStringToByteArray("0000000000000000000000000000000000000000000000000000000000000000");
        assertThat(result).isNotEqualTo(expectedResult);
    }

    @Test
    void getBlockHashAfterTheLatestReturnsZero() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result =
                contract.call_getBlockHash(BigInteger.valueOf(Long.MAX_VALUE)).send();
        var expectedResult = hexStringToByteArray("0000000000000000000000000000000000000000000000000000000000000000");
        assertThat(result).isEqualTo(expectedResult);
    }

    @ParameterizedTest
    @CsvSource({
        // function getCodeHash with parameter hedera system accounts, expected 0 bytes
        "0000000000000000000000000000000000000000000000000000000000000167, 0000000000000000000000000000000000000000000000000000000000000000",
        "0000000000000000000000000000000000000000000000000000000000000168, 0000000000000000000000000000000000000000000000000000000000000000",
        "00000000000000000000000000000000000000000000000000000000000002ee, 0000000000000000000000000000000000000000000000000000000000000000",
        "00000000000000000000000000000000000000000000000000000000000002e4, 0000000000000000000000000000000000000000000000000000000000000000",
    })
    void testSystemContractCodeHash(String input, String expectedOutput) throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_getCodeHash(input).send();
        var expectedResult = hexStringToByteArray(expectedOutput);
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    void testNonSystemContractCodeHash() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        final var ethCallContract = testWeb3jService.deploy(EthCall::deploy);
        final var result =
                contract.call_getCodeHash(ethCallContract.getContractAddress()).send();
        String keccak256ofEthCallExpected = "4d458231141d3a62c392d17732d8839ab80c88d9307b97906286065349c3a1d8";
        assertThat(result).isEqualTo(hexStringToByteArray(keccak256ofEthCallExpected));
    }

    @Test
    void testNonSystemAccountCodeHash() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        final var address = toAddress(1078);
        final var autoRenewEntityId = entityIdFromEvmAddress(address);
        domainBuilder
                .entity()
                .customize(e -> e.id(autoRenewEntityId.getId())
                        .num(autoRenewEntityId.getNum())
                        .evmAddress(null)
                        .alias(toEvmAddress(autoRenewEntityId)))
                .persist();

        final var result = contract.call_getCodeHash(address.toString()).send();
        String keccak256ofEthCallExpected = "c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470";
        assertThat(result).isEqualTo(hexStringToByteArray(keccak256ofEthCallExpected));
    }

    @Test
    void selfDestructCall() throws Exception {
        // Given
        final var senderAddress = toAddress(1043);
        final var senderAlias = Bytes.wrap(recoverAddressFromPubKey(ByteString.copyFrom(
                        Hex.decode("3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d"))
                .substring(2)
                .toByteArray()));
        final var senderEntityId = entityIdFromEvmAddress(senderAddress);
        final var senderPublicKey = ByteString.copyFrom(
                Hex.decode("3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d"));
        domainBuilder
                .entity()
                .customize(e -> e.id(senderEntityId.getId())
                        .num(senderEntityId.getNum())
                        .evmAddress(senderAlias.toArray())
                        .deleted(false)
                        .alias(senderPublicKey.toByteArray())
                        .balance(10000 * 100_000_000L))
                .persist();

        // When
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.send_destroyContract(senderAlias.toUnprefixedHexString())
                .send();

        // Then
        assertThat(result.getContractAddress()).isEqualTo("0x");
    }

    @Test
    void selfDestructCallWithSystemAccount() {
        // Given
        final var systemAccountAddress = toAddress(700);
        final var systemAccountEntityId = entityIdFromEvmAddress(systemAccountAddress);
        domainBuilder
                .entity()
                .customize(e -> e.id(systemAccountEntityId.getId())
                        .num(systemAccountEntityId.getNum())
                        .evmAddress(null)
                        .alias(toEvmAddress(systemAccountEntityId))
                        .balance(20000L))
                .persist();
        // When
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);

        // Then
        assertThatThrownBy(() -> contract.send_destroyContract(systemAccountAddress.toUnprefixedHexString())
                        .send())
                .isInstanceOf(MirrorEvmTransactionException.class)
                .satisfies(ex -> {
                    MirrorEvmTransactionException exception = (MirrorEvmTransactionException) ex;
                    assertEquals(exception.getMessage(), INVALID_SOLIDITY_ADDRESS.name());
                });
    }

    private List<byte[]> convertBlake2HexToList(String hex) {
        // Convert hex string to byte array
        byte[] byteArray = hexStringToByteArray(hex);

        // Split byte array into two equal parts
        int mid = byteArray.length / 2;
        byte[] firstPart = Arrays.copyOfRange(byteArray, 0, mid);
        byte[] secondPart = Arrays.copyOfRange(byteArray, mid, byteArray.length);
        return Arrays.asList(firstPart, secondPart);
    }

    private static byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
