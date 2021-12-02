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

package client

import (
	"context"
	"fmt"
	"net/http"
	"time"

	rosettaAsserter "github.com/coinbase/rosetta-sdk-go/asserter"
	rosettaClient "github.com/coinbase/rosetta-sdk-go/client"
	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/pkg/errors"
	log "github.com/sirupsen/logrus"
)

const agent = "hedera-mirror-rosetta-test-bdd-client"

type Client struct {
	firstOperator hedera.AccountID
	hederaClient  *hedera.Client
	network       *types.NetworkIdentifier
	offlineClient *rosettaClient.APIClient
	onlineClient  *rosettaClient.APIClient
	operators     map[hedera.AccountID]hedera.PrivateKey
	retry         retry
}

func (c Client) DeleteAccount(accountId hedera.AccountID, privateKey *hedera.PrivateKey) error {
	_, err := hedera.NewAccountDeleteTransaction().
		SetAccountID(accountId).
		SetTransferAccountID(c.firstOperator).
		SetTransactionID(hedera.TransactionIDGenerate(accountId)).
		Sign(*privateKey).
		Execute(c.hederaClient)
	if err != nil {
		log.Errorf("Failed to delete account %s: %v", accountId, err)
		return nil
	}

	log.Infof("Successfully submitted the AccountDeleteTransaction for %s", accountId)
	return nil
}

func (c Client) FindTransaction(ctx context.Context, hash string) (
	*types.Transaction,
	error,
) {
	blockApi := c.onlineClient.BlockAPI

	status, rosettaErr, err := c.onlineClient.NetworkAPI.NetworkStatus(
		ctx,
		&types.NetworkRequest{NetworkIdentifier: c.network},
	)
	if err = c.handleError("Failed to get network status", rosettaErr, err); err != nil {
		return nil, err
	}

	blockIndex := status.CurrentBlockIdentifier.Index
	log.Debugf("Current block index %d", blockIndex)

	blockRequest := &types.BlockRequest{
		NetworkIdentifier: c.network,
		BlockIdentifier:   &types.PartialBlockIdentifier{Index: &blockIndex},
	}
	i := 1
	for {
		log.Infof("Looking for transaction %s in block %d", hash, blockIndex)
		blockResponse, rosettaErr, _ := blockApi.Block(ctx, blockRequest)
		if rosettaErr != nil && !rosettaErr.Retriable {
			msg := fmt.Sprintf("Failed to get block %d: %v", blockIndex, rosettaErr)
			log.Error(msg)
			return nil, errors.Errorf(msg)
		}

		if blockResponse != nil {
			log.Infof("Got block response for block %d", blockIndex)
			for _, transaction := range blockResponse.Block.Transactions {
				if transaction.TransactionIdentifier.Hash == hash {
					log.Infof("Found transaction %s in block %d", hash, blockIndex)
					return transaction, nil
				}
			}

			// only increase blockIndex when the block is successfully retrieved
			log.Infof("Transaction %s not found in block %d", hash, blockIndex)
			blockIndex += 1
		} else {
			log.Infof("Block %d not ready yet", blockIndex)
		}

		if i > c.retry.Max {
			break
		}

		i += 1
		time.Sleep(time.Second * time.Duration(c.retry.BackOff))
	}

	log.Infof("Failed to find transaction %s", hash)
	return nil, errors.Errorf("Faild to find transaction")
}

func (c Client) GetFirstOperatorAccount() *types.AccountIdentifier {
	return &types.AccountIdentifier{Address: c.firstOperator.String()}
}

