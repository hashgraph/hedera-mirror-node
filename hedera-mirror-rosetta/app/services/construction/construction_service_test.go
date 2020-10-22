/*-
 * ‌
 * Hedera Mirror Node
 *
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
 * ‍
 */

package construction

import (
	"encoding/hex"
	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/stretchr/testify/assert"
	"testing"
)

var (
	validUnsignedTxHash = "0x1a00223d0a140a0c0891d0fef905109688f3a701120418d8c307120218061880c2d72f2202087872180a160a090a0418d8c30710cf0f0a090a0418fec40710d00f"
	validSignedTxHash   = "0x1a660a640a20d25025bad248dbd4c6ca704eefba7ab4f3e3f48089fa5f20e4e1d10303f97ade1a40967f26876ad492cc27b4c384dc962f443bcc9be33cbb7add3844bc864de047340e7a78c0fbaf40ab10948dc570bbc25edb505f112d0926dffb65c93199e6d507223c0a130a0b08c7af94fa0510f7d9fc76120418d8c307120218041880c2d72f2202087872180a160a090a0418d8c30710cf0f0a090a0418fec40710d00f"
	invalidTxHash       = "InvalidTxHash"
	unmarshableTxHash   = "0x6767"
	publicKeyBytes      = "d25025bad248dbd4c6ca704eefba7ab4f3e3f48089fa5f20e4e1d10303f97ade"
)

func dummyConstructionCombineRequest() *types.ConstructionCombineRequest {
	unsignedTxHash := "0x1a00223c0a130a0b08c7af94fa0510f7d9fc76120418d8c307120218041880c2d72f2202087872180a160a090a0418d8c30710cf0f0a090a0418fec40710d00f"
	signingPayloadBytes := "967f26876ad492cc27b4c384dc962f443bcc9be33cbb7add3844bc864de047340e7a78c0fbaf40ab10948dc570bbc25edb505f112d0926dffb65c93199e6d507"
	signatureBytes := "0a130a0b08c7af94fa0510f7d9fc76120418d8c307120218041880c2d72f2202087872180a160a090a0418d8c30710cf0f0a090a0418fec40710d00f"

	return dummyConstructionCombineRequestWith(
		unsignedTxHash,
		signingPayloadBytes,
		publicKeyBytes,
		signatureBytes,
	)
}

