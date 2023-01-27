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

package domain

import "github.com/hashgraph/hedera-sdk-go/v2"

func GenEd25519KeyPair() (hedera.PrivateKey, hedera.PublicKey) {
	sk, err := hedera.PrivateKeyGenerateEd25519()
	if err != nil {
		panic(err)
	}
	return sk, sk.PublicKey()
}
