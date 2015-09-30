/**
 * Copyright 2015 DuraSpace, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fcrepo.repository;

import static java.lang.Integer.MAX_VALUE;
import static javax.ws.rs.core.Response.Status.CREATED;
import static org.slf4j.LoggerFactory.getLogger;

import java.net.URI;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;

/**
 * Uses an HTTP client configured with a repository base URI to PUT resources with a given path and content.
 *
 * @author Peter Eichman
 * @since 2015-09-25
 */
public class ResourcePutter {

    private static final Logger LOGGER = getLogger(ResourcePutter.class);

    private final HttpClient httpClient = HttpClientBuilder.create().setMaxConnPerRoute(MAX_VALUE)
            .setMaxConnTotal(MAX_VALUE).build();

    private final URI baseUrl;

    /**
     * The base URI is given in baseUrl. It should end with a "/" for relative URI refs to resolve as expected.
     *
     * @param baseUrl
     */
    public ResourcePutter(final URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Issues a PUT request to a URI constructed from the baseUrl plus the given uriRef with the entity as the content
     * of that request. The Content-Type header is always forced to text/turtle.
     *
     * @param uriRef relative path for the uploaded resource
     * @param entity content of the resource
     * @return true on success, false on failure
     */
    public boolean put(final String uriRef, final HttpEntity entity) {
        final URI requestURI = baseUrl.resolve(uriRef);
        final HttpPut put = new HttpPut(requestURI);
        put.setEntity(entity);
        put.setHeader("Content-Type", "text/turtle");
        HttpResponse res;
        try {
            res = httpClient.execute(put);
            LOGGER.debug("URL:" + requestURI);
            LOGGER.debug("Response:" + res.toString());
            return res.getStatusLine().getStatusCode() == CREATED.getStatusCode();
        } catch (final Exception e) {
            LOGGER.warn("Failed to PUT " + requestURI);
            e.printStackTrace();
            return false;
        }
    }
}
