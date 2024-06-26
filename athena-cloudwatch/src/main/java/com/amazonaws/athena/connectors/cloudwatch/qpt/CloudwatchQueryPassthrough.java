/*-
 * #%L
 * athena-docdb
 * %%
 * Copyright (C) 2019 - 2024 Amazon Web Services
 * %%
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
 * #L%
 */
package com.amazonaws.athena.connectors.cloudwatch.qpt;

import com.amazonaws.athena.connector.lambda.metadata.optimizations.querypassthrough.QueryPassthroughSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public final class CloudwatchQueryPassthrough implements QueryPassthroughSignature
{
    private static final String SCHEMA_NAME = "system";
    private static final String NAME = "query";
    // List of arguments for the query, statically initialized as it always contains the same value.
    public static final String ENDTIME = "ENDTIME";
    public static final String LIMIT = "LIMIT";
    public static final String LOGGROUPNAMES = "LOGGROUPNAMES";
    public static final String QUERYSTRING = "QUERYSTRING";
    public static final String STARTTIME = "STARTTIME";
    private static final Logger LOGGER = LoggerFactory.getLogger(CloudwatchQueryPassthrough.class);

    @Override
    public String getFunctionSchema()
    {
        return SCHEMA_NAME;
    }

    @Override
    public String getFunctionName()
    {
        return NAME;
    }

    @Override
    public List<String> getFunctionArguments()
    {
        return Arrays.asList(ENDTIME, QUERYSTRING, STARTTIME, LOGGROUPNAMES, LIMIT);
    }

    @Override
    public Logger getLogger()
    {
        return LOGGER;
    }
}
