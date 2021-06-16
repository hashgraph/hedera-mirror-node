/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

package construction

import (
	"encoding/hex"
	"fmt"
	"math/big"
	"reflect"
	"testing"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	hexutils "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/hex"
	types2 "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/types"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

const (
	defaultCryptoAccountId1 = "0.0.123352"
	defaultCryptoAccountId2 = "0.0.123518"
	defaultSendAmount       = "-1000"
	defaultReceiveAmount    = "1000"
	defaultNetwork          = "testnet"
)

var (
	defaultAccountId1 = hedera.AccountID{Account: 123352}
	defaultNodes      = types2.NodeMap{
		"10.0.0.1:50211": hedera.AccountID{Account: 3},
		"10.0.0.2:50211": hedera.AccountID{Account: 4},
		"10.0.0.3:50211": hedera.AccountID{Account: 5},
		"10.0.0.4:50211": hedera.AccountID{Account: 6},
	}
	validSignedTransaction   = "0x0aaa012aa7010a3d0a140a0c08feafcb840610ae86c0db03120418d8c307120218041880c2d72f2202087872180a160a090a0418d8c30710cf0f0a090a0418fec40710d00f12660a640a20eba8cc093a83a4ca5e813e30d8c503babb35c22d57d34b6ec5ac0303a6aaba771a40793de745bc19dd8fe8e817891f51b8fe1e259c2e6428bd7fa075b181585a2d40e3666a7c9a1873abb5433ffe1414502836d8d37082eaf94a648b530e9fa78108"
	validUnsignedTransaction = "0x0a432a410a3d0a140a0c08feafcb840610ae86c0db03120418d8c307120218041880c2d72f2202087872180a160a090a0418d8c30710cf0f0a090a0418fec40710d00f1200"
	invalidTransaction       = "InvalidTxHexString"
	invalidTypeTransaction   = "0x0a332a310a2d0a140a0c08a6e4cb840610f6a3aeef0112041882810c12021805188084af5f22020878c20107320508d0c8e1031200"
	corruptedTransaction     = "0x6767"
	publicKeyStr             = "eba8cc093a83a4ca5e813e30d8c503babb35c22d57d34b6ec5ac0303a6aaba77" // without ed25519PubKeyPrefix
	privateKey, _            = hedera.PrivateKeyFromString("302e020100300506032b6570042204207904b9687878e08e101723f7b724cd61a42bbff93923177bf3fcc2240b0dd3bc")
)

func dummyConstructionCombineRequest() *types.ConstructionCombineRequest {
	unsignedTransaction := "0x0a432a410a3d0a140a0c08feafcb840610ae86c0db03120418d8c307120218041880c2d72f2202087872180a160a090a0418d8c30710cf0f0a090a0418fec40710d00f1200"
	signingPayloadBytes := "967f26876ad492cc27b4c384dc962f443bcc9be33cbb7add3844bc864de047340e7a78c0fbaf40ab10948dc570bbc25edb505f112d0926dffb65c93199e6d507"
	signatureBytes := "793de745bc19dd8fe8e817891f51b8fe1e259c2e6428bd7fa075b181585a2d40e3666a7c9a1873abb5433ffe1414502836d8d37082eaf94a648b530e9fa78108"

	return dummyConstructionCombineRequestWith(
		unsignedTransaction,
		signingPayloadBytes,
		publicKeyStr,
		signatureBytes,
	)
}

func dummyConstructionPreprocessRequest(valid bool) *types.ConstructionPreprocessRequest {
	operations := []*types.Operation{
		dummyOperation(0, "CRYPTOTRANSFER", defaultCryptoAccountId1, defaultSendAmount),
		dummyOperation(1, "CRYPTOTRANSFER", defaultCryptoAccountId2, defaultReceiveAmount),
	}
	if !valid {
		operations = append(
			operations,
			dummyOperation(3, "CRYPTOTRANSFER", "123532", "-5000"),
		)
	}

	return &types.ConstructionPreprocessRequest{
		NetworkIdentifier: networkIdentifier(),
		Operations:        operations,
	}
}

func dummyConstructionCombineRequestWith(unsignedTransaction, signingPayload, publicKey, signature string) *types.ConstructionCombineRequest {
	signingPayloadBytes, e1 := hex.DecodeString(signingPayload)
	publicKeyBytes, e2 := hex.DecodeString(publicKey)
	signatureBytes, e3 := hex.DecodeString(signature)

	if e1 != nil || e2 != nil || e3 != nil {
		return nil
	}

	return &types.ConstructionCombineRequest{
		NetworkIdentifier:   networkIdentifier(),
		UnsignedTransaction: unsignedTransaction,
		Signatures: []*types.Signature{
			{
				SigningPayload: &types.SigningPayload{
					AccountIdentifier: &types.AccountIdentifier{
						Address:  defaultCryptoAccountId1,
						Metadata: nil,
					},
					Bytes:         signingPayloadBytes,
					SignatureType: types.Ed25519,
				},
				PublicKey: &types.PublicKey{
					Bytes:     publicKeyBytes,
					CurveType: types.Edwards25519,
				},
				SignatureType: types.Ed25519,
				Bytes:         signatureBytes,
			},
		},
	}
}

func dummyOperation(index int64, transferType, account, amount string) *types.Operation {
	return &types.Operation{
		OperationIdentifier: &types.OperationIdentifier{
			Index: index,
		},
		Type: transferType,
		Account: &types.AccountIdentifier{
			Address: account,
		},
		Amount: &types.Amount{
			Value: amount,
			Currency: &types.Currency{
				Symbol:   "HBAR",
				Decimals: 8,
				Metadata: map[string]interface{}{
					"issuer": "Hedera",
				},
			},
		},
	}
}

func networkIdentifier() *types.NetworkIdentifier {
	return &types.NetworkIdentifier{
		Blockchain: "SomeBlockchain",
		Network:    "SomeNetwork",
		SubNetworkIdentifier: &types.SubNetworkIdentifier{
			Network: "SomeSubNetwork",
		},
	}
}

func dummyConstructionHashRequest(signedTx string) *types.ConstructionHashRequest {
	return &types.ConstructionHashRequest{
		NetworkIdentifier: networkIdentifier(),
		SignedTransaction: signedTx,
	}
}

func dummyConstructionParseRequest(txHash string, signed bool) *types.ConstructionParseRequest {
	return &types.ConstructionParseRequest{
		NetworkIdentifier: networkIdentifier(),
		Signed:            signed,
		Transaction:       txHash,
	}
}

func dummyPayloadsRequest(operations []*types.Operation) *types.ConstructionPayloadsRequest {
	return &types.ConstructionPayloadsRequest{
		Operations: operations,
	}
}

func getHederaNetworkByName(network string) map[string]hedera.AccountID {
	client, err := hedera.ClientForName(network)
	if err != nil {
		panic(err)
	}

	return client.GetNetwork()
}

func getNodeAccountIds(network map[string]hedera.AccountID) []hedera.AccountID {
	nodeAccountIds := make([]hedera.AccountID, 0, len(network))

	for _, nodeAccountId := range network {
		nodeAccountIds = append(nodeAccountIds, nodeAccountId)
	}

	return nodeAccountIds
}

func TestNewConstructionAPIService(t *testing.T) {
	var tests = []struct {
		name                  string
		network               string
		nodes                 types2.NodeMap
		expectedHederaNetwork map[string]hedera.AccountID
		wantErr               bool
	}{
		{
			name:                  "Testnet",
			network:               "testnet",
			nodes:                 types2.NodeMap{},
			expectedHederaNetwork: getHederaNetworkByName("testnet"),
			wantErr:               false,
		},
		{
			name:                  "Previewnet",
			network:               "previewnet",
			nodes:                 types2.NodeMap{},
			expectedHederaNetwork: getHederaNetworkByName("previewnet"),
			wantErr:               false,
		},
		{
			name:                  "Mainnet",
			network:               "mainnet",
			nodes:                 types2.NodeMap{},
			expectedHederaNetwork: getHederaNetworkByName("mainnet"),
			wantErr:               false,
		},
		{
			name:                  "Demo",
			network:               "demo",
			nodes:                 types2.NodeMap{},
			expectedHederaNetwork: getHederaNetworkByName("testnet"),
			wantErr:               false,
		},
		{
			name:                  "OtherWithNodes",
			network:               "other",
			nodes:                 defaultNodes,
			expectedHederaNetwork: defaultNodes,
			wantErr:               false,
		},
		{
			name:                  "OtherWithEmptyNodes",
			network:               "other",
			nodes:                 types2.NodeMap{},
			expectedHederaNetwork: nil,
			wantErr:               true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			actual, err := NewConstructionAPIService(tt.network, tt.nodes, &mockTransactionConstructor{})

			if tt.wantErr {
				assert.Error(t, err)
			} else {
				assert.NoError(t, err)
				assert.IsType(t, &constructionAPIService{}, actual)

				service := actual.(*constructionAPIService)
				expectedNodeAccountIds := getNodeAccountIds(tt.expectedHederaNetwork)
				assert.EqualValues(t, tt.expectedHederaNetwork, service.hederaClient.GetNetwork())
				assert.ElementsMatch(t, expectedNodeAccountIds, service.nodeAccountIds)
				assert.Equal(t, big.NewInt(int64(len(service.nodeAccountIds))), service.nodeAccountIdsLen)
			}
		})
	}
}

