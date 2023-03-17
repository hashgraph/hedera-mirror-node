/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

package services

import (
	"encoding/hex"
	"fmt"
	"math/big"
	"reflect"
	"strconv"
	"testing"
	"time"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/config"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/tools"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

const (
	defaultSendAmount    = -1000
	defaultReceiveAmount = 1000
	defaultNetwork       = "testnet"
	ed25519AliasPrefix   = "0x1220"
)

var (
	adminKeyStr             = "302a300506032b6570032100d619a3a22d6bd2a9e4b08f3d999df757e5a9ef0364c13b4b3356bc065b34fa01"
	adminKey, _             = hedera.PublicKeyFromString(adminKeyStr)
	corruptedTransaction    = "0x6767"
	defaultCryptoAccountId1 = types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(123352))
	defaultCryptoAccountId2 = types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(123518))
	defaultCryptoAccountId3 = types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(123532))
	defaultNodes            = config.NodeMap{
		"10.0.0.1:50211": hedera.AccountID{Account: 3},
		"10.0.0.2:50211": hedera.AccountID{Account: 4},
		"10.0.0.3:50211": hedera.AccountID{Account: 5},
		"10.0.0.4:50211": hedera.AccountID{Account: 6},
	}
	singleNode                  = config.NodeMap{"10.0.0.1:50211": hedera.AccountID{Account: 3}}
	freezeKeyStr                = "302a300506032b65700321006663a95da28adcb0fc129d1b4eda4be7dd90b54a337cd2dd953e1d2dc03ca6d1"
	freezeKey, _                = hedera.PublicKeyFromString(freezeKeyStr)
	invalidTransaction          = "InvalidTxHexString"
	invalidTypeTransaction      = "0x0a332a310a2d0a140a0c08a6e4cb840610f6a3aeef0112041882810c12021805188084af5f22020878c20107320508d0c8e1031200"
	nodeAccountId               = hedera.AccountID{Account: 7}
	offlineBaseService          = NewOfflineBaseService()
	onlineBaseService           = NewOnlineBaseService(&mocks.MockBlockRepository{}, &mocks.MockTransactionRepository{})
	payerId                     = hedera.AccountID{Account: 100}
	publicKeyStr                = "eba8cc093a83a4ca5e813e30d8c503babb35c22d57d34b6ec5ac0303a6aaba77" // without ed25519PubKeyPrefix
	privateKey, _               = hedera.PrivateKeyFromString("302e020100300506032b6570042204207904b9687878e08e101723f7b724cd61a42bbff93923177bf3fcc2240b0dd3bc")
	aliasStr                    = ed25519AliasPrefix + publicKeyStr
	aliasAccount, _             = types.NewAccountIdFromString(aliasStr, 0, 0)
	unsignedTransactionWithMemo = "0x0a332a310a2d0a0f0a0708959aef3a107b120418d8c307120218031880c2d72f220308b40132087472616e7366657272020a001200"
	validSignedTransaction      = "0x0aaa012aa7010a3d0a140a0c08feafcb840610ae86c0db03120418d8c307120218041880c2d72f2202087872180a160a090a0418d8c30710cf0f0a090a0418fec40710d00f12660a640a20eba8cc093a83a4ca5e813e30d8c503babb35c22d57d34b6ec5ac0303a6aaba771a40793de745bc19dd8fe8e817891f51b8fe1e259c2e6428bd7fa075b181585a2d40e3666a7c9a1873abb5433ffe1414502836d8d37082eaf94a648b530e9fa78108"
)

func getConstructionCombineRequest() *rTypes.ConstructionCombineRequest {
	unsignedTransaction := "0x0a432a410a3d0a140a0c08feafcb840610ae86c0db03120418d8c307120218041880c2d72f2202087872180a160a090a0418d8c30710cf0f0a090a0418fec40710d00f1200"
	signingPayloadBytes := "967f26876ad492cc27b4c384dc962f443bcc9be33cbb7add3844bc864de047340e7a78c0fbaf40ab10948dc570bbc25edb505f112d0926dffb65c93199e6d507"
	signatureBytes := "793de745bc19dd8fe8e817891f51b8fe1e259c2e6428bd7fa075b181585a2d40e3666a7c9a1873abb5433ffe1414502836d8d37082eaf94a648b530e9fa78108"

	return getConstructionCombineRequestWith(
		unsignedTransaction,
		signingPayloadBytes,
		publicKeyStr,
		signatureBytes,
	)
}

func getConstructionPreprocessRequest(valid bool, metadata map[string]interface{}) *rTypes.ConstructionPreprocessRequest {
	operations := types.OperationSlice{
		getOperation(0, types.OperationTypeCryptoTransfer, defaultCryptoAccountId1, defaultSendAmount),
		getOperation(1, types.OperationTypeCryptoTransfer, defaultCryptoAccountId2, defaultReceiveAmount),
	}
	if !valid {
		operations = append(
			operations,
			getOperation(3, types.OperationTypeCryptoTransfer, defaultCryptoAccountId3, -5000),
		)
	}

	return &rTypes.ConstructionPreprocessRequest{
		Metadata:          metadata,
		NetworkIdentifier: networkIdentifier(),
		Operations:        operations.ToRosetta(),
	}
}

func getConstructionCombineRequestWith(
	unsignedTransaction string,
	signingPayload string,
	publicKey string,
	signature string,
) *rTypes.ConstructionCombineRequest {
	signingPayloadBytes, e1 := hex.DecodeString(signingPayload)
	publicKeyBytes, e2 := hex.DecodeString(publicKey)
	signatureBytes, e3 := hex.DecodeString(signature)

	if e1 != nil || e2 != nil || e3 != nil {
		return nil
	}

	return &rTypes.ConstructionCombineRequest{
		NetworkIdentifier:   networkIdentifier(),
		UnsignedTransaction: unsignedTransaction,
		Signatures: []*rTypes.Signature{
			{
				SigningPayload: &rTypes.SigningPayload{
					AccountIdentifier: defaultCryptoAccountId1.ToRosetta(),
					Bytes:             signingPayloadBytes,
					SignatureType:     rTypes.Ed25519,
				},
				PublicKey: &rTypes.PublicKey{
					Bytes:     publicKeyBytes,
					CurveType: rTypes.Edwards25519,
				},
				SignatureType: rTypes.Ed25519,
				Bytes:         signatureBytes,
			},
		},
	}
}

