/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.microsoft;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.poi.hpsf.ClassID;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.Ole10Native;
import org.apache.poi.poifs.filesystem.Ole10NativeException;
import org.xml.sax.SAXException;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.detect.zip.DefaultZipContainerDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.microsoft.OfficeParser.POIFSDocumentType;
import org.apache.tika.sax.XHTMLContentHandler;

abstract class AbstractPOIFSExtractor {
    protected final Metadata parentMetadata;//metadata of the parent/container document
    protected final OfficeParserConfig officeParserConfig;
    protected final ParseContext context;
    private final EmbeddedDocumentUtil embeddedDocumentUtil;
    private PasswordProvider passwordProvider;

    protected AbstractPOIFSExtractor(ParseContext context) {
        this(context, null);
    }

    protected AbstractPOIFSExtractor(ParseContext context, Metadata parentMetadata) {
        embeddedDocumentUtil = new EmbeddedDocumentUtil(context);

        this.passwordProvider = context.get(PasswordProvider.class);
        this.officeParserConfig = context.get(OfficeParserConfig.class, new OfficeParserConfig());
        this.parentMetadata = parentMetadata;
        this.context = context;
    }

    // Note - these cache, but avoid creating the default TikaConfig if not needed
    protected TikaConfig getTikaConfig() {
        return embeddedDocumentUtil.getTikaConfig();
    }

    protected Detector getDetector() {
        return embeddedDocumentUtil.getDetector();
    }

    /**
     * @return mimetypes
     * @deprecated use {@link #embeddedDocumentUtil}
     */
    protected MimeTypes getMimeTypes() {
        return embeddedDocumentUtil.getMimeTypes();
    }

    /**
     * Returns the password to be used for this file, or null
     * if no / default password should be used
     */
    protected String getPassword() {
        if (passwordProvider != null) {
            return passwordProvider.getPassword(parentMetadata);
        }
        return null;
    }

    // PUTHURR

    protected void handleEmbeddedResource(TikaInputStream resource, String filename,
                                          String relationshipID, String mediaType,
                                          XHTMLContentHandler xhtml, boolean outputHtml)
            throws IOException, SAXException, TikaException {
        handleEmbeddedResource(0, resource, filename, relationshipID, null, mediaType,
                xhtml, outputHtml);
    }
    protected void handleEmbeddedResource(int sourceId, TikaInputStream resource, String filename,
                                          String relationshipID, String mediaType, XHTMLContentHandler xhtml,
                                          boolean outputHtml)
            throws IOException, SAXException, TikaException {
        handleEmbeddedResource(sourceId, resource, filename, relationshipID, null, mediaType,
                xhtml, outputHtml);
    }

    protected void handleEmbeddedResource(TikaInputStream resource, String filename,
                                          String relationshipID, ClassID storageClassID, String mediaType,
                                          XHTMLContentHandler xhtml,
                                          boolean outputHtml)
            throws IOException, SAXException, TikaException {
        handleEmbeddedResource(0, resource, new Metadata(), filename,
                relationshipID, storageClassID, mediaType, xhtml, outputHtml);
    }

    protected void handleEmbeddedResource(int sourceId, TikaInputStream resource, String filename,
                                          String relationshipID, ClassID storageClassID, String mediaType,
                                          XHTMLContentHandler xhtml,
                                          boolean outputHtml)
            throws IOException, SAXException, TikaException {
        handleEmbeddedResource(sourceId, resource, new Metadata(), filename,
                relationshipID, storageClassID, mediaType, xhtml, outputHtml);
    }

    protected void handleEmbeddedResource(TikaInputStream resource, Metadata embeddedMetadata, String filename,
                                          String relationshipID, ClassID storageClassID, String mediaType,
                                          XHTMLContentHandler xhtml,
                                          boolean outputHtml)
            throws IOException, SAXException, TikaException {
        handleEmbeddedResource(0, resource, embeddedMetadata,filename,relationshipID,storageClassID,
                mediaType,xhtml,outputHtml);
    }

    protected void handleEmbeddedResource(int sourceId,
                                          TikaInputStream resource,
                                          Metadata embeddedMetadata,
                                          String filename,
                                          String relationshipID,
                                          ClassID storageClassID,
                                          String mediaType,
                                          XHTMLContentHandler xhtml,
                                          boolean outputHtml)
            throws IOException, SAXException, TikaException {

        try {

            if (filename != null) {
                embeddedMetadata.set(Metadata.TIKA_MIME_FILE, filename);
                embeddedMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            }
            if (relationshipID != null) {
                embeddedMetadata.set(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID, relationshipID);
            }
            if (storageClassID != null) {
                embeddedMetadata.set(TikaCoreProperties.EMBEDDED_STORAGE_CLASS_ID,
                        storageClassID.toString());
            }
            if (mediaType != null) {
                embeddedMetadata.set(Metadata.CONTENT_TYPE, mediaType);
            }

            if (embeddedDocumentUtil.shouldParseEmbedded(embeddedMetadata)) {
                embeddedDocumentUtil.parseEmbedded(resource, xhtml, embeddedMetadata, outputHtml);
            }
        } finally {
            resource.close();
        }
    }

    /**
     * Handle an office document that's embedded at the POIFS level
     */
    protected void handleEmbeddedOfficeDoc(DirectoryEntry dir, XHTMLContentHandler xhtml)
            throws IOException, SAXException, TikaException {
        handleEmbeddedOfficeDoc(0, dir, null, xhtml);
    }

