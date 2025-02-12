/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
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
 */

package client

import (
	"fmt"
	"time"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	"github.com/pkg/errors"
)

type Operator struct {
	Id         hiero.AccountID
	PrivateKey hiero.PrivateKey
}

func (o Operator) String() string {
	return fmt.Sprintf("{Id: %s PrivateKey: ***}", o.Id)
}

type retry struct {
	BackOff time.Duration
	Max     int
}

func (r retry) Run(work func() (bool, *types.Error, error), forceRetryOnRosettaError bool) (*types.Error, error) {
	var done bool
	var rosettaErr *types.Error
	var err error
	i := 1
	for {
		done, rosettaErr, err = work()
		if rosettaErr != nil {
			if !forceRetryOnRosettaError && !rosettaErr.Retriable {
				break
			}
		} else if err != nil {
			break
		} else if done {
			break
		}

		if i > r.Max {
			err = errors.Errorf("Exceeded %d retries", r.Max)
			break
		}

		i += 1
		time.Sleep(r.BackOff)
	}

	return rosettaErr, err
}

type Server struct {
	DataRetry   retry
	HttpTimeout time.Duration
	Network     map[string]hiero.AccountID
	OfflineUrl  string
	OnlineUrl   string
	SubmitRetry retry
}