func getOperation(index int64, operationType string, account types.AccountId, amount int64) types.Operation {
	return types.Operation{
		AccountId: account,
		Amount:    &types.HbarAmount{Value: amount},
		Index:     index,
		Type:      operationType,
	}
}

func networkIdentifier() *rTypes.NetworkIdentifier {
	return &rTypes.NetworkIdentifier{
		Blockchain: "SomeBlockchain",
		Network:    "SomeNetwork",
		SubNetworkIdentifier: &rTypes.SubNetworkIdentifier{
			Network: "SomeSubNetwork",
		},
	}
}

func getConstructionHashRequest(signedTx string) *rTypes.ConstructionHashRequest {
	return &rTypes.ConstructionHashRequest{
		NetworkIdentifier: networkIdentifier(),
		SignedTransaction: signedTx,
	}
}

func getConstructionParseRequest(transaction string, signed bool) *rTypes.ConstructionParseRequest {
	return &rTypes.ConstructionParseRequest{
		NetworkIdentifier: networkIdentifier(),
		Signed:            signed,
		Transaction:       transaction,
	}
}

func getPayloadsRequest(
	operations types.OperationSlice,
	customizers ...func(payloadsRequest *rTypes.ConstructionPayloadsRequest),
) *rTypes.ConstructionPayloadsRequest {
	request := &rTypes.ConstructionPayloadsRequest{Operations: operations.ToRosetta()}
	for _, customize := range customizers {
		customize(request)
	}
	return request
}

func payloadsRequestMetadata(metadata map[string]interface{}) func(*rTypes.ConstructionPayloadsRequest) {
	return func(request *rTypes.ConstructionPayloadsRequest) {
		request.Metadata = metadata
	}
}

func payloadsRequestOperationAccountIdentifier(address string) func(*rTypes.ConstructionPayloadsRequest) {
	return func(request *rTypes.ConstructionPayloadsRequest) {
		for _, operation := range request.Operations {
			operation.Account.Address = address
		}
	}
}

func payloadsRequestOperationAmount(amount *rTypes.Amount) func(*rTypes.ConstructionPayloadsRequest) {
	return func(request *rTypes.ConstructionPayloadsRequest) {
		for _, operation := range request.Operations {
			operation.Amount = amount
		}
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
		nodes                 config.NodeMap
		expectedHederaNetwork map[string]hedera.AccountID
		wantErr               bool
	}{
		{
			name:                  "Testnet",
			network:               "testnet",
			nodes:                 config.NodeMap{},
			expectedHederaNetwork: getHederaNetworkByName("testnet"),
			wantErr:               false,
		},
		{
			name:                  "Previewnet",
			network:               "previewnet",
			nodes:                 config.NodeMap{},
			expectedHederaNetwork: getHederaNetworkByName("previewnet"),
			wantErr:               false,
		},
		{
			name:                  "Mainnet",
			network:               "mainnet",
			nodes:                 config.NodeMap{},
			expectedHederaNetwork: getHederaNetworkByName("mainnet"),
			wantErr:               false,
		},
		{
			name:                  "Demo",
			network:               "demo",
			nodes:                 config.NodeMap{},
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
			nodes:                 config.NodeMap{},
			expectedHederaNetwork: nil,
			wantErr:               true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			actual, err := NewConstructionAPIService(
				&mocks.MockAccountRepository{},
				onlineBaseService,
				tt.network,
				tt.nodes,
				0,
				0,
				&mocks.MockTransactionConstructor{},
			)

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
	expectedConstructionCombineResponse := &rTypes.ConstructionCombineResponse{
		SignedTransaction: validSignedTransaction,
	}
	service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, defaultNodes, 0, 0, nil)

	// when:
	res, e := service.ConstructionCombine(nil, getConstructionCombineRequest())

	// then:
	assert.Equal(t, expectedConstructionCombineResponse, res)
	assert.Nil(t, e)
}

func TestConstructionCombineThrowsWithNoSignature(t *testing.T) {
	// given
	request := getConstructionCombineRequest()
	request.Signatures = []*rTypes.Signature{}
	service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, defaultNodes, 0, 0, nil)

	// when
	res, e := service.ConstructionCombine(nil, request)

	// then
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrNoSignature, e)
}

func TestConstructionCombineThrowsWithInvalidSignatureType(t *testing.T) {
	// given
	request := getConstructionCombineRequest()
	request.Signatures[0].SignatureType = rTypes.Schnorr1
	service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, defaultNodes, 0, 0, nil)

	// when
	res, e := service.ConstructionCombine(defaultContext, request)

	// then
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrInvalidSignatureType, e)
}

func TestConstructionCombineThrowsWhenDecodeStringFails(t *testing.T) {
	// given:
	request := getConstructionCombineRequest()
	request.UnsignedTransaction = invalidTransaction

	// when:
	service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, defaultNodes, 0, 0, nil)
	res, e := service.ConstructionCombine(defaultContext, request)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrTransactionDecodeFailed, e)
}

func TestConstructionCombineThrowsWhenUnmarshallFails(t *testing.T) {
	// given:
	request := getConstructionCombineRequest()
	request.UnsignedTransaction = corruptedTransaction

	// when:
	service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, defaultNodes, 0, 0, nil)
	res, e := service.ConstructionCombine(defaultContext, request)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrTransactionUnmarshallingFailed, e)
}

func TestConstructionCombineThrowsWithInvalidPublicKey(t *testing.T) {
	// given:
	request := getConstructionCombineRequest()
	request.Signatures[0].PublicKey = &rTypes.PublicKey{}

	// when:
	service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, defaultNodes, 0, 0, nil)
	res, e := service.ConstructionCombine(defaultContext, request)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrInvalidPublicKey, e)
}

func TestConstructionCombineThrowsWithInvalidSignature(t *testing.T) {
	// given:
	request := getConstructionCombineRequest()
	request.Signatures[0].Bytes = []byte("bad signature")

	// when:
	service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, defaultNodes, 0, 0, nil)
	res, e := service.ConstructionCombine(defaultContext, request)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrInvalidSignatureVerification, e)
}

func TestConstructionCombineThrowsWithInvalidTransactionType(t *testing.T) {
	// given:
	request := getConstructionCombineRequest()
	request.UnsignedTransaction = invalidTypeTransaction

	// when:
	service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, defaultNodes, 0, 0, nil)
	res, e := service.ConstructionCombine(defaultContext, request)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrTransactionInvalidType, e)
}