func TestConstructionCombine(t *testing.T) {
	// given:
	expectedConstructionCombineResponse := &types.ConstructionCombineResponse{
		SignedTransaction: validSignedTransaction,
	}
	service, _ := NewConstructionAPIService(defaultNetwork, defaultNodes, nil)

	// when:
	res, e := service.ConstructionCombine(nil, dummyConstructionCombineRequest())

	// then:
	assert.Equal(t, expectedConstructionCombineResponse, res)
	assert.Nil(t, e)
}

func TestConstructionCombineThrowsWithNoSignature(t *testing.T) {
	// given
	request := dummyConstructionCombineRequest()
	request.Signatures = []*types.Signature{}
	service, _ := NewConstructionAPIService(defaultNetwork, defaultNodes, nil)

	// when
	res, e := service.ConstructionCombine(nil, request)

	// then
	assert.NotNil(t, e)
	assert.Nil(t, res)

}

func TestConstructionCombineThrowsWhenDecodeStringFails(t *testing.T) {
	// given:
	exampleCorruptedTxHexStrConstructionCombineRequest := dummyConstructionCombineRequest()
	exampleCorruptedTxHexStrConstructionCombineRequest.UnsignedTransaction = invalidTransaction

	// when:
	service, _ := NewConstructionAPIService(defaultNetwork, defaultNodes, nil)
	res, e := service.ConstructionCombine(nil, exampleCorruptedTxHexStrConstructionCombineRequest)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrTransactionDecodeFailed, e)
}

