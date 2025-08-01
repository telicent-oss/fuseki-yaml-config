/**
 * Copyright (C) Telicent Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.telicent.jena.fuseki.config.yaml;

import java.util.Map;


public record Database(String name,
                       String dbType,
                       String attributes,
                       String attributesURL,
                       String labels,
                       String labelsStore,
                       String tripleDefaultLabels,
                       String data,
                       String location,
                       String dataset,
                       String cache,
                       String attributeCacheSize,
                       String attributeCacheExpiryTime,
                       String hierarchiesURL,
                       String hierarchyCacheSize,
                       String hierarchyCacheExpiryTime,
                       Map<String, String> settings) {}