func TestConstructionDerive(t *testing.T) {
	ed25519PrivateKey, _ := hedera.PrivateKeyGenerateEd25519()
	ed25519PublicKey := ed25519PrivateKey.PublicKey()
	secp256k1PrivateKey, _ := hedera.PrivateKeyGenerateEcdsa()
	secp256k1PublicKey := secp256k1PrivateKey.PublicKey()

	tests := []struct {
		name      string
		publicKey rTypes.PublicKey
		expectErr bool
		expected  *rTypes.ConstructionDeriveResponse
	}{
		{
			name: string(rTypes.Edwards25519),
			publicKey: rTypes.PublicKey{
				Bytes:     ed25519PublicKey.BytesRaw(),
				CurveType: rTypes.Edwards25519,
			},
			expected: &rTypes.ConstructionDeriveResponse{
				AccountIdentifier: &rTypes.AccountIdentifier{Address: ed25519AliasPrefix + hex.EncodeToString(ed25519PublicKey.BytesRaw())},
			},
		},
		{
			name: string(rTypes.Secp256k1),
			publicKey: rTypes.PublicKey{
				Bytes:     secp256k1PublicKey.BytesRaw(),
				CurveType: rTypes.Secp256k1,
			},
			expectErr: true,
		},
		{
			name: "CurveTypeKeyMismatch",
			publicKey: rTypes.PublicKey{
				Bytes:     secp256k1PublicKey.BytesRaw(),
				CurveType: rTypes.Edwards25519,
			},
			expectErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// given
			service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, defaultNodes, 0, 0, nil)
			request := &rTypes.ConstructionDeriveRequest{
				NetworkIdentifier: networkIdentifier(),
				PublicKey:         &tt.publicKey,
			}

			// when
			resp, err := service.ConstructionDerive(defaultContext, request)

			// then
			if !tt.expectErr {
				assert.Nil(t, err)
				assert.Equal(t, tt.expected, resp)
			} else {
				assert.NotNil(t, err)
				assert.Nil(t, resp)
			}
		})
	}
}

func TestConstructionHash(t *testing.T) {
	// given:
	expectedHash := "0xc371b00f25490004c01b7d50350aecacaf7569ca564955465aa2b48fafd68dda5f7f277465a5f1b862f438e630c30648"
	request := getConstructionHashRequest(validSignedTransaction)
	expected := &rTypes.TransactionIdentifierResponse{
		TransactionIdentifier: &rTypes.TransactionIdentifier{Hash: expectedHash},
	}

	// when:
	service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, defaultNodes, 0, 0, nil)
	res, e := service.ConstructionHash(defaultContext, request)

	// then:
	assert.Equal(t, expected, res)
	assert.Nil(t, e)
}

func TestConstructionHashThrowsWhenDecodeStringFails(t *testing.T) {
	// given:
	request := getConstructionHashRequest(invalidTransaction)

	// when:
	service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, defaultNodes, 0, 0, nil)
	res, e := service.ConstructionHash(defaultContext, request)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrTransactionDecodeFailed, e)
}

func TestConstructionMetadataOnline(t *testing.T) {
	// given
	accountId := types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(100))
	mockAccountRepo := &mocks.MockAccountRepository{}
	mockAccountRepo.
		On("GetAccountId", defaultContext, mock.MatchedBy(func(accountId types.AccountId) bool {
			return accountId.String() == aliasStr
		})).
		Return(accountId, mocks.NilError)
	mockTransactionConstructor := &mocks.MockTransactionConstructor{}
	mockTransactionConstructor.
		On("GetDefaultMaxTransactionFee", types.OperationTypeCryptoTransfer).
		Return(types.HbarAmount{Value: 100}, mocks.NilError)
	request := &rTypes.ConstructionMetadataRequest{
		NetworkIdentifier: networkIdentifier(),
		Options: map[string]interface{}{
			types.MetadataKeyMemo:   "tx memo",
			optionKeyAccountAliases: aliasStr,
			optionKeyOperationType:  types.OperationTypeCryptoTransfer,
		},
	}
	expectedResponse := &rTypes.ConstructionMetadataResponse{
		Metadata: map[string]interface{}{
			types.MetadataKeyMemo:  "tx memo",
			metadataKeyAccountMap:  fmt.Sprintf("%s:%s", aliasStr, accountId),
			optionKeyOperationType: types.OperationTypeCryptoTransfer,
		},
		SuggestedFee: []*rTypes.Amount{{Value: "100", Currency: types.CurrencyHbar}},
	}

	// when
	service, _ := NewConstructionAPIService(
		mockAccountRepo,
		onlineBaseService,
		defaultNetwork,
		defaultNodes,
		0,
		0,
		mockTransactionConstructor,
	)
	res, e := service.ConstructionMetadata(defaultContext, request)

	// then
	mockAccountRepo.AssertExpectations(t)
	mockTransactionConstructor.AssertExpectations(t)

	assert.IsType(t, "", res.Metadata[metadataKeyValidUntilNanos])
	validUntilNanos, _ := strconv.ParseInt(res.Metadata[metadataKeyValidUntilNanos].(string), 10, 64)
	assert.InDelta(t, validUntilNanos, time.Now().Add(maxValidDurationNanos).UnixNano(), 3_000_000_000)
	delete(res.Metadata, metadataKeyValidUntilNanos)

	assert.Equal(t, expectedResponse, res)
	assert.Nil(t, e)
}

func TestConstructionMetadataOffline(t *testing.T) {
	// given
	mockTransactionConstructor := &mocks.MockTransactionConstructor{}
	mockTransactionConstructor.
		On("GetDefaultMaxTransactionFee", types.OperationTypeCryptoTransfer).
		Return(types.HbarAmount{Value: 100}, mocks.NilError)
	request := &rTypes.ConstructionMetadataRequest{
		NetworkIdentifier: networkIdentifier(),
		Options:           map[string]interface{}{optionKeyOperationType: types.OperationTypeCryptoTransfer},
	}
	expectedResponse := &rTypes.ConstructionMetadataResponse{
		Metadata:     map[string]interface{}{optionKeyOperationType: types.OperationTypeCryptoTransfer},
		SuggestedFee: []*rTypes.Amount{{Value: "100", Currency: types.CurrencyHbar}},
	}

	// when
	service, _ := NewConstructionAPIService(
		nil,
		offlineBaseService,
		defaultNetwork,
		defaultNodes,
		0,
		0,
		mockTransactionConstructor,
	)
	res, e := service.ConstructionMetadata(defaultContext, request)

	// then
	mockTransactionConstructor.AssertExpectations(t)
	assert.Equal(t, expectedResponse, res)
	assert.Nil(t, e)
}

