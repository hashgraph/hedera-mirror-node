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
	"math/big"
	"testing"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	types2 "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/types"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
)

const (
	DefaultCryptoAccountId1 = "0.0.123352"
	DefaultCryptoAccountId2 = "0.0.123518"
	DefaultSendAmount       = "-1000"
	DefaultReceiveAmount    = "1000"
	DefaultNetwork          = "testnet"
)

var (
	defaultNodes = types2.NodeMap{
		"10.0.0.1:50211": hedera.AccountID{Account: 3},
		"10.0.0.2:50211": hedera.AccountID{Account: 4},
		"10.0.0.3:50211": hedera.AccountID{Account: 5},
		"10.0.0.4:50211": hedera.AccountID{Account: 6},
	}
	validSignedTransaction   = "0x0aaa012aa7010a3d0a140a0c08feafcb840610ae86c0db03120418d8c307120218041880c2d72f2202087872180a160a090a0418d8c30710cf0f0a090a0418fec40710d00f12660a640a20eba8cc093a83a4ca5e813e30d8c503babb35c22d57d34b6ec5ac0303a6aaba771a40793de745bc19dd8fe8e817891f51b8fe1e259c2e6428bd7fa075b181585a2d40e3666a7c9a1873abb5433ffe1414502836d8d37082eaf94a648b530e9fa78108"
	validUnsignedTransaction = "0x0a432a410a3d0a140a0c08feafcb840610ae86c0db03120418d8c307120218041880c2d72f2202087872180a160a090a0418d8c30710cf0f0a090a0418fec40710d00f1200"
	invalidTransaction       = "InvalidTxHexString"
	//
	invalidTypeTransaction = "0x0a332a310a2d0a140a0c08a6e4cb840610f6a3aeef0112041882810c12021805188084af5f22020878c20107320508d0c8e1031200"
	corruptedTransaction   = "0x6767"
	publicKey              = "eba8cc093a83a4ca5e813e30d8c503babb35c22d57d34b6ec5ac0303a6aaba77" // without ed25519PubKeyPrefix
)

func dummyConstructionCombineRequest() *types.ConstructionCombineRequest {
	unsignedTransaction := "0x0a432a410a3d0a140a0c08feafcb840610ae86c0db03120418d8c307120218041880c2d72f2202087872180a160a090a0418d8c30710cf0f0a090a0418fec40710d00f1200"
	signingPayloadBytes := "967f26876ad492cc27b4c384dc962f443bcc9be33cbb7add3844bc864de047340e7a78c0fbaf40ab10948dc570bbc25edb505f112d0926dffb65c93199e6d507"
	signatureBytes := "793de745bc19dd8fe8e817891f51b8fe1e259c2e6428bd7fa075b181585a2d40e3666a7c9a1873abb5433ffe1414502836d8d37082eaf94a648b530e9fa78108"

	return dummyConstructionCombineRequestWith(
		unsignedTransaction,
		signingPayloadBytes,
		publicKey,
		signatureBytes,
	)
}

func dummyConstructionPreprocessRequest(valid bool) *types.ConstructionPreprocessRequest {
	operations := []*types.Operation{
		dummyOperation(0, "CRYPTOTRANSFER", DefaultCryptoAccountId1, DefaultSendAmount),
		dummyOperation(1, "CRYPTOTRANSFER", DefaultCryptoAccountId2, DefaultReceiveAmount),
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
						Address:  DefaultCryptoAccountId1,
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
			actual, err := NewConstructionAPIService(tt.network, tt.nodes)

			if tt.wantErr {
				assert.Error(t, err)
			} else {
				assert.NoError(t, err)
				assert.IsType(t, &ConstructionAPIService{}, actual)

				service := actual.(*ConstructionAPIService)
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

	// when:
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
	res, e := service.ConstructionCombine(nil, dummyConstructionCombineRequest())

	// then:
	assert.Equal(t, expectedConstructionCombineResponse, res)
	assert.Nil(t, e)
}

func TestConstructionCombineThrowsWithMultipleSignatures(t *testing.T) {
	// given:
	exampleConstructionCombineRequest := dummyConstructionCombineRequest()
	exampleConstructionCombineRequest.Signatures = []*types.Signature{
		{}, {},
	}

	// when:
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
	res, e := service.ConstructionCombine(nil, exampleConstructionCombineRequest)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrMultipleSignaturesPresent, e)
}

func TestConstructionCombineThrowsWhenDecodeStringFails(t *testing.T) {
	// given:
	exampleCorruptedTxHexStrConstructionCombineRequest := dummyConstructionCombineRequest()
	exampleCorruptedTxHexStrConstructionCombineRequest.UnsignedTransaction = invalidTransaction

	// when:
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
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
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
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
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
	res, e := service.ConstructionCombine(nil, exampleInvalidPublicKeyConstructionCombineRequest)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrInvalidPublicKey, e)
}