func TestConstructionCombineThrowsWhenUnmarshallFails(t *testing.T) {
	// given:
	exampleCorruptedTxHexStrConstructionCombineRequest := dummyConstructionCombineRequest()
	exampleCorruptedTxHexStrConstructionCombineRequest.UnsignedTransaction = corruptedTransaction

	// when:
	service, _ := NewConstructionAPIService(defaultNetwork, defaultNodes, nil)
	res, e := service.ConstructionCombine(nil, exampleCorruptedTxHexStrConstructionCombineRequest)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrTransactionUnmarshallingFailed, e)
}

func TestConstructionCombineThrowsWithInvalidPublicKey(t *testing.T) {
	// given:
	exampleInvalidPublicKeyConstructionCombineRequest := dummyConstructionCombineRequest()
	exampleInvalidPublicKeyConstructionCombineRequest.Signatures[0].PublicKey = &types.PublicKey{}

	// when:
	service, _ := NewConstructionAPIService(defaultNetwork, defaultNodes, nil)
	res, e := service.ConstructionCombine(nil, exampleInvalidPublicKeyConstructionCombineRequest)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrInvalidPublicKey, e)
}

func TestConstructionCombineThrowsWithInvalidSignature(t *testing.T) {
	// given:
	exampleInvalidSigningPayloadConstructionCombineRequest := dummyConstructionCombineRequest()
	exampleInvalidSigningPayloadConstructionCombineRequest.Signatures[0].Bytes = []byte("bad signature")

	// when:
	service, _ := NewConstructionAPIService(defaultNetwork, defaultNodes, nil)
	res, e := service.ConstructionCombine(nil, exampleInvalidSigningPayloadConstructionCombineRequest)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrInvalidSignatureVerification, e)
}

func TestConstructionCombineThrowsWithInvalidTransactionType(t *testing.T) {
	// given:
	exampleInvalidTransactionTypeConstructionCombineRequest := dummyConstructionCombineRequest()
	exampleInvalidTransactionTypeConstructionCombineRequest.UnsignedTransaction = invalidTypeTransaction

	// when:
	service, _ := NewConstructionAPIService(defaultNetwork, defaultNodes, nil)
	res, e := service.ConstructionCombine(nil, exampleInvalidTransactionTypeConstructionCombineRequest)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrTransactionInvalidType, e)
}

