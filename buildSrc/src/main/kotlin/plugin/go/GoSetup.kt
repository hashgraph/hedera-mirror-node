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

package plugin.go

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.getByName
import java.io.File
import java.io.FileInputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream

// Downloads and decompresses the Go artifacts
open class GoSetup : DefaultTask() {

    @TaskAction
    fun prepare() {
        val go = project.extensions.getByName<GoExtension>("go")
        go.goRoot.mkdirs()
        val url =
            URL("https://storage.googleapis.com/golang/go${go.version}.${go.os}-${go.arch}.tar.gz")
        val filename = Paths.get(url.path).fileName
        val targetFile = go.cacheDir.toPath().resolve(filename)

        if (!targetFile.toFile().exists()) {
            url.openStream().use {
                logger.warn("Downloading: ${url}")
                Files.copy(it, targetFile, StandardCopyOption.REPLACE_EXISTING)
            }
        }

        if (!go.goBin.exists()) {
            decompressTgz(targetFile.toFile(), go.cacheDir)
        }
    }

    fun decompressTgz(source: File, destDir: File) {
        TarArchiveInputStream(GZIPInputStream(FileInputStream(source)), "UTF-8").use {
            destDir.mkdirs()
            var entry = it.nextEntry

            while (entry != null) {
                val file = destDir.resolve(entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    Files.copy(it, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    if (!file.setExecutable(true, true)) {
                        throw IllegalStateException("Unable to set execute bit on file " + file)
                    }

                }
                entry = it.nextEntry
            }
        }
    }
}
