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

package types

import (
	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/stretchr/testify/assert"
	"testing"
)

func expectedAmount() *types.Amount {
	return &types.Amount{
		Value:    "400",
		Currency: config.CurrencyHbar,
		Metadata: nil,
	}
}

func exampleAmount() *Amount {
	return &Amount{Value: int64(400)}
}

func TestToRosettaAmount(t *testing.T) {
	// when:
	result := exampleAmount().ToRosetta()

	// then:
	assert.Equal(t, expectedAmount(), result)
}