func TestConstructionDerive(t *testing.T) {
	// given
	service, _ := NewConstructionAPIService(defaultNetwork, defaultNodes, nil)

	// when:
	res, e := service.ConstructionDerive(nil, nil)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrNotImplemented, e)
}

func TestConstructionHash(t *testing.T) {
	// given:
	expectedHash := "0xc371b00f25490004c01b7d50350aecacaf7569ca564955465aa2b48fafd68dda5f7f277465a5f1b862f438e630c30648"
	exampleConstructionHashRequest := dummyConstructionHashRequest(validSignedTransaction)
	expectedConstructHashResponse := &types.TransactionIdentifierResponse{
		TransactionIdentifier: &types.TransactionIdentifier{Hash: expectedHash},
	}

	// when:
	service, _ := NewConstructionAPIService(defaultNetwork, defaultNodes, nil)
	res, e := service.ConstructionHash(nil, exampleConstructionHashRequest)

	// then:
	assert.Equal(t, expectedConstructHashResponse, res)
	assert.Nil(t, e)
}

func TestConstructionHashThrowsWhenDecodeStringFails(t *testing.T) {
	// given:
	exampleConstructionHashRequest := dummyConstructionHashRequest(invalidTransaction)

	// when:
	service, _ := NewConstructionAPIService(defaultNetwork, defaultNodes, nil)
	res, e := service.ConstructionHash(nil, exampleConstructionHashRequest)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrTransactionDecodeFailed, e)
}

func TestConstructionMetadata(t *testing.T) {
	// given:
	expectedResponse := &types.ConstructionMetadataResponse{
		Metadata: make(map[string]interface{}),
	}

	// when:
	service, _ := NewConstructionAPIService(defaultNetwork, defaultNodes, nil)
	res, e := service.ConstructionMetadata(nil, nil)

	// then:
	assert.Equal(t, expectedResponse, res)
	assert.Nil(t, e)
}

func TestConstructionParse(t *testing.T) {
	var tests = []struct {
		name    string
		signed  bool
		signers []*types.AccountIdentifier
	}{
		{
			name:    "NotSigned",
			signers: []*types.AccountIdentifier{},
		},
		{
			name:    "Signed",
			signed:  true,
			signers: []*types.AccountIdentifier{{Address: defaultCryptoAccountId1}},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// given:
			request := dummyConstructionParseRequest(validSignedTransaction, tt.signed)
			operations := []*types.Operation{
				dummyOperation(0, "CRYPTOTRANSFER", defaultCryptoAccountId1, defaultSendAmount),
				dummyOperation(1, "CRYPTOTRANSFER", defaultCryptoAccountId2, defaultReceiveAmount),
			}
			expectedConstructionParseResponse := &types.ConstructionParseResponse{
				Operations:               operations,
				AccountIdentifierSigners: tt.signers,
			}
			mockConstructor := &mockTransactionConstructor{}
			mockConstructor.
				On("Parse", mock.IsType(&hedera.TransferTransaction{})).
				Return(operations, []hedera.AccountID{defaultAccountId1}, nilError)
			service, _ := NewConstructionAPIService(defaultNetwork, defaultNodes, mockConstructor)

			// when:
			res, e := service.ConstructionParse(nil, request)

			// then:
			assert.Equal(t, expectedConstructionParseResponse, res)
			assert.Nil(t, e)
			mockConstructor.AssertExpectations(t)
		})
	}
}

func TestConstructionParseThrowsWhenConstructorParseFails(t *testing.T) {
	// given
	mockConstructor := &mockTransactionConstructor{}
	mockConstructor.
		On("Parse", mock.IsType(&hedera.TransferTransaction{})).
		Return(nilOperations, nilSigners, errors.ErrInternalServerError)
	service, _ := NewConstructionAPIService(defaultNetwork, defaultNodes, mockConstructor)

	// when
	res, e := service.ConstructionParse(nil, dummyConstructionParseRequest(validSignedTransaction, false))

	// then
	assert.Nil(t, res)
	assert.NotNil(t, e)
	mockConstructor.AssertExpectations(t)
}