func TestConstructionMetadataOfflineAccountAliasesFail(t *testing.T) {
	// given
	mockTransactionConstructor := &mocks.MockTransactionConstructor{}
	mockTransactionConstructor.
		On("GetDefaultMaxTransactionFee", types.OperationTypeCryptoTransfer).
		Return(types.HbarAmount{Value: 100}, mocks.NilError)
	request := &rTypes.ConstructionMetadataRequest{
		NetworkIdentifier: networkIdentifier(),
		Options: map[string]interface{}{
			optionKeyAccountAliases: aliasStr,
			optionKeyOperationType:  types.OperationTypeCryptoTransfer,
		},
	}

	// when
	service, _ := NewConstructionAPIService(
		nil,
		offlineBaseService,
		defaultNetwork,
		defaultNodes,
		0,
		0,
		mockTransactionConstructor,
	)
	response, err := service.ConstructionMetadata(defaultContext, request)

	// then
	mockTransactionConstructor.AssertExpectations(t)
	assert.Nil(t, response)
	assert.NotNil(t, err)
}

func TestConstructionMetadataFailsWhenInvalidOptions(t *testing.T) {
	tests := []struct {
		name    string
		request *rTypes.ConstructionMetadataRequest
	}{
		{
			name:    "nil options",
			request: &rTypes.ConstructionMetadataRequest{NetworkIdentifier: networkIdentifier(), Options: nil},
		},
		{
			name: "empty options",
			request: &rTypes.ConstructionMetadataRequest{
				NetworkIdentifier: networkIdentifier(),
				Options:           map[string]interface{}{},
			},
		},
		{
			name: "incorrect operation type value type",
			request: &rTypes.ConstructionMetadataRequest{
				NetworkIdentifier: networkIdentifier(),
				Options: map[string]interface{}{
					optionKeyOperationType: 1,
				},
			},
		},
		{
			name: "incorrect account aliases",
			request: &rTypes.ConstructionMetadataRequest{
				NetworkIdentifier: networkIdentifier(),
				Options: map[string]interface{}{
					optionKeyAccountAliases: "foobar",
					optionKeyOperationType:  types.OperationTypeCryptoTransfer,
				},
			},
		},
		{
			name: "incorrect account aliases value type",
			request: &rTypes.ConstructionMetadataRequest{
				NetworkIdentifier: networkIdentifier(),
				Options: map[string]interface{}{
					optionKeyAccountAliases: 1,
					optionKeyOperationType:  types.OperationTypeCryptoTransfer,
				},
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			mockAccountRepo := &mocks.MockAccountRepository{}
			mockTransactionConstructor := &mocks.MockTransactionConstructor{}
			mockTransactionConstructor.
				On("GetDefaultMaxTransactionFee", types.OperationTypeCryptoTransfer).
				Return(types.HbarAmount{Value: 100}, mocks.NilError)

			// when
			service, _ := NewConstructionAPIService(
				mockAccountRepo,
				onlineBaseService,
				defaultNetwork,
				defaultNodes,
				0,
				0,
				mockTransactionConstructor,
			)
			res, e := service.ConstructionMetadata(defaultContext, tt.request)

			// then
			mockAccountRepo.AssertExpectations(t)
			assert.Nil(t, res)
			assert.NotNil(t, e)
		})
	}
}

func TestConstructionMetadataFailsWhenAccountRepoFails(t *testing.T) {
	// given
	mockAccountRepo := &mocks.MockAccountRepository{}
	mockAccountRepo.
		On("GetAccountId", defaultContext, mock.IsType(types.AccountId{})).
		Return(types.AccountId{}, errors.ErrInvalidAccount)
	mockTransactionConstructor := &mocks.MockTransactionConstructor{}
	mockTransactionConstructor.
		On("GetDefaultMaxTransactionFee", types.OperationTypeCryptoTransfer).
		Return(types.HbarAmount{Value: 100}, mocks.NilError)
	request := &rTypes.ConstructionMetadataRequest{
		NetworkIdentifier: networkIdentifier(),
		Options: map[string]interface{}{
			optionKeyAccountAliases: aliasStr,
			optionKeyOperationType:  types.OperationTypeCryptoTransfer,
		},
	}
	service, _ := NewConstructionAPIService(
		mockAccountRepo,
		onlineBaseService,
		defaultNetwork,
		defaultNodes,
		0,
		0,
		mockTransactionConstructor,
	)

	// when
	response, err := service.ConstructionMetadata(defaultContext, request)

	// then
	mockAccountRepo.AssertExpectations(t)
	mockTransactionConstructor.AssertExpectations(t)
	assert.Nil(t, response)
	assert.NotNil(t, err)
}

func TestConstructionMetadataFailsWhenTransactionConstructorFails(t *testing.T) {
	// given
	mockAccountRepo := &mocks.MockAccountRepository{}
	mockTransactionConstructor := &mocks.MockTransactionConstructor{}
	mockTransactionConstructor.
		On("GetDefaultMaxTransactionFee", types.OperationTypeCryptoTransfer).
		Return(types.HbarAmount{}, errors.ErrInvalidOperationType)
	request := &rTypes.ConstructionMetadataRequest{
		NetworkIdentifier: networkIdentifier(),
		Options:           map[string]interface{}{optionKeyOperationType: types.OperationTypeCryptoTransfer},
	}
	service, _ := NewConstructionAPIService(
		mockAccountRepo,
		onlineBaseService,
		defaultNetwork,
		defaultNodes,
		0,
		0,
		mockTransactionConstructor,
	)

	// when
	response, err := service.ConstructionMetadata(defaultContext, request)

	// then
	mockTransactionConstructor.AssertExpectations(t)
	assert.Nil(t, response)
	assert.NotNil(t, err)
}