func TestConstructionCombineThrowsWhenSignatureIsNotVerified(t *testing.T) {
	// given:
	exampleInvalidSigningPayloadConstructionCombineRequest := dummyConstructionCombineRequest()
	exampleInvalidSigningPayloadConstructionCombineRequest.Signatures[0].Bytes = []byte("bad signature")

	// when:
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
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
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
	res, e := service.ConstructionCombine(nil, exampleInvalidTransactionTypeConstructionCombineRequest)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrTransactionInvalidType, e)
}

func TestConstructionDerive(t *testing.T) {
	// when:
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
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
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
	res, e := service.ConstructionHash(nil, exampleConstructionHashRequest)

	// then:
	assert.Equal(t, expectedConstructHashResponse, res)
	assert.Nil(t, e)
}

func TestConstructionHashThrowsWhenDecodeStringFails(t *testing.T) {
	// given:
	exampleConstructionHashRequest := dummyConstructionHashRequest(invalidTransaction)

	// when:
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
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
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
	res, e := service.ConstructionMetadata(nil, nil)

	// then:
	assert.Equal(t, expectedResponse, res)
	assert.Nil(t, e)
}

func TestConstructionParse(t *testing.T) {
	// given:
	exampleConstructionParseRequest := dummyConstructionParseRequest(validUnsignedTransaction, false)
	expectedConstructionParseResponse := &types.ConstructionParseResponse{
		Operations: []*types.Operation{
			dummyOperation(0, "CRYPTOTRANSFER", DefaultCryptoAccountId1, DefaultSendAmount),
			dummyOperation(1, "CRYPTOTRANSFER", DefaultCryptoAccountId2, DefaultReceiveAmount),
		},
	}

	// when:
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
	res, e := service.ConstructionParse(nil, exampleConstructionParseRequest)

	// then:
	assert.Equal(t, expectedConstructionParseResponse, res)
	assert.Nil(t, e)
}

func TestConstructionParseSigned(t *testing.T) {
	// given:
	exampleConstructionParseRequest := dummyConstructionParseRequest(validSignedTransaction, true)
	expectedConstructionParseResponse := &types.ConstructionParseResponse{
		Operations: []*types.Operation{
			dummyOperation(0, "CRYPTOTRANSFER", DefaultCryptoAccountId1, DefaultSendAmount),
			dummyOperation(1, "CRYPTOTRANSFER", DefaultCryptoAccountId2, DefaultReceiveAmount),
		},
		AccountIdentifierSigners: []*types.AccountIdentifier{
			{
				Address: DefaultCryptoAccountId1,
			},
		},
	}

	// when:
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
	res, e := service.ConstructionParse(nil, exampleConstructionParseRequest)

	// then:
	assert.Equal(t, expectedConstructionParseResponse, res)
	assert.Nil(t, e)
}

func TestConstructionParseThrowsWhenDecodeStringFails(t *testing.T) {
	// when:
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
	res, e := service.ConstructionParse(nil, dummyConstructionParseRequest(invalidTransaction, false))

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrTransactionDecodeFailed, e)
}

func TestConstructionParseThrowsWhenUnmarshallFails(t *testing.T) {
	// when:
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
	res, e := service.ConstructionParse(nil, dummyConstructionParseRequest(corruptedTransaction, false))

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrTransactionUnmarshallingFailed, e)
}

