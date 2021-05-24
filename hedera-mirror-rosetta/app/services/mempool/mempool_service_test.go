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

package mempool

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks/repository"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestNewMempoolAPIService(t *testing.T) {
	repository.Setup()
	mempoolService := NewMempoolAPIService()

	assert.IsType(t, &MempoolAPIService{}, mempoolService)
}

func TestMempool(t *testing.T) {
	// when:
	res, e := NewMempoolAPIService().Mempool(nil, nil)

	// then:
	assert.Equal(
		t,
		&rTypes.MempoolResponse{
			TransactionIdentifiers: []*rTypes.TransactionIdentifier{},
		},
		res,
	)

	assert.Nil(t, e)
}

func TestMempoolTransaction(t *testing.T) {
	// when:
	res, e := NewMempoolAPIService().MempoolTransaction(nil, nil)

	// then:
	assert.Equal(t, errors.ErrTransactionNotFound, e)
	assert.Nil(t, res)
}
