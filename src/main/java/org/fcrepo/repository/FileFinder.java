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
import static org.fcrepo.repository.FedoraResourceImport.MODE_CREATE;
import static org.fcrepo.repository.FedoraResourceImport.MODE_UPDATE;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.activation.MimetypesFileTypeMap;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.ParseException;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.slf4j.Logger;

/**
 * File visitor that looks for files with specific extensions and uploads them to a repository using a ResourcePutter
 * object.
 * 
 * @author Peter Eichman
 * @since 2015-09-25
 */
public class FileFinder extends SimpleFileVisitor<Path> {

  private static final Logger LOGGER = getLogger(FileFinder.class);

  private final Path root;

  private final ResourceLoader resourceLoader;

  private final ByteArrayInputStream prefixStream;

  private String finderMode = MODE_CREATE;

  private String fileType = ".ttl";

  private static final List<String> allowedBinaryFormats = getAllowedFormatsFromFile();

  private boolean skipDirsWithoutMetaFile = false;

  private String logPrefix = "Creating";

  private static StringEntity emptyEntity = new StringEntity("", Charset.forName("UTF-8"));

  public FileFinder(final Path root, final ResourceLoader resourceLoader, final String prefixFileLocation)
      throws IOException {
    this.root = root;
    this.resourceLoader = resourceLoader;
    this.prefixStream = new ByteArrayInputStream(FileUtils.readFileToByteArray(new File(prefixFileLocation)));
  }

  private static List<String> getAllowedFormatsFromFile() {
    final String fileLocation = FedoraDatasetImport.class.getResource("/allowed-binary-formats").getFile();
    String line = null;
    ArrayList<String> formats = new ArrayList<String>();
    try {
      BufferedReader br = new BufferedReader(new FileReader(new File(fileLocation)));
      while ((line = br.readLine()) != null) {
        formats.add("." + line.trim());
      }
      br.close();
    } catch (Exception e) {
      LOGGER
          .warn("Exception while reading allowed-binary-formats file! Defaulting to jpg as the only allowed binary format!");
      LOGGER.debug("Exception:", e);
      formats.add(".jpg");
    }
    return formats;
  }

  public void setFileType(final String type) {
    this.fileType = type;
  }

  public void skipDirsWithoutMetaFile(final boolean skip) {
    this.skipDirsWithoutMetaFile = skip;
  }

  public void setFinderMode(String mode) {
    if (mode.equals(MODE_CREATE)) {
      this.finderMode = MODE_CREATE;
      this.fileType = ".ttl";
      this.skipDirsWithoutMetaFile = false;
      this.logPrefix = "Creating";
      return;
    } else if (mode.equals(MODE_UPDATE)) {
      this.finderMode = MODE_UPDATE;
      this.fileType = ".ru";
      this.skipDirsWithoutMetaFile = true;
      this.logPrefix = "Patching";
      return;
    } else {
      LOGGER.debug("Unknown finder mode: " + mode);
      return;
    }
  }

  /**
   * For each file with the .ttl or .ru extension, if it is not also a dotfile or named "_", send its contents in a HTTP
   * request to a URI constructed by removing the extension from the filename. So, for example, a file named
   * "collection/23/data.ttl" is uploaded to the relative URI "collection/23/data". PUT method is used for .ttl files
   * and PATCH method is used for .ru files.
   * 
   * @throws IOException
   */
  @Override
  public FileVisitResult visitFile(final Path file,
      final BasicFileAttributes attrs) throws IOException {

    final String filename = file.getFileName().toString();

    if (filename.endsWith(fileType) && !filename.startsWith(".") && !filename.equals("_" + fileType)) {
      // file is not hidden ("dotfile") or the special "_.ttl" that represents the container
      final String uriRef = root.relativize(file).toString().replaceAll("\\" + fileType + "$", "");
      LOGGER.info("{} {} from {}", logPrefix, uriRef, filename);
      final FileInputStream fileStream = new FileInputStream(file.toFile());
      prefixStream.reset();
      loadResourceWithEntity(uriRef, new InputStreamEntity(new SequenceInputStream(prefixStream, fileStream)), null);
      fileStream.close();
    } else if (finderMode.equals(MODE_CREATE)) {
      final String extension = filename.substring(filename.lastIndexOf("."));
      if (allowedBinaryFormats.contains(extension) && !filename.startsWith(".") && !filename.equals("_" + extension)) {
        String uriRef = root.relativize(file).toString().replaceAll("\\" + extension + "$", "");
        LOGGER.info("{} {} from {}", logPrefix, uriRef, filename);
        final String mimeType = new MimetypesFileTypeMap().getContentType(extension);
        loadResourceWithEntity(uriRef, new InputStreamEntity(new FileInputStream(file.toFile())), mimeType);
      }
    }

    return CONTINUE;
  }

  /**
   * For each directory, if there is a file named "_.ttl", use that as the Turtle representation of the container
   * corresponding to this directory. Otherwise, use an empty entity to force creation of a container unless
   * skipDirsWithoutMetaFile is set to true. If there is a file name "_.ru", use that as the SPARQL query to update the
   * container.
   * 
   * @throws IOException
   */
  @Override
  public FileVisitResult preVisitDirectory(final Path dir,
      final BasicFileAttributes attrs) throws IOException {

    final String uriRef = root.relativize(dir).toString();

    final Path dirFile = Paths.get(dir.toString(), "_" + fileType);
    if (Files.exists(dirFile)) {
      LOGGER.info("{} container {}", logPrefix, uriRef);
      prefixStream.reset();
      final FileInputStream fileStream = new FileInputStream(dirFile.toFile());
      loadResourceWithEntity(uriRef, new InputStreamEntity(new SequenceInputStream(prefixStream, fileStream)), null);
      fileStream.close();
    } else if (finderMode.equals(MODE_CREATE)) {
      final Path dirBinaryFile = getAllowedBinaryFile(dir);
      if (dirBinaryFile != null) {
        LOGGER.info("{} binary {}", logPrefix, uriRef);
        final String mimeType = new MimetypesFileTypeMap().getContentType(dirBinaryFile.toString());
        loadResourceWithEntity(uriRef, new InputStreamEntity(new FileInputStream(dirBinaryFile.toFile())), mimeType);
      } else if (!(skipDirsWithoutMetaFile || isPairTreeCompatibleName(dir))) {
        LOGGER.info("{} container {}", logPrefix, uriRef);
        loadResourceWithEntity(uriRef, emptyEntity, null);
      }
    }
    return CONTINUE;
  }

  private Path getAllowedBinaryFile(Path dir) {
    Path dirFile;
    for (Iterator<String> iter = allowedBinaryFormats.iterator(); iter.hasNext();) {
      dirFile = Paths.get(dir.toString(), "_" + iter.next());
      if (Files.exists(dirFile)) {
        return dirFile;
      }
    }
    return null;
  }

  private void loadResourceWithEntity(String uriRef, HttpEntity entity, String mimeType) throws ParseException,
      IOException {
    if (finderMode.equals(MODE_CREATE)) {
      if (mimeType != null) {
        resourceLoader.put(uriRef, entity, mimeType);
      } else {
        resourceLoader.put(uriRef, entity);
      }
    } else {
      resourceLoader.patch(uriRef, entity);
    }
  }

  private boolean isPairTreeCompatibleName(Path path) {
    return path.getFileName().toString().length() == 2;
  }

}
