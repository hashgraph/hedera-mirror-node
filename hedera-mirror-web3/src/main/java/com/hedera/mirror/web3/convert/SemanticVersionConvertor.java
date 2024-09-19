/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.convert;

import com.hedera.hapi.node.base.SemanticVersion;
import jakarta.inject.Named;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;

@Named
@ConfigurationPropertiesBinding
public class SemanticVersionConvertor implements Converter<String, SemanticVersion> {
    /** Arbitrary limit to prevent stack overflow when parsing unrealistically long versions. */
    private static final int MAX_VERSION_LENGTH = 100;
    /** From <a href="https://semver.org/#is-there-a-suggested-regular-expression-regex-to-check-a-semver-string"></a> */
    // suppress the warning that the regular expression is too complicated
    @SuppressWarnings({"java:S5843", "java:S5998"})
    private static final Pattern SEMVER_SPEC_REGEX = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)"
                    + "(?:\\."
                    + "(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)"
                    + "*))?$");

    @Override
    public SemanticVersion convert(String value) {
        if (value.length() > MAX_VERSION_LENGTH) {
            throw new IllegalArgumentException("Semantic version '" + value + "' is too long");
        }

        final var matcher = SEMVER_SPEC_REGEX.matcher(value);
        if (matcher.matches()) {
            final var builder = SemanticVersion.newBuilder()
                    .major(Integer.parseInt(matcher.group(1)))
                    .minor(Integer.parseInt(matcher.group(2)))
                    .patch(Integer.parseInt(matcher.group(3)));
            if (matcher.group(4) != null) {
                builder.pre(matcher.group(4));
            }
            if (matcher.group(5) != null) {
                builder.build(matcher.group(5));
            }
            return builder.build();
        } else {
            throw new IllegalArgumentException("'" + value + "' is not a valid semantic version");
        }
    }
}
