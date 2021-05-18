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
		block, err = a.RetrieveLatest()
	}
	if err != nil {
		return nil, err
	}

	balance, err := a.accountRepo.RetrieveBalanceAtBlock(request.AccountIdentifier.Address, block.ConsensusEndNanos)
	if err != nil {
		return nil, err
	}

	return &rTypes.AccountBalanceResponse{
		BlockIdentifier: &rTypes.BlockIdentifier{
			Index: block.Index,
			Hash:  hexUtils.SafeAddHexPrefix(block.Hash),
		},
		Balances: []*rTypes.Amount{balance.ToRosetta()},
	}, nil
}

func (a *AccountAPIService) AccountCoins(
	ctx context.Context,
	request *rTypes.AccountCoinsRequest,
) (*rTypes.AccountCoinsResponse, *rTypes.Error) {
	return nil, errors.ErrNotImplemented
}