func TestConstructionParseThrowsWhenDecodeStringFails(t *testing.T) {
	// given
	mockConstructor := &mockTransactionConstructor{}
	service, _ := NewConstructionAPIService(defaultNetwork, defaultNodes, mockConstructor)

	// when
	res, e := service.ConstructionParse(nil, dummyConstructionParseRequest(invalidTransaction, false))

	// then
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrTransactionDecodeFailed, e)
}

func TestConstructionParseThrowsWhenUnmarshallFails(t *testing.T) {
	// given
	mockConstructor := &mockTransactionConstructor{}
	service, _ := NewConstructionAPIService(defaultNetwork, defaultNodes, mockConstructor)

	// when
	res, e := service.ConstructionParse(nil, dummyConstructionParseRequest(corruptedTransaction, false))

	// then
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrTransactionUnmarshallingFailed, e)
}

func TestConstructionPayloads(t *testing.T) {
	// given
	operations := []*types.Operation{
		dummyOperation(0, "CRYPTOTRANSFER", defaultCryptoAccountId1, defaultSendAmount),
		dummyOperation(1, "CRYPTOTRANSFER", defaultCryptoAccountId2, defaultReceiveAmount),
	}
	payloadBytes, _ := hex.DecodeString("0a120a0a08bca0fa850610c0c407120418d8c307120218071880c2d72f2202087872020a00")
	expected := &types.ConstructionPayloadsResponse{
		UnsignedTransaction: "0x0a2b2a290a250a120a0a08bca0fa850610c0c407120418d8c307120218071880c2d72f2202087872020a001200",
		Payloads: []*types.SigningPayload{
			{
				AccountIdentifier: &types.AccountIdentifier{Address: defaultCryptoAccountId1},
				Bytes:             payloadBytes,
				SignatureType:     types.Ed25519,
			},
		},
	}

	transactionId, _ := hedera.TransactionIdFromString(fmt.Sprintf("%s@1623101500.123456", defaultAccountId1))
	transaction, _ := hedera.NewTransferTransaction().
		SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
		SetTransactionID(transactionId).
		Freeze()
	mockConstructor := &mockTransactionConstructor{}
	mockConstructor.
		On("Construct", mock.IsType(hedera.AccountID{}), mock.IsType([]*types.Operation{})).
		Return(transaction, []hedera.AccountID{defaultAccountId1}, nilErr)
	service, _ := NewConstructionAPIService(defaultNetwork, defaultNodes, mockConstructor)

	// when
	actual, e := service.ConstructionPayloads(nil, dummyPayloadsRequest(operations))

	// then
	assert.Nil(t, e)
	assert.Equal(t, expected, actual)
}

func TestConstructionPayloadsThrowsWithConstuctorConstructFailure(t *testing.T) {
	// given
	operations := []*types.Operation{
		dummyOperation(0, "CRYPTOTRANSFER", defaultCryptoAccountId1, defaultSendAmount),
		dummyOperation(1, "CRYPTOTRANSFER", defaultCryptoAccountId2, defaultReceiveAmount),
	}
	mockConstructor := &mockTransactionConstructor{}
	mockConstructor.
		On("Construct", mock.IsType(hedera.AccountID{}), mock.IsType([]*types.Operation{})).
		Return(nilTransaction, nilSigners, errors.ErrInternalServerError)
	service, _ := NewConstructionAPIService(defaultNetwork, defaultNodes, mockConstructor)

	// when
	actual, err := service.ConstructionPayloads(nil, dummyPayloadsRequest(operations))

	// then
	assert.NotNil(t, err)
	assert.Nil(t, actual)
}

func TestConstructionSubmitThrowsWhenDecodeStringFails(t *testing.T) {
	// given:
	exampleConstructionSubmitRequest := &types.ConstructionSubmitRequest{
		NetworkIdentifier: networkIdentifier(),
		SignedTransaction: invalidTransaction,
	}

	// when:
	service, _ := NewConstructionAPIService(defaultNetwork, defaultNodes, nil)
	res, e := service.ConstructionSubmit(nil, exampleConstructionSubmitRequest)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrTransactionDecodeFailed, e)
}

