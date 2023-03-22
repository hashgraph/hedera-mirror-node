package com.hedera.mirror.common.exception;

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

import java.io.Serial;

public abstract class MirrorNodeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 8757494818600695310L;

    protected MirrorNodeException(String message) {
        super(message);
    }

    protected MirrorNodeException(Throwable throwable) {
        super(throwable);
    }

    protected MirrorNodeException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