// Submit submits the operations to the network, goes through the construction preprocess, metadata, payloads, combine,
// and submit workflow. Note payloads signing happens between payloads and combine.
func (c Client) Submit(ctx context.Context, operations []*types.Operation, signers map[string]hedera.PrivateKey) (
	*types.TransactionIdentifier,
	error,
) {
	offlineConstructor := c.offlineClient.ConstructionAPI
	onlineConstructor := c.onlineClient.ConstructionAPI

	if signers == nil {
		signers = map[string]hedera.PrivateKey{c.firstOperator.String(): c.operators[c.firstOperator]}
	} else {
		for signerId := range signers {
			accountId, _ := hedera.AccountIDFromString(signerId)
			operatorKey, ok := c.operators[accountId]
			if ok {
				signers[signerId] = operatorKey
			}

			if len(signers[signerId].Bytes()) == 0 {
				return nil, errors.Errorf("No private key for signer %s", signers)
			}
		}
	}

	// preprocess
	preprocessRequest := &types.ConstructionPreprocessRequest{
		NetworkIdentifier: c.network,
		Operations:        operations,
	}
	preprocessResponse, rosettaErr, err := offlineConstructor.ConstructionPreprocess(ctx, preprocessRequest)
	if err = c.handleError("Failed to handle preprocess request", rosettaErr, err); err != nil {
		return nil, err
	}

	// metadata, note currently /construction/metadata doesn't return any chain-specific data
	metadataRequest := &types.ConstructionMetadataRequest{
		NetworkIdentifier: c.network,
		Options:           preprocessResponse.Options,
	}
	_, rosettaErr, err = onlineConstructor.ConstructionMetadata(ctx, metadataRequest)
	if err = c.handleError("Failed to handle metadata request", rosettaErr, err); err != nil {
		return nil, err
	}

	// payloads
	payloadsRequest := &types.ConstructionPayloadsRequest{
		NetworkIdentifier: c.network,
		Operations:        operations,
	}
	payloadsResponse, rosettaErr, err := offlineConstructor.ConstructionPayloads(ctx, payloadsRequest)
	if err = c.handleError("Failed to handle payloads request", rosettaErr, err); err != nil {
		return nil, err
	}

	// sign
	signatures := make([]*types.Signature, 0)
	for _, signingPayload := range payloadsResponse.Payloads {
		signerAddress := signingPayload.AccountIdentifier.Address
		key := signers[signerAddress]
		signature := key.Sign(signingPayload.Bytes)
		signatures = append(signatures, &types.Signature{
			SigningPayload: signingPayload,
			PublicKey: &types.PublicKey{
				Bytes:     key.PublicKey().Bytes(),
				CurveType: types.Edwards25519,
			},
			SignatureType: types.Ed25519,
			Bytes:         signature,
		})
	}

	// combine
	combineRequest := &types.ConstructionCombineRequest{
		NetworkIdentifier:   c.network,
		UnsignedTransaction: payloadsResponse.UnsignedTransaction,
		Signatures:          signatures,
	}
	combineResponse, rosettaErr, err := offlineConstructor.ConstructionCombine(ctx, combineRequest)
	if err = c.handleError("Failed to handle combine request", rosettaErr, err); err != nil {
		return nil, err
	}

	// submit
	submitRequest := &types.ConstructionSubmitRequest{
		NetworkIdentifier: c.network,
		SignedTransaction: combineResponse.SignedTransaction,
	}
	submitResponse, rosettaErr, err := onlineConstructor.ConstructionSubmit(ctx, submitRequest)
	if err = c.handleError("Failed to handle submit request", rosettaErr, err); err != nil {
		return nil, err
	}

	return submitResponse.TransactionIdentifier, nil
}

func (c Client) handleError(message string, rosettaError *types.Error, err error) error {
	if rosettaError != nil {
		err = errors.Errorf("%s: %+v", message, rosettaError)
		log.Error(err)
		return err
	}
	if err != nil {
		log.Errorf("%s: %v", message, err)
		return err
	}
	return nil
}

func createRosettaClient(serverUrl string, timeout int) *rosettaClient.APIClient {
	cfg := rosettaClient.NewConfiguration(serverUrl, agent, &http.Client{Timeout: time.Second * time.Duration(timeout)})
	return rosettaClient.NewAPIClient(cfg)
}

func NewClient(serverCfg Server, operators []Operator) Client {
	offlineClient := createRosettaClient(serverCfg.OfflineUrl, serverCfg.HttpTimeout)
	onlineClient := createRosettaClient(serverCfg.OnlineUrl, serverCfg.HttpTimeout)

	ctx := context.Background()
	networkList, rosettaErr, err := onlineClient.NetworkAPI.NetworkList(ctx, &types.MetadataRequest{})
	if rosettaErr != nil {
		log.Fatalf("Failed to list network: %+v", rosettaErr)
	}
	if err != nil {
		log.Fatal(err)
	}

	network := networkList.NetworkIdentifiers[0]
	log.Infof("Network: %s, SubNetwork: %s", network.Network, network.SubNetworkIdentifier.Network)

	networkRequest := &types.NetworkRequest{NetworkIdentifier: network}
	status, rosettaErr, err := onlineClient.NetworkAPI.NetworkStatus(ctx, networkRequest)
	if rosettaErr != nil {
		log.Fatalf("Failed to get network status: %+v", rosettaErr)
	}
	if err != nil {
		log.Fatal(err)
	}

	log.Infof("Network status - current block: %+v", status.CurrentBlockIdentifier)

	options, rosettaErr, err := onlineClient.NetworkAPI.NetworkOptions(ctx, networkRequest)
	if rosettaErr != nil {
		log.Fatalf("Failed to get network options: %+v", rosettaErr)
	}
	if err != nil {
		log.Fatal(err)
	}

	log.Info("Fetched Network Options")

	if err := rosettaAsserter.NetworkOptionsResponse(options); err != nil {
		log.Fatalf("Failed to assert network options response: %v", err)
	}

	log.Info("Successfully set up rosetta clients for online and offline servers")

	var firstOperator hedera.AccountID
	operatorsMap := make(map[hedera.AccountID]hedera.PrivateKey)
	for idx, operator := range operators {
		if idx == 0 {
			firstOperator = operator.Id
		}
		operatorsMap[operator.Id] = operator.PrivateKey
	}

	// create hedera client
	hederaClient, err := hedera.ClientForName(network.Network)
	if err != nil {
		log.Fatalf("Failed to create client for hedera '%s'", network.Network)
	}
	log.Infof("Successfully created client for hedera '%s'", network.Network)
	hederaClient.SetOperator(firstOperator, operatorsMap[firstOperator])

	return Client{
		firstOperator: firstOperator,
		hederaClient:  hederaClient,
		network:       network,
		offlineClient: offlineClient,
		onlineClient:  onlineClient,
		operators:     operatorsMap,
		retry:         serverCfg.Retry,
	}
}