func TestConstructionSubmitThrowsWhenUnmarshalBinaryFails(t *testing.T) {
	constructionSubmitSignedTransaction := "0xfc2267c53ef8a27e2ab65f0a6b5e5607ba33b9c8c8f7304d8cb4a77aee19107d"

	// given:
	exampleConstructionSubmitRequest := &types.ConstructionSubmitRequest{
		NetworkIdentifier: networkIdentifier(),
		SignedTransaction: constructionSubmitSignedTransaction,
	}

	// when:
	service, _ := NewConstructionAPIService(defaultNetwork, defaultNodes, nil)
	res, e := service.ConstructionSubmit(nil, exampleConstructionSubmitRequest)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrTransactionUnmarshallingFailed, e)
}

func TestConstructionPreprocess(t *testing.T) {
	// given:
	expected := &types.ConstructionPreprocessResponse{
		Options:            make(map[string]interface{}),
		RequiredPublicKeys: []*types.AccountIdentifier{{Address: defaultCryptoAccountId1}},
	}
	mockConstructor := &mockTransactionConstructor{}
	mockConstructor.
		On("Preprocess", mock.IsType([]*types.Operation{})).
		Return([]hedera.AccountID{defaultAccountId1}, nilErr)
	service, _ := NewConstructionAPIService(defaultNetwork, defaultNodes, mockConstructor)

	// when:
	actual, e := service.ConstructionPreprocess(nil, dummyConstructionPreprocessRequest(true))

	// then:
	assert.Equal(t, expected, actual)
	assert.Nil(t, e)
}

func TestConstructionPreprocessThrowsWithConstructorPreprocessFailure(t *testing.T) {
	// given:
	mockConstructor := &mockTransactionConstructor{}
	mockConstructor.
		On("Preprocess", mock.IsType([]*types.Operation{})).
		Return(nilSigners, errors.ErrInternalServerError)
	service, _ := NewConstructionAPIService(defaultNetwork, defaultNodes, mockConstructor)

	// when:
	actual, e := service.ConstructionPreprocess(nil, dummyConstructionPreprocessRequest(false))

	// then:
	assert.Nil(t, actual)
	assert.NotNil(t, e)
}

func freezeTransaction(transaction ITransaction) {
	nodeAccountIds := []hedera.AccountID{nodeAccountId}
	transactionId := hedera.TransactionIDGenerate(payerId)

	var err error
	switch tx := transaction.(type) {
	case *hedera.TokenAssociateTransaction:
		_, err = tx.SetNodeAccountIDs(nodeAccountIds).
			SetTransactionID(transactionId).
			Freeze()
	case *hedera.TokenBurnTransaction:
		_, err = tx.SetNodeAccountIDs(nodeAccountIds).
			SetTransactionID(transactionId).
			Freeze()
	case *hedera.TokenCreateTransaction:
		_, err = tx.SetNodeAccountIDs(nodeAccountIds).
			SetTransactionID(transactionId).
			Freeze()
	case *hedera.TokenDeleteTransaction:
		_, err = tx.SetNodeAccountIDs(nodeAccountIds).
			SetTransactionID(transactionId).
			Freeze()
	case *hedera.TokenDissociateTransaction:
		_, err = tx.SetNodeAccountIDs(nodeAccountIds).
			SetTransactionID(transactionId).
			Freeze()
	case *hedera.TokenFreezeTransaction:
		_, err = tx.SetNodeAccountIDs(nodeAccountIds).
			SetTransactionID(transactionId).
			Freeze()
	case *hedera.TokenGrantKycTransaction:
		_, err = tx.SetNodeAccountIDs(nodeAccountIds).
			SetTransactionID(transactionId).
			Freeze()
	case *hedera.TokenMintTransaction:
		_, err = tx.SetNodeAccountIDs(nodeAccountIds).
			SetTransactionID(transactionId).
			Freeze()
	case *hedera.TokenRevokeKycTransaction:
		_, err = tx.SetNodeAccountIDs(nodeAccountIds).
			SetTransactionID(transactionId).
			Freeze()
	case *hedera.TokenUnfreezeTransaction:
		_, err = tx.SetNodeAccountIDs(nodeAccountIds).
			SetTransactionID(transactionId).
			Unfreeze() // SDK typo
	case *hedera.TokenUpdateTransaction:
		_, err = tx.SetNodeAccountIDs(nodeAccountIds).
			SetTransactionID(transactionId).
			Freeze()
	case *hedera.TokenWipeTransaction:
		_, err = tx.SetNodeAccountIDs(nodeAccountIds).
			SetTransactionID(transactionId).
			Freeze()
	case *hedera.TransferTransaction:
		_, err = tx.SetNodeAccountIDs(nodeAccountIds).
			SetTransactionID(transactionId).
			Freeze()
	case *hedera.TopicCreateTransaction:
		_, err = tx.SetNodeAccountIDs(nodeAccountIds).
			SetTransactionID(transactionId).
			Freeze() // only to test addSignature for unsupported transaction types
	default:
		panic("unsupported transaction type")
	}

	if err != nil {
		panic("failed to freeze transaction")
	}
}