func TestConstructionParse(t *testing.T) {
	var tests = []struct {
		name     string
		metadata map[string]interface{}
		request  *rTypes.ConstructionParseRequest
		signers  []*rTypes.AccountIdentifier
	}{
		{
			name:     "NotSigned",
			metadata: map[string]interface{}{},
			request:  getConstructionParseRequest(validSignedTransaction, false),
			signers:  []*rTypes.AccountIdentifier{},
		},
		{
			name:     "Signed",
			metadata: map[string]interface{}{},
			request:  getConstructionParseRequest(validSignedTransaction, true),
			signers:  []*rTypes.AccountIdentifier{defaultCryptoAccountId1.ToRosetta()},
		},
		{
			name:     "memo",
			metadata: map[string]interface{}{"memo": "transfer"},
			request:  getConstructionParseRequest(unsignedTransactionWithMemo, false),
			signers:  []*rTypes.AccountIdentifier{},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// given:
			operations := types.OperationSlice{
				getOperation(0, types.OperationTypeCryptoTransfer, defaultCryptoAccountId1, defaultSendAmount),
				getOperation(1, types.OperationTypeCryptoTransfer, defaultCryptoAccountId2, defaultReceiveAmount),
			}
			expected := &rTypes.ConstructionParseResponse{
				Operations:               operations.ToRosetta(),
				AccountIdentifierSigners: tt.signers,
				Metadata:                 tt.metadata,
			}
			mockConstructor := &mocks.MockTransactionConstructor{}
			mockConstructor.
				On("Parse", defaultContext, mock.IsType(&hedera.TransferTransaction{})).
				Return(operations, []types.AccountId{defaultCryptoAccountId1}, mocks.NilError)
			service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, defaultNodes, 0, 0,
				mockConstructor)

			// when:
			actual, e := service.ConstructionParse(defaultContext, tt.request)

			// then:
			assert.Equal(t, expected, actual)
			assert.Nil(t, e)
			mockConstructor.AssertExpectations(t)
		})
	}
}

func TestConstructionParseThrowsWhenConstructorParseFails(t *testing.T) {
	// given
	mockConstructor := &mocks.MockTransactionConstructor{}
	mockConstructor.
		On("Parse", defaultContext, mock.IsType(&hedera.TransferTransaction{})).
		Return(mocks.NilOperations, mocks.NilSigners, errors.ErrInternalServerError)
	service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, defaultNodes, 0, 0, mockConstructor)

	// when
	res, e := service.ConstructionParse(defaultContext, getConstructionParseRequest(validSignedTransaction, false))

	// then
	assert.Nil(t, res)
	assert.NotNil(t, e)
	mockConstructor.AssertExpectations(t)
}

func TestConstructionParseThrowsWhenDecodeStringFails(t *testing.T) {
	// given
	mockConstructor := &mocks.MockTransactionConstructor{}
	service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, defaultNodes, 0, 0, mockConstructor)

	// when
	res, e := service.ConstructionParse(defaultContext, getConstructionParseRequest(invalidTransaction, false))

	// then
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrTransactionDecodeFailed, e)
}

func TestConstructionParseThrowsWhenUnmarshallFails(t *testing.T) {
	// given
	mockConstructor := &mocks.MockTransactionConstructor{}
	service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, defaultNodes, 0, 0, mockConstructor)

	// when
	res, e := service.ConstructionParse(defaultContext, getConstructionParseRequest(corruptedTransaction, false))

	// then
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrTransactionUnmarshallingFailed, e)
}

func TestConstructionPayloads(t *testing.T) {
	tests := []struct {
		name           string
		metadata       map[string]interface{}
		payerAccountId types.AccountId
		expected       *rTypes.ConstructionPayloadsResponse
	}{
		{
			name:           "shard.realm.num payer",
			metadata:       map[string]interface{}{metadataKeyValidStartNanos: "123456789000000123"},
			payerAccountId: defaultCryptoAccountId1,
			expected: &rTypes.ConstructionPayloadsResponse{
				UnsignedTransaction: "0x0a292a270a230a0f0a0708959aef3a107b120418d8c307120218031880c2d72f220308b40172020a001200",
				Payloads: []*rTypes.SigningPayload{
					{
						AccountIdentifier: defaultCryptoAccountId1.ToRosetta(),
						Bytes:             hexutil.MustDecode("0x0a0f0a0708959aef3a107b120418d8c307120218031880c2d72f220308b40172020a00"),
						SignatureType:     rTypes.Ed25519,
					},
				},
			},
		},
		{
			name: "alias account payer",
			metadata: map[string]interface{}{
				metadataKeyAccountMap:      fmt.Sprintf("%s:0.0.100", aliasStr),
				metadataKeyValidStartNanos: "123456789000000123",
			},
			payerAccountId: aliasAccount,
			expected: &rTypes.ConstructionPayloadsResponse{
				UnsignedTransaction: "0x0a272a250a210a0d0a0708959aef3a107b12021864120218031880c2d72f220308b40172020a001200",
				Payloads: []*rTypes.SigningPayload{
					{
						AccountIdentifier: aliasAccount.ToRosetta(),
						Bytes:             hexutil.MustDecode("0x0a0d0a0708959aef3a107b12021864120218031880c2d72f220308b40172020a00"),
						SignatureType:     rTypes.Ed25519,
					},
				},
			},
		},
		{
			name: "transaction memo",
			metadata: map[string]interface{}{
				"memo":                     "transfer",
				metadataKeyValidStartNanos: "123456789000000123",
			},
			payerAccountId: defaultCryptoAccountId1,
			expected: &rTypes.ConstructionPayloadsResponse{
				UnsignedTransaction: "0x0a332a310a2d0a0f0a0708959aef3a107b120418d8c307120218031880c2d72f220308b40132087472616e7366657272020a001200",
				Payloads: []*rTypes.SigningPayload{
					{
						AccountIdentifier: defaultCryptoAccountId1.ToRosetta(),
						Bytes:             hexutil.MustDecode("0x0a0f0a0708959aef3a107b120418d8c307120218031880c2d72f220308b40132087472616e7366657272020a00"),
						SignatureType:     rTypes.Ed25519,
					},
				},
			},
		},
		{
			name: "valid until",
			metadata: map[string]interface{}{
				// valid start nanos and valid duration are ignored
				metadataKeyValidDurationSeconds: "100",
				metadataKeyValidStartNanos:      "123499999000000123",
				metadataKeyValidUntilNanos:      "123456609000000123",
			},
			payerAccountId: defaultCryptoAccountId1,
			expected: &rTypes.ConstructionPayloadsResponse{
				UnsignedTransaction: "0x0a292a270a230a0f0a0708ad97ef3a107b120418d8c307120218031880c2d72f220308b40172020a001200",
				Payloads: []*rTypes.SigningPayload{
					{
						AccountIdentifier: defaultCryptoAccountId1.ToRosetta(),
						Bytes:             hexutil.MustDecode("0x0a0f0a0708ad97ef3a107b120418d8c307120218031880c2d72f220308b40172020a00"),
						SignatureType:     rTypes.Ed25519,
					},
				},
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// given
			operations := types.OperationSlice{
				getOperation(0, types.OperationTypeCryptoTransfer, tt.payerAccountId, defaultSendAmount),
				getOperation(1, types.OperationTypeCryptoTransfer, defaultCryptoAccountId2, defaultReceiveAmount),
			}

			mockConstructor := &mocks.MockTransactionConstructor{}
			mockConstructor.
				On("Construct", defaultContext, mock.IsType(types.OperationSlice{})).
				Return(hedera.NewTransferTransaction(), []types.AccountId{tt.payerAccountId}, mocks.NilError)
			request := getPayloadsRequest(operations, payloadsRequestMetadata(tt.metadata))
			service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, singleNode, 0, 0, mockConstructor)

			// when
			actual, err := service.ConstructionPayloads(defaultContext, request)

			// then
			mockConstructor.AssertExpectations(t)
			assert.Nil(t, err)
			assert.Equal(t, tt.expected, actual)
		})
	}
}

