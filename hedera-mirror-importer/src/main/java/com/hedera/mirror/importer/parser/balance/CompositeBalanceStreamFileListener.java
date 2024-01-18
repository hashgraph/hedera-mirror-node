/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.balance;

import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.parser.StreamFileListener;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import jakarta.inject.Named;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;

@Named
@Primary
@RequiredArgsConstructor
public class CompositeBalanceStreamFileListener implements BalanceStreamFileListener {

    private final List<BalanceStreamFileListener> listeners;
    private final AccountBalanceFileRepository accountBalanceFileRepository;

    @Override
    public void onStart() throws ImporterException {
        onEach(StreamFileListener::onStart);
    }

    @Override
    public void onEnd(AccountBalanceFile streamFile) throws ImporterException {
        accountBalanceFileRepository.save(streamFile);
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onEnd(streamFile);
        }
    }

    @Override
    public void onError() {
        onEach(StreamFileListener::onError);
    }

    private void onEach(Consumer<BalanceStreamFileListener> consumer) {
        for (int i = 0; i < listeners.size(); i++) {
            consumer.accept(listeners.get(i));
        }
    }
}
