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

package account

import (
	"context"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/repositories"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services/base"
	hexUtils "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/hex"
)

// AccountAPIService implements the server.AccountAPIServicer interface.
type AccountAPIService struct {
	base.BaseService
	accountRepo repositories.AccountRepository
}

// NewAccountAPIService creates a new instance of a AccountAPIService.
func NewAccountAPIService(base base.BaseService, accountRepo repositories.AccountRepository) *AccountAPIService {
	return &AccountAPIService{
		BaseService: base,
		accountRepo: accountRepo,
	}
}

// AccountBalance implements the /account/balance endpoint.
func (a *AccountAPIService) AccountBalance(
	ctx context.Context,
	request *rTypes.AccountBalanceRequest,
) (*rTypes.AccountBalanceResponse, *rTypes.Error) {
	var block *types.Block
	var err *rTypes.Error

	if request.BlockIdentifier != nil {
		block, err = a.RetrieveBlock(request.BlockIdentifier)
	} else {
		block, err = a.RetrieveSecondLatest()
	}
	if err != nil {
		return nil, err
	}

	if block.LatestIndex-block.Index < 1 {
		// only show info up to the second latest block
		return nil, errors.ErrBlockNotFound
	}

	address := request.AccountIdentifier.Address
	balances, err := a.accountRepo.RetrieveBalanceAtBlock(address, block.ConsensusEndNanos)
	if err != nil {
		return nil, err
	}

	genesisTokenBalances, err := a.getGenesisTokenBalances(address, balances, block.ConsensusEndNanos)
	if err != nil {
		return nil, err
	}
	balances = append(balances, genesisTokenBalances...)

	return &rTypes.AccountBalanceResponse{
		BlockIdentifier: &rTypes.BlockIdentifier{
			Index: block.Index,
			Hash:  hexUtils.SafeAddHexPrefix(block.Hash),
		},
		Balances: a.toRosettaBalances(balances),
	}, nil
}

func (a *AccountAPIService) AccountCoins(
	ctx context.Context,
	request *rTypes.AccountCoinsRequest,
) (*rTypes.AccountCoinsResponse, *rTypes.Error) {
	return nil, errors.ErrNotImplemented
}

// getGenesisTokenBalances get the genesis token balances for tokens transferred to the account in the next block after
// consensusEnd
func (a *AccountAPIService) getGenesisTokenBalances(
	address string,
	balances []types.Amount,
	consensusEnd int64,
) ([]types.Amount, *rTypes.Error) {
	tokens, err := a.accountRepo.RetrieveTransferredTokensInBlockAfter(address, consensusEnd)
	if err != nil {
		return nil, err
	}

	if len(tokens) == 0 {
		return []types.Amount{}, nil
	}

	tokenSet := make(map[string]bool)
	for _, balance := range balances {
		if t, ok := balance.(*types.TokenAmount); ok {
			tokenSet[t.TokenId.String()] = true
		}
	}

	genesisTokenBalances := make([]types.Amount, 0)
	for _, token := range tokens {
		if _, ok := tokenSet[token.TokenId.String()]; !ok {
			tokenBalance := &types.TokenAmount{
				Decimals: int64(token.Decimals),
				TokenId:  token.TokenId,
				Value:    0,
			}
			genesisTokenBalances = append(genesisTokenBalances, tokenBalance)
		}
	}

	return genesisTokenBalances, nil
}

func (a *AccountAPIService) toRosettaBalances(balances []types.Amount) []*rTypes.Amount {
	rosettaBalances := make([]*rTypes.Amount, 0, len(balances))
	for _, balance := range balances {
		rosettaBalances = append(rosettaBalances, balance.ToRosetta())
	}

	return rosettaBalances
}