    protected void handleEmbeddedOfficeDoc(int sourceId,
                                           DirectoryEntry dir, XHTMLContentHandler xhtml)
            throws IOException, SAXException, TikaException {
        handleEmbeddedOfficeDoc(sourceId, dir, null, xhtml);
    }
    /**
     * Handle an office document that's embedded at the POIFS level
     */
    protected void handleEmbeddedOfficeDoc(DirectoryEntry dir,
                                           String resourceName,
                                           XHTMLContentHandler xhtml)
            throws IOException, SAXException, TikaException {
        handleEmbeddedOfficeDoc(0, dir, resourceName, xhtml);
    }
    /**
     * Handle an office document that's embedded at the POIFS level
     */
    protected void handleEmbeddedOfficeDoc(int sourceId,
            DirectoryEntry dir, String resourceName, XHTMLContentHandler xhtml)
            throws IOException, SAXException, TikaException {

        // Is it an embedded OLE2 document, or an embedded OOXML document?
        //first try for ooxml
        Entry ooxml = dir.hasEntry("Package") ? dir.getEntry("Package") :
                (dir.hasEntry("package") ? dir.getEntry("package") : null);

        if (ooxml != null) {
            // It's OOXML (has a ZipFile):
            try (TikaInputStream stream = TikaInputStream
                    .get(new DocumentInputStream((DocumentEntry) ooxml))) {
                Detector detector = new DefaultZipContainerDetector();
                MediaType type = null;
                try {
                    //if there's a stream error while detecting...
                    type = detector.detect(stream, new Metadata());
                } catch (Exception e) {
                    EmbeddedDocumentUtil.recordEmbeddedStreamException(e, parentMetadata);
                    return;
                }
                handleEmbeddedResource(sourceId, stream, null, dir.getName(),
                        dir.getStorageClsid(), type.toString(), xhtml, true);
                return;
            }
        }

        // It's regular OLE2:

        // What kind of document is it?
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID, dir.getName());
        if (dir.getStorageClsid() != null) {
            metadata.set(TikaCoreProperties.EMBEDDED_STORAGE_CLASS_ID,
                    dir.getStorageClsid().toString());
        }
        POIFSDocumentType type = POIFSDocumentType.detectType(dir);
        TikaInputStream embedded = null;
        String rName = (resourceName == null) ? dir.getName() : resourceName;
        try {
            if (type == POIFSDocumentType.OLE10_NATIVE) {
                try {
                    // Try to un-wrap the OLE10Native record:
                    Ole10Native ole = Ole10Native.createFromEmbeddedOleObject((DirectoryNode) dir);
                    if (ole.getLabel() != null) {
                        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY,
                                rName + '/' + ole.getLabel());
                    } else {
                        metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, rName);
                    }
                    if (ole.getCommand() != null) {
                        metadata.add(TikaCoreProperties.ORIGINAL_RESOURCE_NAME, ole.getCommand());
                    }
                    if (ole.getFileName() != null) {
                        metadata.add(TikaCoreProperties.ORIGINAL_RESOURCE_NAME, ole.getFileName());
                    }
                    byte[] data = ole.getDataBuffer();
                    embedded = TikaInputStream.get(data);
                } catch (Ole10NativeException ex) {
                    // Not a valid OLE10Native record, skip it
                } catch (Exception e) {
                    EmbeddedDocumentUtil.recordEmbeddedStreamException(e, parentMetadata);
                    return;
                }
            } else if (type == POIFSDocumentType.COMP_OBJ) {
                try {
                    //TODO: figure out if the equivalent of OLE 1.0's
                    //getCommand() and getFileName() exist for OLE 2.0 to populate
                    //TikaCoreProperties.ORIGINAL_RESOURCE_NAME

                    // Grab the contents and process
                    DocumentEntry contentsEntry;
                    try {
                        contentsEntry = (DocumentEntry) dir.getEntry("CONTENTS");
                    } catch (FileNotFoundException ioe) {
                        contentsEntry = (DocumentEntry) dir.getEntry("Contents");
                    }
                    byte[] contents;
                    try (DocumentInputStream inp = new DocumentInputStream(contentsEntry)) {
                        contents = new byte[contentsEntry.getSize()];
                        inp.readFully(contents);
                    }
                    embedded = TikaInputStream.get(contents);

                    // Try to work out what it is
                    MediaType mediaType = getDetector().detect(embedded, new Metadata());
                    String extension = type.getExtension();
                    try {
                        MimeType mimeType = getMimeTypes().forName(mediaType.toString());
                        extension = mimeType.getExtension();
                    } catch (MimeTypeException mte) {
                        // No details on this type are known
                    }

                    // Record what we can do about it
                    metadata.set(Metadata.CONTENT_TYPE, mediaType.getType());
                    metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, rName + extension);
                } catch (Exception e) {
                    EmbeddedDocumentUtil.recordEmbeddedStreamException(e, parentMetadata);
                    return;
                }
            } else {
                metadata.set(Metadata.CONTENT_TYPE, type.getType().toString());
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY,
                        rName + '.' + type.getExtension());
            }

            // Should we parse it?
            if (embeddedDocumentUtil.shouldParseEmbedded(metadata)) {
                if (embedded == null) {
                    // Make a TikaInputStream that just
                    // passes the root directory of the
                    // embedded document, and is otherwise
                    // empty (byte[0]):
                    embedded = TikaInputStream.get(new byte[0]);
                    embedded.setOpenContainer(dir);
                }
                embeddedDocumentUtil.parseEmbedded(embedded, xhtml, metadata, true);
            }
        } catch (IOException e) {
            EmbeddedDocumentUtil.recordEmbeddedStreamException(e, metadata);
        } finally {
            if (embedded != null) {
                embedded.close();
            }
        }
    }
}
