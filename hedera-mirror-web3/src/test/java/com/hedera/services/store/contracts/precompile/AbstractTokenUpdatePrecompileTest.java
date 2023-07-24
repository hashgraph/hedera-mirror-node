/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.store.contracts.precompile;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AbstractTokenUpdatePrecompileTest {

    //    @Mock
    //    private MessageFrame frame;
    //
    //
    //    @Mock
    //    private ContractAliases aliases;
    //
    //    @Mock
    //    private SyntheticTxnFactory syntheticTxnFactory;
    //
    //    @Mock
    //    private InfrastructureFactory infrastructureFactory;
    //
    //
    //    @Mock
    //    private HederaTokenStore tokenStore;
    //
    //    @Mock
    //    private TokenUpdateLogic updateLogic;
    //
    //    @Mock
    //    private BlockValues blockValues;
    //
    //    @Mock
    //    private LegacyActivationTest legacyActivationTest;
    //
    //    private SigTestingTokenUpdatePrecompile subject;
    //
    //    @BeforeEach
    //    void setUp() {
    //        subject = new SigTestingTokenUpdatePrecompile(
    //                aliases,
    //                sigsVerifier,
    //                sideEffectsTracker,
    //                syntheticTxnFactory,
    //                infrastructureFactory,
    //                pricingUtils);
    //    }
    //
    //    @Test
    //    void validatesAdminKeyAndNewTreasury() {
    //        final var captor = forClass(KeyActivationTest.class);
    //        final var legacyCaptor = forClass(LegacyKeyActivationTest.class);
    //
    //        given(frame.getBlockValues()).willReturn(blockValues);
    //        given(infrastructureFactory.newHederaTokenStore(any(), any(), any(), any()))
    //                .willReturn(tokenStore);
    //        given(infrastructureFactory.newTokenUpdateLogic(tokenStore, ledgers, sideEffectsTracker))
    //                .willReturn(updateLogic);
    //        given(updateLogic.validate(any())).willReturn(OK);
    //
    //        subject.useBodyWithNewTreasury();
    //        subject.run(frame);
    //
    //        verify(keyValidator)
    //                .validateKey(
    //                        eq(frame), eq(tokenMirrorAddress), captor.capture(), eq(ledgers), eq(aliases),
    // eq(TokenUpdate));
    //        verify(legacyKeyValidator)
    //                .validateKey(
    //                        eq(frame),
    //                        eq(newTreasuryMirrorAddress),
    //                        legacyCaptor.capture(),
    //                        eq(ledgers),
    //                        eq(aliases),
    //                        eq(TokenUpdate));
    //        verify(updateLogic).updateToken(any(), anyLong(), eq(true));
    //        // and when:
    //        final var tests = captor.getAllValues();
    //        final var legacyTests = legacyCaptor.getAllValues();
    //        tests.get(0).apply(false, tokenMirrorAddress, pretendActiveContract, ledgers, CryptoTransfer);
    //        verify(sigsVerifier)
    //                .hasActiveAdminKey(false, tokenMirrorAddress, pretendActiveContract, ledgers, CryptoTransfer);
    //        legacyTests
    //                .get(0)
    //                .apply(
    //                        false,
    //                        newTreasuryMirrorAddress,
    //                        pretendActiveContract,
    //                        ledgers,
    //                        legacyActivationTest,
    //                        TokenUpdate);
    //        verify(sigsVerifier)
    //                .hasLegacyActiveKey(
    //                        false,
    //                        newTreasuryMirrorAddress,
    //                        pretendActiveContract,
    //                        ledgers,
    //                        legacyActivationTest,
    //                        TokenUpdate);
    //    }
    //
    //    @Test
    //    void validatesAdminKeyAndNewAutoRenew() {
    //        final var captor = forClass(KeyActivationTest.class);
    //        final var legacyCaptor = forClass(LegacyKeyActivationTest.class);
    //
    //        given(infrastructureFactory.newHederaTokenStore(any(), any(), any(), any()))
    //                .willReturn(tokenStore);
    //        given(infrastructureFactory.newTokenUpdateLogic(tokenStore, ledgers, sideEffectsTracker))
    //                .willReturn(updateLogic);
    //        given(updateLogic.validate(any())).willReturn(OK);
    //
    //        subject.useBodyWithNewAutoRenew();
    //        subject.run(frame);
    //
    //        verify(keyValidator)
    //                .validateKey(
    //                        eq(frame), eq(tokenMirrorAddress), captor.capture(), eq(ledgers), eq(aliases),
    // eq(TokenUpdate));
    //        verify(legacyKeyValidator)
    //                .validateKey(
    //                        eq(frame),
    //                        eq(newAutoRenewMirrorAddress),
    //                        legacyCaptor.capture(),
    //                        eq(ledgers),
    //                        eq(aliases),
    //                        eq(TokenUpdate));
    //        verify(updateLogic).updateTokenExpiryInfo(any());
    //        // and when:
    //        final var tests = captor.getAllValues();
    //        final var legacyTests = legacyCaptor.getAllValues();
    //        tests.get(0).apply(false, tokenMirrorAddress, pretendActiveContract, ledgers, CryptoTransfer);
    //        verify(sigsVerifier)
    //                .hasActiveAdminKey(false, tokenMirrorAddress, pretendActiveContract, ledgers, CryptoTransfer);
    //        legacyTests
    //                .get(0)
    //                .apply(
    //                        false,
    //                        newAutoRenewMirrorAddress,
    //                        pretendActiveContract,
    //                        ledgers,
    //                        legacyActivationTest,
    //                        TokenUpdate);
    //        verify(sigsVerifier)
    //                .hasLegacyActiveKey(
    //                        false,
    //                        newAutoRenewMirrorAddress,
    //                        pretendActiveContract,
    //                        ledgers,
    //                        legacyActivationTest,
    //                        TokenUpdate);
    //    }
    //
    //    @Test
    //    void validatesOnlyAdminKeyWithNoOtherSigReqs() {
    //        final var captor = forClass(KeyActivationTest.class);
    //
    //        given(frame.getBlockValues()).willReturn(blockValues);
    //        given(infrastructureFactory.newHederaTokenStore(any(), any(), any(), any()))
    //                .willReturn(tokenStore);
    //        given(infrastructureFactory.newTokenUpdateLogic(tokenStore, ledgers, sideEffectsTracker))
    //                .willReturn(updateLogic);
    //        given(updateLogic.validate(any())).willReturn(OK);
    //        given(keyValidator.validateKey(any(), any(), any(), any(), any(), eq(TokenUpdate)))
    //                .willReturn(true);
    //        given(ledgers.accounts()).willReturn(accounts);
    //
    //        subject.useBodyWithNoOtherSigReqs();
    //        subject.run(frame);
    //
    //        verify(keyValidator)
    //                .validateKey(
    //                        eq(frame), eq(tokenMirrorAddress), captor.capture(), eq(ledgers), eq(aliases),
    // eq(TokenUpdate));
    //        verifyNoMoreInteractions(keyValidator);
    //        verify(updateLogic).updateToken(any(), anyLong(), eq(true));
    //        // and when:
    //        final var tests = captor.getAllValues();
    //        tests.get(0).apply(false, tokenMirrorAddress, pretendActiveContract, ledgers, CryptoTransfer);
    //        verify(sigsVerifier)
    //                .hasActiveAdminKey(false, tokenMirrorAddress, pretendActiveContract, ledgers, CryptoTransfer);
    //        verifyNoMoreInteractions(sigsVerifier);
    //    }
    //
    //    private static class SigTestingTokenUpdatePrecompile extends AbstractTokenUpdatePrecompile {
    //        protected SigTestingTokenUpdatePrecompile(
    //                final SyntheticTxnFactory syntheticTxnFactory,
    //                final InfrastructureFactory infrastructureFactory,
    //                final PrecompilePricingUtils pricingUtils) {
    //            super(
    //                    syntheticTxnFactory,
    //                    infrastructureFactory,
    //                    pricingUtils);
    //        }
    //
    //        @Override
    //        public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
    //            throw new NotImplementedException();
    //        }
    //
    //        public void useBodyWithNewTreasury() {
    //            type = UpdateType.UPDATE_TOKEN_INFO;
    //            tokenId = Id.fromGrpcToken(targetId);
    //            final var updateOp = baseBuilder().setTreasury(newTreasury);
    //            transactionBody = TransactionBody.newBuilder().setTokenUpdate(updateOp);
    //        }
    //
    //        public void useBodyWithNewAutoRenew() {
    //            type = UpdateType.UPDATE_TOKEN_EXPIRY;
    //            tokenId = Id.fromGrpcToken(targetId);
    //            final var updateOp = baseBuilder().setAutoRenewAccount(newAutoRenew);
    //            transactionBody = TransactionBody.newBuilder().setTokenUpdate(updateOp);
    //        }
    //
    //        public void useBodyWithNoOtherSigReqs() {
    //            type = UpdateType.UPDATE_TOKEN_INFO;
    //            tokenId = Id.fromGrpcToken(targetId);
    //            final var updateOp = baseBuilder();
    //            transactionBody = TransactionBody.newBuilder().setTokenUpdate(updateOp);
    //        }
    //
    //        private TokenUpdateTransactionBody.Builder baseBuilder() {
    //            return TokenUpdateTransactionBody.newBuilder().setToken(targetId);
    //        }
    //    }
    //
    //    private static final TokenID targetId =
    //            TokenID.newBuilder().setTokenNum(666).build();
    //    private static final Id tokenId = Id.fromGrpcToken(targetId);
    //    private static final AccountID newTreasury =
    //            AccountID.newBuilder().setAccountNum(2345).build();
    //    private static final AccountID newAutoRenew =
    //            AccountID.newBuilder().setAccountNum(7777).build();
    //    private static final Address tokenMirrorAddress = tokenId.asEvmAddress();
    //    private static final Address newTreasuryMirrorAddress =
    //            Id.fromGrpcAccount(newTreasury).asEvmAddress();
    //    private static final Address newAutoRenewMirrorAddress =
    //            Id.fromGrpcAccount(newAutoRenew).asEvmAddress();
    //    private static final Address pretendActiveContract = Address.BLAKE2B_F_COMPRESSION;

}
