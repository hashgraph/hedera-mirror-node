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

package com.hedera.services.config;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hederahashgraph.api.proto.java.AccountID;

public class MockGlobalDynamicProps extends GlobalDynamicProperties {

    public MockGlobalDynamicProps() {
        super(null, null);
    }

    @Override
    public void reload() {}

    @Override
    public AccountID fundingAccount() {
        return AccountID.newBuilder().setAccountNum(98L).build();
    }

    @Override
    public boolean isStakingEnabled() {
        return true;
    }

    @Override
    public int getStakingRewardPercent() {
        return 10;
    }

    @Override
    public int getNodeRewardPercent() {
        return 10;
    }
}
