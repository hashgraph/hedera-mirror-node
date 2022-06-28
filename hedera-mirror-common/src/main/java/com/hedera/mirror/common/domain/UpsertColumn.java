package com.hedera.mirror.common.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Used to customize the logic used to upsert this column.
 */
@Documented
@Target(FIELD)
@Retention(RUNTIME)
public @interface UpsertColumn {

    /**
     * Specify custom logic to use for coalescing this column during the upsert process. To avoid repetition,
     * replacement variables can be used. {0} is column name and {1} is column default. t or blank is the temporary
     * table alias and e is the existing.
     *
     * @return the SQL clause
     */
    String coalesce() default "";
}