func TestConstructionPayloadValidDuration(t *testing.T) {
	// given
	operations := types.OperationSlice{
		getOperation(0, types.OperationTypeCryptoTransfer, defaultCryptoAccountId1, defaultSendAmount),
		getOperation(1, types.OperationTypeCryptoTransfer, defaultCryptoAccountId2, defaultReceiveAmount),
	}
	payloadBytes, _ := hex.DecodeString("0a0f0a0708959aef3a107b120418d8c307120218031880c2d72f2202083c72020a00")
	expected := &rTypes.ConstructionPayloadsResponse{
		UnsignedTransaction: "0x0a282a260a220a0f0a0708959aef3a107b120418d8c307120218031880c2d72f2202083c72020a001200",
		Payloads: []*rTypes.SigningPayload{
			{
				AccountIdentifier: defaultCryptoAccountId1.ToRosetta(),
				Bytes:             payloadBytes,
				SignatureType:     rTypes.Ed25519,
			},
		},
	}

	mockConstructor := &mocks.MockTransactionConstructor{}
	mockConstructor.
		On("Construct", defaultContext, mock.IsType(types.OperationSlice{})).
		Return(hedera.NewTransferTransaction(), []types.AccountId{defaultCryptoAccountId1}, mocks.NilError)
	metadata := map[string]interface{}{
		metadataKeyValidStartNanos:      "123456789000000123",
		metadataKeyValidDurationSeconds: "60",
	}
	request := getPayloadsRequest(operations, payloadsRequestMetadata(metadata))
	service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, singleNode, 0, 0, mockConstructor)

	// when
	actual, e := service.ConstructionPayloads(defaultContext, request)

	// then
	mockConstructor.AssertExpectations(t)
	assert.Nil(t, e)
	assert.Equal(t, expected, actual)
}

func TestConstructionPayloadsAliasError(t *testing.T) {
	tests := []struct {
		name     string
		metadata map[string]interface{}
	}{
		{
			name:     "no account_map metadata",
			metadata: map[string]interface{}{},
		},
		{
			name:     "no matching alias in metadata",
			metadata: map[string]interface{}{metadataKeyAccountMap: "foobar:0.0.100"},
		},
		{
			name:     "invalid metadata value type",
			metadata: map[string]interface{}{metadataKeyAccountMap: 10},
		},
		{
			name:     "invalid account id",
			metadata: map[string]interface{}{metadataKeyAccountMap: fmt.Sprintf("%s:abc", aliasStr)},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// given
			operations := types.OperationSlice{
				getOperation(0, types.OperationTypeCryptoTransfer, aliasAccount, defaultSendAmount),
				getOperation(1, types.OperationTypeCryptoTransfer, defaultCryptoAccountId2, defaultReceiveAmount),
			}

			mockConstructor := &mocks.MockTransactionConstructor{}
			mockConstructor.
				On("Construct", defaultContext, mock.IsType(types.OperationSlice{})).
				Return(hedera.NewTransferTransaction(), []types.AccountId{aliasAccount}, mocks.NilError)
			request := getPayloadsRequest(operations, payloadsRequestMetadata(tt.metadata))
			service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, singleNode, 0, 0, mockConstructor)

			// when
			actual, err := service.ConstructionPayloads(defaultContext, request)

			// then
			mockConstructor.AssertExpectations(t)
			assert.NotNil(t, err)
			assert.Nil(t, actual)
		})
	}
}

