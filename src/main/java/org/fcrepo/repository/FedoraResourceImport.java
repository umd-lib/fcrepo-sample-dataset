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

import static org.slf4j.LoggerFactory.getLogger;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;

/**
 * Tool to load a directory tree of Turtle RDF files into a repository.
 *
 * @author Peter Eichman
 * @since 2015-09-25
 */
public class FedoraResourceImport {
    private static final Logger LOGGER = getLogger(FedoraResourceImport.class);

    /**
     * Upload a directory tree of Turtle files into a repository. The repository base URL passed in using the
     * fcrepo.url system property (default "http://localhost:8080/rest/"), and the directory to scan for Turtle files
     * is given in the resources.dir system property (default "." for the current directory).
     *
     * @param args
     */
    public static void main(final String[] args) {

        final String fcrepoUrl = System.getProperty("fcrepo.url", "http://localhost:8080/rest/");
        final String resourcesDir = System.getProperty("resources.dir", ".");

        final File dir = new File(resourcesDir);

        LOGGER.info("fcrepoUrl:" + fcrepoUrl);
        LOGGER.info("resources dir:" + dir.getAbsolutePath());

        final Path filesRoot = Paths.get(resourcesDir);
        final ResourcePutter putter = new ResourcePutter(URI.create(fcrepoUrl));
        final FileFinder finder = new FileFinder(filesRoot, putter);
        try {
            Files.walkFileTree(filesRoot, finder);
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
}