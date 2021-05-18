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

package block

import (
	"context"
	"fmt"
	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services/base"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/hex"
	log "github.com/sirupsen/logrus"
)

// BlockAPIService implements the server.BlockAPIServicer interface.
type BlockAPIService struct {
	base.BaseService
}

// NewBlockAPIService creates a new instance of a BlockAPIService.
func NewBlockAPIService(base base.BaseService) server.BlockAPIServicer {
	return &BlockAPIService{
		BaseService: base,
	}
}

// Block implements the /block endpoint.
func (s *BlockAPIService) Block(
	ctx context.Context,
	request *rTypes.BlockRequest,
) (*rTypes.BlockResponse, *rTypes.Error) {
	block, err := s.RetrieveBlock(request.BlockIdentifier)
	if err != nil {
		index := "(nil)"
		if request.BlockIdentifier.Index != nil {
			index = fmt.Sprintf("%d", *request.BlockIdentifier.Index)
		}

		hash := "(nil)"
		if request.BlockIdentifier.Hash != nil {
			hash = fmt.Sprintf("%s", *request.BlockIdentifier.Hash)
		}
		log.Errorf("Failed to retrieve block with identifier (index - %s, hash - \"%s\"), err %s",
			index, hash, err.Message)
		return nil, err
	}

	transactions, err := s.FindBetween(block.ConsensusStartNanos, block.ConsensusEndNanos)
	if err != nil {
		return nil, err
	}

	block.Transactions = transactions
	rBlock := block.ToRosetta()
	return &rTypes.BlockResponse{
		Block: rBlock,
	}, nil
}

// BlockTransaction implements the /block/transaction endpoint.
func (s *BlockAPIService) BlockTransaction(
	ctx context.Context,
	request *rTypes.BlockTransactionRequest,
) (*rTypes.BlockTransactionResponse, *rTypes.Error) {
	h := hex.SafeRemoveHexPrefix(request.BlockIdentifier.Hash)
	block, err := s.FindByIdentifier(request.BlockIdentifier.Index, h)
	if err != nil {
		return nil, err
	}

	transaction, err := s.FindByHashInBlock(
		request.TransactionIdentifier.Hash,
		block.ConsensusStartNanos,
		block.ConsensusEndNanos,
	)
	if err != nil {
		return nil, err
	}
	rTransaction := transaction.ToRosetta()
	return &rTypes.BlockTransactionResponse{
		Transaction: rTransaction,
	}, nil
}