func TestConstructionPayloadsInvalidRequest(t *testing.T) {
	operations := types.OperationSlice{
		getOperation(0, types.OperationTypeCryptoTransfer, defaultCryptoAccountId1, defaultSendAmount),
		getOperation(1, types.OperationTypeCryptoTransfer, defaultCryptoAccountId2, defaultReceiveAmount),
	}

	tests := []struct {
		name      string
		customize func(*rTypes.ConstructionPayloadsRequest)
	}{
		{
			name:      "ValidDurationOverMax",
			customize: payloadsRequestMetadata(map[string]interface{}{metadataKeyValidDurationSeconds: "181"}),
		},
		{
			name:      "ValidDurationUnderMin",
			customize: payloadsRequestMetadata(map[string]interface{}{metadataKeyValidDurationSeconds: "-1"}),
		},
		{
			name:      "InvalidValidDurationType",
			customize: payloadsRequestMetadata(map[string]interface{}{metadataKeyValidDurationSeconds: 120}),
		},
		{
			name:      "ValidDurationTypeNotANumber",
			customize: payloadsRequestMetadata(map[string]interface{}{metadataKeyValidDurationSeconds: "abc"}),
		},
		{
			name:      "InvalidValidStartNanosType",
			customize: payloadsRequestMetadata(map[string]interface{}{metadataKeyValidStartNanos: 100}),
		},
		{
			name:      "NegativeValidSTartNanos",
			customize: payloadsRequestMetadata(map[string]interface{}{metadataKeyValidStartNanos: "-100"}),
		},
		{
			name:      "ValidStartNanosNotNumber",
			customize: payloadsRequestMetadata(map[string]interface{}{metadataKeyValidStartNanos: "abc"}),
		},
		{
			name:      "ValidUntilNegative",
			customize: payloadsRequestMetadata(map[string]interface{}{metadataKeyValidUntilNanos: "-100"}),
		},
		{
			name:      "ValidUntilCauseZeroValidStartNanos",
			customize: payloadsRequestMetadata(map[string]interface{}{metadataKeyValidUntilNanos: "180000000000"}),
		},
		{
			name:      "ValidUntilCauseNegativeValidStartNanos",
			customize: payloadsRequestMetadata(map[string]interface{}{metadataKeyValidUntilNanos: "179999999999"}),
		},
		{
			name:      "ValidUntilNotNumber",
			customize: payloadsRequestMetadata(map[string]interface{}{metadataKeyValidUntilNanos: "abc"}),
		},
		{
			name:      "InvalidOperationAccountIdentifier",
			customize: payloadsRequestOperationAccountIdentifier("-100"),
		},
		{
			name: "InvalidAmountCurrencySymbol",
			customize: payloadsRequestOperationAmount(&rTypes.Amount{
				Value:    "100",
				Currency: &rTypes.Currency{Symbol: "-100"},
			}),
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// given
			request := getPayloadsRequest(operations, tt.customize)
			service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, defaultNodes, 0, 0,
				&mocks.MockTransactionConstructor{})

			// when
			response, err := service.ConstructionPayloads(defaultContext, request)

			// then
			assert.NotNil(t, err)
			assert.Nil(t, response)
		})
	}
}

func TestConstructionPayloadsThrowsWithConstructorConstructFailure(t *testing.T) {
	// given
	operations := types.OperationSlice{
		getOperation(0, types.OperationTypeCryptoTransfer, defaultCryptoAccountId1, defaultSendAmount),
		getOperation(1, types.OperationTypeCryptoTransfer, defaultCryptoAccountId2, defaultReceiveAmount),
	}
	mockConstructor := &mocks.MockTransactionConstructor{}
	mockConstructor.
		On(
			"Construct",
			defaultContext,
			mock.IsType(types.OperationSlice{}),
		).
		Return(mocks.NilHederaTransaction, mocks.NilSigners, errors.ErrInternalServerError)
	service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, defaultNodes, 0, 0, mockConstructor)

	// when
	actual, err := service.ConstructionPayloads(defaultContext, getPayloadsRequest(operations))

	// then
	assert.NotNil(t, err)
	assert.Nil(t, actual)
}

func TestConstructionSubmitThrowsWhenDecodeStringFails(t *testing.T) {
	// given:
	request := &rTypes.ConstructionSubmitRequest{
		NetworkIdentifier: networkIdentifier(),
		SignedTransaction: invalidTransaction,
	}

	// when:
	service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, defaultNodes, 0, 0, nil)
	res, e := service.ConstructionSubmit(defaultContext, request)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrTransactionDecodeFailed, e)
}

func TestConstructionSubmitThrowsWhenUnmarshalBinaryFails(t *testing.T) {
	constructionSubmitSignedTransaction := "0xfc2267c53ef8a27e2ab65f0a6b5e5607ba33b9c8c8f7304d8cb4a77aee19107d"

	// given:
	request := &rTypes.ConstructionSubmitRequest{
		NetworkIdentifier: networkIdentifier(),
		SignedTransaction: constructionSubmitSignedTransaction,
	}

	// when:
	service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, defaultNodes, 0, 0, nil)
	res, e := service.ConstructionSubmit(defaultContext, request)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.ErrTransactionUnmarshallingFailed, e)
}

func TestConstructionSubmitOffline(t *testing.T) {
	// given
	request := &rTypes.ConstructionSubmitRequest{
		NetworkIdentifier: networkIdentifier(),
		SignedTransaction: "0xfc2267c53ef8a27e2ab65f0a6b5e5607ba33b9c8c8f7304d8cb4a77aee19107d",
	}

	service, _ := NewConstructionAPIService(nil, offlineBaseService, defaultNetwork, defaultNodes, 0, 0, nil)

	// when
	res, e := service.ConstructionSubmit(defaultContext, request)

	// then
	assert.Equal(t, errors.ErrEndpointNotSupportedInOfflineMode, e)
	assert.Nil(t, res)
}

func TestConstructionPreprocess(t *testing.T) {
	tests := []struct {
		name     string
		metadata map[string]interface{}
		signers  []types.AccountId
		expected *rTypes.ConstructionPreprocessResponse
	}{
		{
			name:    "shard.realm.num signer",
			signers: []types.AccountId{defaultCryptoAccountId1},
			expected: &rTypes.ConstructionPreprocessResponse{
				Options:            map[string]interface{}{optionKeyOperationType: types.OperationTypeCryptoTransfer},
				RequiredPublicKeys: []*rTypes.AccountIdentifier{defaultCryptoAccountId1.ToRosetta()},
			},
		},
		{
			name:     "alias account signer",
			metadata: map[string]interface{}{"memo": "tx memo"},
			signers:  []types.AccountId{aliasAccount},
			expected: &rTypes.ConstructionPreprocessResponse{
				Options: map[string]interface{}{
					"memo":                  "tx memo",
					optionKeyAccountAliases: aliasStr,
					optionKeyOperationType:  types.OperationTypeCryptoTransfer,
				},
				RequiredPublicKeys: []*rTypes.AccountIdentifier{aliasAccount.ToRosetta()},
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// given:
			mockConstructor := &mocks.MockTransactionConstructor{}
			mockConstructor.
				On("Preprocess", defaultContext, mock.IsType(types.OperationSlice{})).
				Return(tt.signers, mocks.NilError)
			service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, defaultNodes, 0, 0, mockConstructor)

			// when:
			actual, err := service.ConstructionPreprocess(defaultContext, getConstructionPreprocessRequest(true, tt.metadata))

			// then:
			assert.Equal(t, tt.expected, actual)
			assert.Nil(t, err)
		})
	}
}

