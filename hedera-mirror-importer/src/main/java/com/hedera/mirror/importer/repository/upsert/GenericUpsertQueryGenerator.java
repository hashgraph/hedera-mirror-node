/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.repository.upsert;

import java.io.StringWriter;
import java.text.MessageFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

@Log4j2
@RequiredArgsConstructor
public class GenericUpsertQueryGenerator implements UpsertQueryGenerator {

    private static final String UPSERT_TEMPLATE = "/db/template/upsert.vm";
    private static final String UPSERT_HISTORY_TEMPLATE = "/db/template/upsert_history.vm";

    private final EntityMetadata metadata;

    @Override
    public String getCreateTempIndexQuery() {
        String columns = metadata.columns(ColumnMetadata::isId, "{0}");
        return MessageFormat.format(
                "create index if not exists {0}_idx on {0} ({1})", getTemporaryTableName(), columns);
    }

    @Override
    public String getCreateTempTableQuery() {
        return String.format(
                "create temporary table if not exists %s on commit drop as table %s limit 0",
                getTemporaryTableName(), metadata.getTableName());
    }

    @Override
    public String getFinalTableName() {
        return metadata.getTableName();
    }

    /**
     * Constructs an upsert query using a velocity template with replacement variables for table and column names
     * constructed from the `EntityMetadata` metadata.
     *
     * @return the upsert query
     */
    @Override
    public String getUpsertQuery() {
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADERS, RuntimeConstants.RESOURCE_LOADER_CLASS);
        velocityEngine.setProperty("resource.loader.class.class", ClasspathResourceLoader.class.getName());
        velocityEngine.init();

        String templatePath = metadata.getUpsertable().history() ? UPSERT_HISTORY_TEMPLATE : UPSERT_TEMPLATE;
        Template template = velocityEngine.getTemplate(templatePath);

        VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("finalTable", getFinalTableName());
        velocityContext.put("historyTable", getFinalTableName() + "_history");
        velocityContext.put("tempTable", getTemporaryTableName());

        // {0} is column name and {1} is column default. t or blank is the temporary table alias and e is the existing.
        velocityContext.put("coalesceColumns", metadata.columns("coalesce({0}, e_{0}, {1})"));
        velocityContext.put("conflictColumns", metadata.columns(ColumnMetadata::isId, "{0}"));
        velocityContext.put("existingColumns", closeRange(metadata.columns("e_{0}")));
        velocityContext.put("existingColumnsAs", metadata.columns("e.{0} as e_{0}"));
        velocityContext.put("idJoin", metadata.columns(ColumnMetadata::isId, "e.{0} = t.{0}", " and "));
        velocityContext.put("insertColumns", metadata.columns("{0}"));
        velocityContext.put(
                "notUpdatableColumn", metadata.column(c -> !c.isUpdatable(), "coalesce({0}, e_{0}) is not null"));
        velocityContext.put("updateColumns", metadata.columns(ColumnMetadata::isUpdatable, "{0} = excluded.{0}"));

        StringWriter writer = new StringWriter();
        template.merge(velocityContext, writer);
        return writer.toString();
    }

    private String closeRange(String input) {
        return input.replace(
                "e_timestamp_range", "int8range(lower(e_timestamp_range), lower(timestamp_range)) as timestamp_range");
    }
}
