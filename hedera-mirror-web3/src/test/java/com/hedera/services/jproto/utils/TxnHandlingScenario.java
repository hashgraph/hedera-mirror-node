/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.jproto.utils;

import static com.hedera.services.jproto.utils.KeyTree.withRoot;
import static com.hedera.services.jproto.utils.NodeFactory.ed25519;
import static com.hedera.services.jproto.utils.NodeFactory.list;
import static com.hedera.services.jproto.utils.NodeFactory.threshold;

public interface TxnHandlingScenario {
    KeyTree COMPLEX_KEY_ACCOUNT_KT = withRoot(list(
            ed25519(),
            threshold(1, list(list(ed25519(), ed25519()), ed25519()), ed25519()),
            ed25519(),
            list(threshold(2, ed25519(), ed25519(), ed25519()))));

    KeyTree SIMPLE_NEW_ADMIN_KT = withRoot(ed25519());

    KeyTree TOKEN_ADMIN_KT = withRoot(ed25519());
}
