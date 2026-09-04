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
package org.kie.kogito.integrationtests.springboot;

import org.jbpm.process.instance.impl.feel.BpmnFeelSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * <code>jbpm.expressions.feel.sandboxed=false</code> in the application's configuration reaches jBPM, and the
 * documented <code>kcontext</code> shape keeps working when the live objects stand behind it.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = KogitoSpringbootApplication.class,
        properties = { BpmnFeelSettings.SANDBOXED_PROPERTY + "=false" })
class FeelSandboxOffTest extends BaseRestTest {

    @Test
    void theApplicationsConfigurationReachesTheEngine() {
        assertThat(BpmnFeelSettings.configured()).contains(false);
        assertThat(BpmnFeelSettings.isSandboxed()).isFalse();
    }

    @Test
    void theSameDocumentRunsUnsandboxed() {
        given().body("{ \"who\": \"alice\" }")
                .contentType(ContentType.JSON)
                .when()
                .post("/BPMN2FeelKcontext")
                .then()
                .statusCode(201)
                .body("id", not(emptyOrNullString()))
                .body("path", is("hit"))
                .body("nodeSeen", is("Record"))
                .body("procId", is("BPMN2FeelKcontext"));
    }
}
