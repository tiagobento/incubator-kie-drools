/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.kie.kogito.integrationtests;

import java.util.HashMap;
import java.util.Map;

import org.jbpm.process.instance.impl.feel.BpmnFeelSettings;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * What the engine believes its FEEL setting to be, as seen from inside the running application: the way an
 * integration test, running outside it, can tell whether the application's configuration reached jBPM.
 */
@Path("/feel-settings")
@Produces(MediaType.APPLICATION_JSON)
public class FeelSettingsResource {

    @GET
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("sandboxed", BpmnFeelSettings.isSandboxed());
        settings.put("configured", BpmnFeelSettings.configured().orElse(null));
        return settings;
    }
}
