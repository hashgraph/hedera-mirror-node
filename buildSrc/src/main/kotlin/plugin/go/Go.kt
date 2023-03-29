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
package plugin.go

import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Internal
import org.gradle.kotlin.dsl.getByName

// Template task to execute the go CLI
abstract class Go : Exec() {

    @Internal
    val go = project.extensions.getByName<GoExtension>("go")

    init {
        dependsOn("setup")
    }

    override fun exec() {
        logger.info("Executing go ${args}")
        executable(go.goBin.absolutePath)
        super.exec()
    }
}
