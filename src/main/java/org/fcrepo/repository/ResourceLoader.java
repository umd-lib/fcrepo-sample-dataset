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
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.net.URI;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;

/**
 * Uses an HTTP client configured with a repository base URI to PUT or PATCH resources with a given path and content.
 * 
 * @author Peter Eichman
 * @since 2015-09-25
 */
public class ResourceLoader {

  private static final Logger LOGGER = getLogger(ResourceLoader.class);

  private final HttpClient httpClient = HttpClientBuilder.create().setMaxConnPerRoute(MAX_VALUE)
      .setMaxConnTotal(MAX_VALUE).build();

  private final URI baseUrl;

  private final String authHeader;

  /**
   * The base URI is given in baseUrl. It should end with a "/" for relative URI refs to resolve as expected.
   * 
   * @param baseUrl
   */
  public ResourceLoader(final URI baseUrl) {
    this.baseUrl = baseUrl;
    this.authHeader = null;
  }

  /**
   * The base URI is given in baseUrl. It should end with a "/" for relative URI refs to resolve as expected.
   * 
   * @param baseUrl
   */
  public ResourceLoader(final URI baseUrl, final String authHeader) {
    this.baseUrl = baseUrl;
    this.authHeader = authHeader;
  }

  /**
   * Issues a PUT request to a URI constructed from the baseUrl plus the given uriRef with the entity as the content of
   * that request. The Content-Type header is always forced to text/turtle.
   * 
   * @param uriRef
   *          relative path for the uploaded resource
   * @param entity
   *          content of the resource
   * @return true on success, false on failure
   * @throws IOException
   * @throws ParseException
   */
  public boolean put(final String uriRef, final HttpEntity entity) throws ParseException, IOException {
    return put(uriRef, entity, "text/turtle");
  }

  /**
   * Issues a PUT request to a URI constructed from the baseUrl plus the given uriRef with the entity as the content of
   * that request. The provided mimeType is used as the Content-Type header.
   * 
   * @param uriRef
   *          relative path for the uploaded resource
   * @param entity
   *          content of the resource
   * @param contentType
   *          MIME type of the resource
   * @return true on success, false on failure
   * @throws IOException
   * @throws ParseException
   */
  public boolean put(final String uriRef, final HttpEntity entity, final String contentType) throws ParseException,
      IOException {
    final HttpPut put = new HttpPut(baseUrl.resolve(uriRef));
    put.setHeader("Content-Type", contentType);
    return action(put, uriRef, entity, CREATED.getStatusCode());
  }

  /**
   * Issues a PATCH request to a URI constructed from the baseUrl plus the given uriRef with the entity as the content
   * of that request. The Content-Type header is always forced to "application/sparql-update.
   * 
   * @param uriRef
   *          relative path for the uploaded resource
   * @param entity
   *          content of the resource
   * @return true on success, false on failure
   * @throws IOException
   * @throws ParseException
   */
  public boolean patch(final String uriRef, final HttpEntity entity) throws ParseException, IOException {
    // final HttpPatch patch = new HttpPatch(baseUrl.resolve(uriRef));
    final HttpPost postAsPatch = new HttpPost(baseUrl.resolve(uriRef)) {
      @Override
      public String getMethod() {
        return "PATCH";
      }
    };
    postAsPatch.setHeader("Content-Type", "application/sparql-update");
    return action(postAsPatch, uriRef, entity, NO_CONTENT.getStatusCode());
  }

  /**
   * Executes the PUT / PATCH methods after adding Authorization and Content-Type headers.
   * 
   * @param uriRef
   *          relative path for the uploaded resource
   * @param entity
   *          content of the resource
   * @return true on success, false on failure
   * @throws IOException
   * @throws ParseException
   */
  public boolean action(final HttpEntityEnclosingRequest method, final String uriRef, final HttpEntity entity,
      final int expectedCode)
      throws ParseException, IOException {
    final URI requestURI = baseUrl.resolve(uriRef);

    String content = EntityUtils.toString(entity);
    LOGGER.debug("Content: " + content);
    method.setEntity(new StringEntity(content));

    if (authHeader != null) {
      method.setHeader("Authorization", authHeader);
    }

    HttpResponse res;
    try {
      res = httpClient.execute((HttpUriRequest) method);
      LOGGER.debug("URL:" + requestURI);
      LOGGER.debug("Response:" + res.toString());
      return res.getStatusLine().getStatusCode() == CREATED.getStatusCode();
    } catch (final Exception e) {
      LOGGER.warn("Request failed: " + requestURI);
      e.printStackTrace();
      return false;
    }
  }
}