func dummyConstructionPreprocessRequest(valid bool) *types.ConstructionPreprocessRequest {
	operations := []*types.Operation{
		dummyOperation(0, "CRYPTOTRANSFER", "0.0.123352", "-1000"),
		dummyOperation(1, "CRYPTOTRANSFER", "0.0.123518", "1000"),
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

func dummyConstructionCombineRequestWith(unsignedTxHash, signingPayloadBytes, publicKeyBytes, signatureBytes string) *types.ConstructionCombineRequest {
	decodedSigningPayloadBytes, e1 := hex.DecodeString(signingPayloadBytes)
	decodedPublicKeyBytes, e2 := hex.DecodeString(publicKeyBytes)
	decodedSignatureBytes, e3 := hex.DecodeString(signatureBytes)

	if e1 != nil || e2 != nil || e3 != nil {
		return nil
	}

	return &types.ConstructionCombineRequest{
		NetworkIdentifier:   networkIdentifier(),
		UnsignedTransaction: unsignedTxHash,
		Signatures: []*types.Signature{
			{
				SigningPayload: &types.SigningPayload{
					AccountIdentifier: &types.AccountIdentifier{
						Address:  "0.0.123352",
						Metadata: nil,
					},
					Bytes:         decodedSigningPayloadBytes,
					SignatureType: "ed25519",
				},
				PublicKey: &types.PublicKey{
					Bytes:     decodedPublicKeyBytes,
					CurveType: "edwards25519",
				},
				SignatureType: "ed25519",
				Bytes:         decodedSignatureBytes,
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

func dummyConstructionHashRequest(txHash string) *types.ConstructionHashRequest {
	return &types.ConstructionHashRequest{
		NetworkIdentifier: networkIdentifier(),
		SignedTransaction: txHash,
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

func TestNewConstructionAPIService(t *testing.T) {
	assert.IsType(t, &ConstructionAPIService{}, NewConstructionAPIService())
}

func TestConstructionCombine(t *testing.T) {
	// given:
	expectedConstructionCombineResponse := &types.ConstructionCombineResponse{
		SignedTransaction: validSignedTxHash,
	}

	// when:
	res, e := NewConstructionAPIService().ConstructionCombine(nil, dummyConstructionCombineRequest())

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
	res, e := NewConstructionAPIService().ConstructionCombine(nil, exampleConstructionCombineRequest)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.Errors[errors.MultipleSignaturesPresent], e)
}

func TestConstructionCombineThrowsWhenDecodeStringFails(t *testing.T) {
	// given:
	exampleInvalidTxHashConstructionCombineRequest := dummyConstructionCombineRequest()
	exampleInvalidTxHashConstructionCombineRequest.UnsignedTransaction = invalidTxHash

	// when:
	res, e := NewConstructionAPIService().ConstructionCombine(nil, exampleInvalidTxHashConstructionCombineRequest)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.Errors[errors.TransactionDecodeFailed], e)
}

func TestConstructionCombineThrowsWhenUnmarshallFails(t *testing.T) {
	// given:
	exampleInvalidTxHashConstructionCombineRequest := dummyConstructionCombineRequest()
	exampleInvalidTxHashConstructionCombineRequest.UnsignedTransaction = unmarshableTxHash

	// when:
	res, e := NewConstructionAPIService().ConstructionCombine(nil, exampleInvalidTxHashConstructionCombineRequest)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.Errors[errors.TransactionUnmarshallingFailed], e)
}

func TestConstructionCombineThrowsWithInvalidPublicKey(t *testing.T) {
	// given:
	exampleInvalidPublicKeyConstructionCombineRequest := dummyConstructionCombineRequest()
	exampleInvalidPublicKeyConstructionCombineRequest.Signatures[0].PublicKey = &types.PublicKey{}

	// when:
	res, e := NewConstructionAPIService().ConstructionCombine(nil, exampleInvalidPublicKeyConstructionCombineRequest)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.Errors[errors.InvalidPublicKey], e)
}

func TestConstructionCombineThrowsWhenSignatureIsNotVerified(t *testing.T) {
	// given:
	exampleInvalidSigningPayloadConstructionCombineRequest := dummyConstructionCombineRequest()
	exampleInvalidSigningPayloadConstructionCombineRequest.Signatures[0].SigningPayload = &types.SigningPayload{}

	// when:
	res, e := NewConstructionAPIService().ConstructionCombine(nil, exampleInvalidSigningPayloadConstructionCombineRequest)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.Errors[errors.InvalidSignatureVerification], e)
}

func TestConstructionDerive(t *testing.T) {
	// when:
	res, e := NewConstructionAPIService().ConstructionDerive(nil, nil)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.Errors[errors.NotImplemented], e)
}

func TestConstructionHash(t *testing.T) {
	// given:
	txIdentifierHash := "0x9768d458c755befcda5c6fca07e9f7693b94c429f9c414b0cea07163c402ddd44d1108f89b190d0dcabc423a3d45696d"
	exampleConstructionHashRequest := dummyConstructionHashRequest(validSignedTxHash)
	expectedConstructHashResponse := &types.TransactionIdentifierResponse{
		TransactionIdentifier: &types.TransactionIdentifier{Hash: txIdentifierHash},
	}

	// when:
	res, e := NewConstructionAPIService().ConstructionHash(nil, exampleConstructionHashRequest)

	// then:
	assert.Equal(t, expectedConstructHashResponse, res)
	assert.Nil(t, e)
}

func TestConstructionHashThrowsWhenDecodeStringFails(t *testing.T) {
	// given:
	exampleConstructionHashRequest := dummyConstructionHashRequest(invalidTxHash)

	// when:
	res, e := NewConstructionAPIService().ConstructionHash(nil, exampleConstructionHashRequest)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.Errors[errors.TransactionDecodeFailed], e)
}

func TestConstructionMetadata(t *testing.T) {
	// given:
	expectedResponse := &types.ConstructionMetadataResponse{
		Metadata: make(map[string]interface{}),
	}

	// when:
	res, e := NewConstructionAPIService().ConstructionMetadata(nil, nil)

	// then:
	assert.Equal(t, expectedResponse, res)
	assert.Nil(t, e)
}

func TestConstructionParse(t *testing.T) {
	// given:
	exampleConstructionParseRequest := dummyConstructionParseRequest(validUnsignedTxHash, false)
	expectedConstructionParseResponse := &types.ConstructionParseResponse{
		Operations: []*types.Operation{
			dummyOperation(0, "CRYPTOTRANSFER", "0.0.123352", "-1000"),
			dummyOperation(1, "CRYPTOTRANSFER", "0.0.123518", "1000"),
		},
	}

	// when:
	res, e := NewConstructionAPIService().ConstructionParse(nil, exampleConstructionParseRequest)

	// then:
	assert.Equal(t, expectedConstructionParseResponse, res)
	assert.Nil(t, e)
}

func TestConstructionParseSigned(t *testing.T) {
	// given:
	exampleConstructionParseRequest := dummyConstructionParseRequest(validSignedTxHash, true)
	expectedConstructionParseResponse := &types.ConstructionParseResponse{
		Operations: []*types.Operation{
			dummyOperation(0, "CRYPTOTRANSFER", "0.0.123352", "-1000"),
			dummyOperation(1, "CRYPTOTRANSFER", "0.0.123518", "1000"),
		},
		AccountIdentifierSigners: []*types.AccountIdentifier{
			{
				Address: publicKeyBytes,
			},
		},
	}

	// when:
	res, e := NewConstructionAPIService().ConstructionParse(nil, exampleConstructionParseRequest)

	// then:
	assert.Equal(t, expectedConstructionParseResponse, res)
	assert.Nil(t, e)
}

func TestConstructionParseThrowsWhenDecodeStringFails(t *testing.T) {
	// when:
	res, e := NewConstructionAPIService().ConstructionParse(nil, dummyConstructionParseRequest(invalidTxHash, false))

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.Errors[errors.TransactionDecodeFailed], e)
}