func TestConstructionPayloads(t *testing.T) {
	// given:
	var expectedNilBytes []byte
	operations := []*types.Operation{
		dummyOperation(0, "CRYPTOTRANSFER", DefaultCryptoAccountId1, DefaultSendAmount),
		dummyOperation(1, "CRYPTOTRANSFER", DefaultCryptoAccountId2, DefaultReceiveAmount),
	}
	expectedPayloadsResponse := &types.ConstructionPayloadsResponse{
		UnsignedTransaction: "0x1a00223d0a140a0c08faf3b5fc0510c681d7d303120418e3cc13120218061880c2d72f2202087872180a160a090a0418b9c30710c8010a090a0418e3cc1310c701",
		Payloads: []*types.SigningPayload{
			{
				AccountIdentifier: &types.AccountIdentifier{
					Address: DefaultCryptoAccountId1,
				},
				Bytes:         expectedNilBytes,
				SignatureType: types.Ed25519,
			},
		},
	}

	// when:
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
	res, e := service.ConstructionPayloads(nil, dummyPayloadsRequest(operations))

	// then:
	// here we do not assert the whole response object to equal the expected one, because invocation of this method appends a unique timestamp to the result, thus making the signed TX and Bytes unique and non-assertable.
	assert.Len(t, expectedPayloadsResponse.Payloads, 1)
	assert.Equal(
		t,
		expectedPayloadsResponse.Payloads[0].AccountIdentifier.Address,
		res.Payloads[0].AccountIdentifier.Address,
	)
	assert.Nil(t, e)
}

func TestConstructionPayloadsThrowsWithInvalidOperationsSum(t *testing.T) {
	// given:
	operations := []*types.Operation{
		dummyOperation(0, "CRYPTOTRANSFER", "0.0.123321", "1000"),
	}

	// when:
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
	res, e := service.ConstructionPayloads(nil, dummyPayloadsRequest(operations))

	// then:
	assert.Nil(t, res)
	assert.NotNil(t, e)
}

func TestConstructionPayloadsThrowsWithEmptyOperations(t *testing.T) {
	// given:
	operations := []*types.Operation{}

	// when:
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
	res, e := service.ConstructionPayloads(nil, dummyPayloadsRequest(operations))

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrEmptyOperations, e)
}

func TestConstructionPayloadsThrowsWithInvalidOperationAmounts(t *testing.T) {
	// given:
	operations := []*types.Operation{
		dummyOperation(0, "CRYPTOTRANSFER", "0.0.123321", "0"),
	}

	// when:
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
	res, e := service.ConstructionPayloads(nil, dummyPayloadsRequest(operations))

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrInvalidAmount, e)
}

func TestConstructionPayloadsThrowsWhenInvalidAccount(t *testing.T) {
	// given:
	operations := []*types.Operation{
		dummyOperation(0, "CRYPTOTRANSFER", "23321", DefaultReceiveAmount),
		dummyOperation(1, "CRYPTOTRANSFER", "23321", DefaultSendAmount),
	}

	// when:
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
	res, e := service.ConstructionPayloads(nil, dummyPayloadsRequest(operations))

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrInvalidAccount, e)
}

func TestConstructionSubmitThrowsWhenDecodeStringFails(t *testing.T) {
	// given:
	exampleConstructionSubmitRequest := &types.ConstructionSubmitRequest{
		NetworkIdentifier: networkIdentifier(),
		SignedTransaction: invalidTransaction,
	}

	// when:
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
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
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
	res, e := service.ConstructionSubmit(nil, exampleConstructionSubmitRequest)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrTransactionUnmarshallingFailed, e)
}

func TestConstructionPreprocess(t *testing.T) {
	// given:
	expectedResult := &types.ConstructionPreprocessResponse{
		Options: make(map[string]interface{}),
		RequiredPublicKeys: []*types.AccountIdentifier{
			{
				Address: DefaultCryptoAccountId1,
			},
		},
	}

	// when:
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
	res, e := service.ConstructionPreprocess(nil, dummyConstructionPreprocessRequest(true))

	// then:
	assert.Equal(t, expectedResult, res)
	assert.Nil(t, e)
}

func TestConstructionPreprocessThrowsWithInvalidOperationsSum(t *testing.T) {
	// when:
	service, _ := NewConstructionAPIService(DefaultNetwork, defaultNodes)
	res, e := service.ConstructionPreprocess(nil, dummyConstructionPreprocessRequest(false))

	// then:
	assert.Nil(t, res)
	assert.NotNil(t, e)
}