func TestAddSignature(t *testing.T) {
	signature := []byte{0x1, 0x2, 0x3, 0x4, 0x5}
	signatureMap := map[*hedera.PublicKey][]byte{
		&adminKey: signature,
	}

	tests := []struct {
		transaction ITransaction
		expectError bool
	}{
		{transaction: hedera.NewTokenAssociateTransaction()},
		{transaction: hedera.NewTokenBurnTransaction()},
		{transaction: hedera.NewTokenCreateTransaction()},
		{transaction: hedera.NewTokenDeleteTransaction()},
		{transaction: hedera.NewTokenDissociateTransaction()},
		{transaction: hedera.NewTokenFreezeTransaction()},
		{transaction: hedera.NewTokenGrantKycTransaction()},
		{transaction: hedera.NewTokenMintTransaction()},
		{transaction: hedera.NewTokenRevokeKycTransaction()},
		{transaction: hedera.NewTokenUnfreezeTransaction()},
		{transaction: hedera.NewTokenUpdateTransaction()},
		{transaction: hedera.NewTokenWipeTransaction()},
		{transaction: hedera.NewTransferTransaction()},
		{transaction: hedera.NewTopicCreateTransaction(), expectError: true},
	}

	for _, tt := range tests {
		name := reflect.TypeOf(tt.transaction).Elem().String()
		t.Run(name, func(t *testing.T) {
			// given
			freezeTransaction(tt.transaction)

			// when
			err := addSignature(tt.transaction, adminKey, signature)

			// then
			if tt.expectError {
				assert.NotNil(t, err)
			} else {
				assert.Nil(t, err)
				assertSignatureMap(t, tt.transaction, nodeAccountId, signatureMap)
			}
		})
	}
}

func TestAddSignatureMultipleSignature(t *testing.T) {
	// given
	signature1 := []byte{0x1, 0x2, 0x3}
	signature2 := []byte{0x4, 0x5, 0x6}
	signatureMap := map[*hedera.PublicKey][]byte{
		&adminKey:  signature1,
		&freezeKey: signature2,
	}
	tx, _ := hedera.NewTransferTransaction().
		SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
		SetTransactionID(hedera.TransactionIDGenerate(payerId)).
		Freeze()

	// when
	err1 := addSignature(tx, adminKey, signature1)
	err2 := addSignature(tx, freezeKey, signature2)

	// then
	assert.Nil(t, err1)
	assert.Nil(t, err2)
	assertSignatureMap(t, tx, nodeAccountId, signatureMap)
}

func TestGetFrozenTransactionBodyBytes(t *testing.T) {
	// given
	transactionId, _ := hedera.TransactionIdFromString(fmt.Sprintf("%s@1623101500.123456", defaultAccountId1))
	transaction, _ := hedera.NewTransferTransaction().
		SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
		SetTransactionID(transactionId).
		Freeze()
	expected := []byte{0xa, 0x12, 0xa, 0xa, 0x8, 0xbc, 0xa0, 0xfa, 0x85, 0x6, 0x10, 0xc0, 0xc4, 0x7, 0x12, 0x4,
		0x18, 0xd8, 0xc3, 0x7, 0x12, 0x2, 0x18, 0x7, 0x18, 0x80, 0xc2, 0xd7, 0x2f, 0x22, 0x2, 0x8,
		0x78, 0x72, 0x2, 0xa, 0x0}

	// when
	bytes, err := getFrozenTransactionBodyBytes(transaction)

	// then
	assert.Nil(t, err)
	assert.Equal(t, expected, bytes)
}