func TestConstructionPreprocessThrowsWithConstructorPreprocessFailure(t *testing.T) {
	// given:
	mockConstructor := &mocks.MockTransactionConstructor{}
	mockConstructor.
		On("Preprocess", defaultContext, mock.IsType(types.OperationSlice{})).
		Return(mocks.NilSigners, errors.ErrInternalServerError)
	service, _ := NewConstructionAPIService(nil, onlineBaseService, defaultNetwork, defaultNodes, 0, 0, mockConstructor)

	// when:
	actual, e := service.ConstructionPreprocess(defaultContext, getConstructionPreprocessRequest(false, nil))

	// then:
	assert.Nil(t, actual)
	assert.NotNil(t, e)
}

func freezeTransaction(transaction interfaces.Transaction) {
	nodeAccountIds := []hedera.AccountID{nodeAccountId}
	transactionId := hedera.TransactionIDGenerate(payerId)

	var err error
	switch tx := transaction.(type) {
	case *hedera.AccountCreateTransaction:
		_, err = tx.SetNodeAccountIDs(nodeAccountIds).
			SetTransactionID(transactionId).
			Freeze()
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
			Freeze()
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
		transaction interfaces.Transaction
		expectError bool
	}{
		{transaction: hedera.NewAccountCreateTransaction()},
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
	transactionId, _ := hedera.TransactionIdFromString(fmt.Sprintf("%s@1623101500.123456", defaultCryptoAccountId1))
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
		transactions := []interfaces.Transaction{
			hedera.NewAccountCreateTransaction(),
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
	txStr := tools.SafeAddHexPrefix(hex.EncodeToString(bytes))

	// when
	actual, err := unmarshallTransactionFromHexString(txStr)

	// then
	assert.NotNil(t, err)
	assert.Nil(t, actual)
}

func TestTransactionSetMemo(t *testing.T) {
	tests := []struct {
		name     string
		memo     interface{}
		expected string
	}{
		{
			name:     "empty",
			memo:     "",
			expected: "",
		},
		{
			name:     "nil",
			memo:     nil,
			expected: "",
		},
		{
			name:     "transfer",
			memo:     "transfer",
			expected: "transfer",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// given
			transaction := hedera.NewTransferTransaction()
			update := transactionSetMemo(tt.memo)

			// when
			err := update(transaction)

			// then
			assert.Nil(t, err)
			assert.Equal(t, tt.expected, transaction.GetTransactionMemo())
		})
	}
}

func TestTransactionSetMemoFailure(t *testing.T) {
	// given
	transaction := hedera.NewTransferTransaction()
	update := transactionSetMemo(100)

	// when
	err := update(transaction)

	// then
	assert.NotNil(t, err)
}

func TestTransactionSetTransactionId(t *testing.T) {
	// given
	payer := hedera.AccountID{Account: 100}
	validStartNs := int64(123456789111222333)
	transaction := hedera.NewTransferTransaction()

	// when
	update := transactionSetTransactionId(payer, validStartNs)
	update(transaction)

	// then
	transactionId := transaction.GetTransactionID()
	assert.Equal(t, payer.String(), transactionId.AccountID.String())
	assert.False(t, transactionId.GetScheduled())
	assert.Equal(t, validStartNs, transactionId.ValidStart.UnixNano())
}

func TestTransactionSetTransactionIdRandomValidStartNs(t *testing.T) {
	// given
	payer := hedera.AccountID{Account: 100}
	transaction := hedera.NewTransferTransaction()

	// when
	update := transactionSetTransactionId(payer, 0)
	update(transaction)

	// then
	transactionId := transaction.GetTransactionID()
	assert.Equal(t, payer.String(), transactionId.AccountID.String())
	assert.False(t, transactionId.GetScheduled())
	assert.NotEqual(t, 0, transactionId.ValidStart.UnixNano())
}

func TestTransactionFreeze(t *testing.T) {
	// setup
	transactions := []interfaces.Transaction{
		hedera.NewAccountCreateTransaction(),
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
	setNodeAccountId := transactionSetNodeAccountId(hedera.AccountID{Account: 3})
	setTransactionId := transactionSetTransactionId(hedera.AccountID{Account: 100}, 0)
	for _, transaction := range transactions {
		setNodeAccountId(transaction)
		setTransactionId(transaction)
	}

	for _, transaction := range transactions {
		name := fmt.Sprintf("%s", reflect.TypeOf(transaction).Elem().String())
		t.Run(name, func(t *testing.T) {
			// when
			err := transactionFreeze(transaction)

			// then
			assert.Nil(t, err)
			assert.True(t, transaction.IsFrozen())
		})
	}
}

func TestTransactionFreezeFailed(t *testing.T) {
	transaction := hedera.NewTransferTransaction()
	err := transactionFreeze(transaction)
	assert.NotNil(t, err)
}

func TestTransactionFreezeUnsupportedTransaction(t *testing.T) {
	transaction := hedera.NewTopicCreateTransaction()
	err := transactionFreeze(transaction)
	assert.NotNil(t, err)
}

func assertSignatureMap(
	t *testing.T,
	tx interfaces.Transaction,
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

func createTransactionHexString(transaction interfaces.Transaction, signed bool) string {
	nodeAccountIds := []hedera.AccountID{nodeAccountId}
	transactionId := hedera.TransactionIDGenerate(payerId)
	switch tx := transaction.(type) {
	case *hedera.AccountCreateTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(transactionId).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TokenAssociateTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(transactionId).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TokenBurnTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(transactionId).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TokenCreateTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(transactionId).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TokenDeleteTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(transactionId).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TokenDissociateTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(transactionId).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TokenFreezeTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(transactionId).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TokenGrantKycTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(transactionId).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TokenMintTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(transactionId).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TokenRevokeKycTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(transactionId).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TokenUnfreezeTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(transactionId).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TokenUpdateTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(transactionId).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TokenWipeTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(transactionId).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	case *hedera.TransferTransaction:
		tx.SetNodeAccountIDs(nodeAccountIds).SetTransactionID(transactionId).Freeze()
		if signed {
			tx.Sign(privateKey)
		}
	default:
		panic("unsupported transaction type")
	}

	bytes, _ := transaction.ToBytes()
	return tools.SafeAddHexPrefix(hex.EncodeToString(bytes))
}
