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

package types

import (
	"strings"
	"testing"

	"github.com/hashgraph/hedera-sdk-go/v2/proto"
	"github.com/stretchr/testify/assert"
)

func TestResponseCodeUpToDate(t *testing.T) {
	// given
	for code, name := range proto.ResponseCodeEnum_name {
		assert.Equal(t, name, TransactionResults[code])
	}

}

func TestResponseTypesUpToDate(t *testing.T) {
	// given
	for code, name := range proto.HederaFunctionality_name {
		assert.Equal(t, strings.ToUpper(name), TransactionResults[code])
	}

}