func TestUnmarshallTransactionFromHexString(t *testing.T) {
	for _, signed := range []bool{false, true} {
		transactions := []ITransaction{
			hedera.NewTokenAssociateTransaction(),
			hedera.NewTokenBurnTransaction(),
			hedera.NewTokenCreateTransaction(),
			hedera.NewTokenDeleteTransaction(),
			hedera.NewTokenDissociateTransaction(),
			hedera.NewTokenFreezeTransaction(),
			hedera.NewTokenGrantKycTransaction(),
			hedera.NewTokenMintTransaction(),
			hedera.NewTokenRevokeKycTransaction(),
			hedera.NewTokenUnfreezeTransaction(),
			hedera.NewTokenUpdateTransaction(),
			hedera.NewTokenWipeTransaction(),
			hedera.NewTransferTransaction(),
		}

		for _, transaction := range transactions {
			suffix := "Unsigned"
			if signed {
				suffix = "Signed"
			}
			name := fmt.Sprintf("%s%s", reflect.TypeOf(transaction).Elem().String(), suffix)

			t.Run(name, func(t *testing.T) {
				// given
				txStr := createTransactionHexString(transaction, signed)

				// when
				actual, err := unmarshallTransactionFromHexString(txStr)

				// then
				assert.Nil(t, err)
				assert.IsType(t, transaction, actual)
			})
		}
	}
}

func TestUnmarshallTransactionFromHexStringThrowsWithInvalidHexString(t *testing.T) {
	// when
	actual, err := unmarshallTransactionFromHexString("not a hex string")

	// then
	assert.NotNil(t, err)
	assert.Nil(t, actual)
}

func TestUnmarshallTransactionFromHexStringThrowsWithInvalidTransactionBytes(t *testing.T) {
	// when
	actual, err := unmarshallTransactionFromHexString("0xdeadbeaf")

	// then
	assert.NotNil(t, err)
	assert.Nil(t, actual)
}

func TestUnmarshallTransactionFromHexStringThrowsWithUnsupportedTransactionType(t *testing.T) {
	// given
	tx, _ := hedera.NewTopicCreateTransaction().
		SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
		SetTransactionID(hedera.TransactionIDGenerate(payerId)).
		Freeze()
	bytes, _ := tx.ToBytes()
	txStr := hexutils.SafeAddHexPrefix(hex.EncodeToString(bytes))

	// when
	actual, err := unmarshallTransactionFromHexString(txStr)

	// then
	assert.NotNil(t, err)
	assert.Nil(t, actual)
}

func assertSignatureMap(
	t *testing.T,
	tx ITransaction,
	nodeAccountId hedera.AccountID,
	expected map[*hedera.PublicKey][]byte,
) {
	signatures, err := tx.GetSignatures()
	assert.NoError(t, err)

	assert.Len(t, signatures, 1)
	assert.Contains(t, signatures, nodeAccountId)

	actual := signatures[nodeAccountId]
	convertedActual := convertSignatureMap(actual)
	convertedExpected := convertSignatureMap(expected)

	assert.Equal(t, convertedExpected, convertedActual)
}

func convertSignatureMap(signatureMap map[*hedera.PublicKey][]byte) map[string][]byte {
	converted := make(map[string][]byte)
	for pubKey, signature := range signatureMap {
		// hedera.PublicKey is not comparable so have to use its string representation
		converted[pubKey.String()] = signature
	}

	return converted
}

func createTransactionHexString(transaction ITransaction, signed bool) string {
	nodeAccountIds := []hedera.AccountID{nodeAccountId}
	switch tx := transaction.(type) {
	case *hedera.TokenAssociateTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(hedera.TransactionIDGenerate(payerId)).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TokenBurnTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(hedera.TransactionIDGenerate(payerId)).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TokenCreateTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(hedera.TransactionIDGenerate(payerId)).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TokenDeleteTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(hedera.TransactionIDGenerate(payerId)).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TokenDissociateTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(hedera.TransactionIDGenerate(payerId)).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TokenFreezeTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(hedera.TransactionIDGenerate(payerId)).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TokenGrantKycTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(hedera.TransactionIDGenerate(payerId)).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TokenMintTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(hedera.TransactionIDGenerate(payerId)).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TokenRevokeKycTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(hedera.TransactionIDGenerate(payerId)).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TokenUnfreezeTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(hedera.TransactionIDGenerate(payerId)).Unfreeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TokenUpdateTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(hedera.TransactionIDGenerate(payerId)).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TokenWipeTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(hedera.TransactionIDGenerate(payerId)).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TransferTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(hedera.TransactionIDGenerate(payerId)).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	default:
		panic("unsupported transaction type")
	}

	bytes, _ := transaction.ToBytes()
	return hexutils.SafeAddHexPrefix(hex.EncodeToString(bytes))
}
