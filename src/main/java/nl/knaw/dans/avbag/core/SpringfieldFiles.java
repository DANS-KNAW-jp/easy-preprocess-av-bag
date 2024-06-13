/*
 * Copyright (C) 2024 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
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
package nl.knaw.dans.avbag.core;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static org.apache.commons.io.FilenameUtils.getExtension;
import static org.apache.commons.io.FilenameUtils.removeExtension;

@Slf4j
public class SpringfieldFiles {

    private final Path bagDir;
    private final Document filesXml;
    private final Map<String, Element> idToElement = new HashMap<>();

    public SpringfieldFiles(Path bagDir, Document filesXml) throws IOException {
        this.bagDir = bagDir;
        this.filesXml = filesXml;
        var idElems = filesXml.getElementsByTagName("dct:identifier");
        for (int i = 0; i < idElems.getLength(); i++) {
            var idElem = (Element) idElems.item(i);
            idToElement.put(idElem.getTextContent(), (Element) idElem.getParentNode());
        }
    }

    public void addFiles(Map<String, Path> springFieldFiles, PlaceHolders placeHolders) throws IOException {
        List<Node> newFileList = new ArrayList<>();
        for (var entry : springFieldFiles.entrySet()) {
            var added = addPayloadFile(entry.getValue(), placeHolders.getDestPath(entry.getKey()));
            var newFileElement = newFileElement(added, idToElement.get(entry.getKey()));
            newFileList.add(newFileElement);
        }
        // separate loops to not interfere prematurely
        for (Node newFile : newFileList) {
            filesXml.getElementsByTagName("files").item(0)
                .appendChild(newFile);
        }
    }

    private String addPayloadFile(Path source, String placeHolder) throws IOException {
        var sourceExtension = getExtension(source.toString());
        var placeHolderExtension = getExtension(placeHolder);
        var newExtension = sourceExtension.equals(placeHolderExtension)
            ? "-streaming." + sourceExtension
            : "." + sourceExtension;

        var destination = removeExtension(placeHolder) + newExtension;
        FileUtils.copyFile(
            source.toFile(),
            bagDir.relativize(Path.of(destination)).toFile(),
            true,
            COPY_ATTRIBUTES
        );
        return destination;
    }

    private Element newFileElement(String addedFilePath, Element oldFileElement) {
        if (oldFileElement == null) {
            log.error("{} No file element found for: {}", bagDir.getParent().getFileName(), addedFilePath);
            return null;
        }
        var newElement = filesXml.createElement("file");
        newElement.setAttribute("filepath", addedFilePath);
        newElement.appendChild(newRightsElement("accessibleToRights", oldFileElement));
        newElement.appendChild(newRightsElement("visibleToRights", oldFileElement));
        return newElement;
    }

    private Element newRightsElement(String tag, Element oldFileElement) {
        var oldRights = (Element) oldFileElement.getElementsByTagName(tag).item(0);
        var rightsElement = filesXml.createElement(oldRights.getTagName());
        rightsElement.setTextContent(oldRights.getTextContent());
        return rightsElement;
    }
}