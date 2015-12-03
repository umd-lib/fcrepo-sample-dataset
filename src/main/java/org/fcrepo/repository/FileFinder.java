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

import static java.nio.file.FileVisitResult.CONTINUE;
import static org.slf4j.LoggerFactory.getLogger;

import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.slf4j.Logger;

/**
 * File visitor that looks for Turtle files (.ttl extension) and uploads them to a repository using a ResourcePutter
 * object.
 * 
 * @author Peter Eichman
 * @since 2015-09-25
 */
public class FileFinder extends SimpleFileVisitor<Path> {

  private static final Logger LOGGER = getLogger(FileFinder.class);

  private final Path root;

  private final ResourcePutter putter;

  private static StringEntity emptyEntity = new StringEntity("", Charset.forName("UTF-8"));

  public FileFinder(final Path root, final ResourcePutter putter) {
    this.root = root;
    this.putter = putter;
  }

  /**
     * For each file with the ".ttl" extension, if it is not also a dotfile or named "_.ttl", send its contents in a
     * PUT request to a URI constructed by removing the ".ttl" extension from the filename. So, for example, a file
     * named "collection/23/data.ttl" is uploaded to the relative URI "collection/23/data".
   */
  @Override
  public FileVisitResult visitFile(final Path file,
      final BasicFileAttributes attrs) {

    final String filename = file.getFileName().toString();

    if (filename.endsWith(".ttl") && !filename.startsWith(".") && !filename.equals("_.ttl")) {
      // file is not hidden ("dotfile") or the special "_.ttl" that represents the container
      final String uriRef = root.relativize(file).toString().replaceAll("\\.ttl$", "");
      LOGGER.info("Creating {} from {}", uriRef, filename);
      putter.put(uriRef, new FileEntity(file.toFile()));
    }

    return CONTINUE;
  }

  /**
   * For each directory, if there is a file named "_.ttl", use that as the Turtle representation of the container
   * corresponding to this directory. Otherwise, use an empty entity to force creation of a container.
   */
  @Override
  public FileVisitResult preVisitDirectory(final Path dir,
      final BasicFileAttributes attrs) {

    final String uriRef = root.relativize(dir).toString();

    if ("".equals(uriRef)) {
      // Skip creating container for the resource.dir root
      return CONTINUE;
    }

    LOGGER.info("Creating container " + uriRef);

    final Path dirFile = Paths.get(dir.toString(), "_.ttl");
    if (Files.exists(dirFile)) {
      putter.put(uriRef, new FileEntity(dirFile.toFile()));
    } else {
      putter.put(uriRef, emptyEntity);
    }
    return CONTINUE;
  }

}
