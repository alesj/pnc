/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.integration.client;

import org.jboss.pnc.rest.restmodel.bpm.RepositoryCreationRest;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class RepositoryCreationRestClient {

    private static final String REST_ENDPOINT = "/pnc-rest/rest/repository-creation";

    RestClient restClient;

    public RepositoryCreationRestClient() {
        restClient = new RestClient();
    }

    public com.jayway.restassured.response.Response createNewRCAndBC(RepositoryCreationRest repositoryCreationRest) {
        return restClient.post(REST_ENDPOINT, repositoryCreationRest);
    }

}