func TestConstructionParseThrowsWhenUnmarshallFails(t *testing.T) {
	// when:
	res, e := NewConstructionAPIService().ConstructionParse(nil, dummyConstructionParseRequest(unmarshableTxHash, false))

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.Errors[errors.TransactionUnmarshallingFailed], e)
}

func TestConstructionPayloads(t *testing.T) {
	// given:
	expectedDecodedBytes, _ := hex.DecodeString(unmarshableTxHash)
	operations := []*types.Operation{
		dummyOperation(0, "CRYPTOTRANSFER", "0.0.123352", "-1000"),
		dummyOperation(1, "CRYPTOTRANSFER", "0.0.123518", "1000"),
	}
	expectedPayloadsResponse := &types.ConstructionPayloadsResponse{
		UnsignedTransaction: "0x1a00223d0a140a0c08faf3b5fc0510c681d7d303120418e3cc13120218061880c2d72f2202087872180a160a090a0418b9c30710c8010a090a0418e3cc1310c701",
		Payloads: []*types.SigningPayload{
			{
				AccountIdentifier: &types.AccountIdentifier{
					Address: "0.0.123352",
				},
				Bytes: expectedDecodedBytes,
			},
		},
	}

	// when:
	res, e := NewConstructionAPIService().ConstructionPayloads(nil, dummyPayloadsRequest(operations))

	// then:
	// here we do not assert the whole response object to equal the expected one, because invocation of this method appends a unique timestamp to the result, thus making the signed TX and Bytes unique and non-assertable.
	assert.Equal(t, expectedPayloadsResponse.Payloads[0].AccountIdentifier.Address, res.Payloads[0].AccountIdentifier.Address)
	assert.Nil(t, e)
}

func TestConstructionPayloadsThrowsWhenInvalidOperationsSum(t *testing.T) {
	// given:
	operations := []*types.Operation{
		dummyOperation(0, "CRYPTOTRANSFER", "0.0.123321", "1000"),
	}

	// when:
	res, e := NewConstructionAPIService().ConstructionPayloads(nil, dummyPayloadsRequest(operations))

	// then:
	assert.Nil(t, res)
	assert.IsType(t, &types.Error{}, e)
}

func TestConstructionPayloadsThrowsWhenInvalidAccount(t *testing.T) {
	// given:
	operations := []*types.Operation{
		dummyOperation(0, "CRYPTOTRANSFER", "23321", "1000"),
		dummyOperation(1, "CRYPTOTRANSFER", "23321", "-1000"),
	}

	// when:
	res, e := NewConstructionAPIService().ConstructionPayloads(nil, dummyPayloadsRequest(operations))

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.Errors[errors.InvalidAccount], e)
}

func TestConstructionSubmitThrowsWhenDecodeStringFails(t *testing.T) {
	// given:
	exampleConstructionSubmitRequest := &types.ConstructionSubmitRequest{
		NetworkIdentifier: networkIdentifier(),
		SignedTransaction: invalidTxHash,
	}

	// when:
	res, e := NewConstructionAPIService().ConstructionSubmit(nil, exampleConstructionSubmitRequest)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.Errors[errors.TransactionDecodeFailed], e)
}

func TestConstructionSubmitThrowsWhenUnmarshalBinaryFails(t *testing.T) {
	constructionSubmitTxHash := "0xfc2267c53ef8a27e2ab65f0a6b5e5607ba33b9c8c8f7304d8cb4a77aee19107d"

	// given:
	exampleConstructionSubmitRequest := &types.ConstructionSubmitRequest{
		NetworkIdentifier: networkIdentifier(),
		SignedTransaction: constructionSubmitTxHash,
	}

	// when:
	res, e := NewConstructionAPIService().ConstructionSubmit(nil, exampleConstructionSubmitRequest)

	// then:
	assert.Nil(t, res)
	assert.Equal(t, errors.Errors[errors.TransactionUnmarshallingFailed], e)
}

func TestConstructionPreprocess(t *testing.T) {
	// given:
	expectedResult := &types.ConstructionPreprocessResponse{
		Options: make(map[string]interface{}),
	}

	// when:
	res, e := NewConstructionAPIService().ConstructionPreprocess(nil, dummyConstructionPreprocessRequest(true))

	// then:
	assert.Equal(t, expectedResult, res)
	assert.Nil(t, e)
}

func TestConstructionPreprocessThrowsWithInvalidOperationsSum(t *testing.T) {
	// when:
	res, e := NewConstructionAPIService().ConstructionPreprocess(nil, dummyConstructionPreprocessRequest(false))

	// then:
	assert.Nil(t, res)
	assert.IsType(t, &types.Error{}, e)
}
