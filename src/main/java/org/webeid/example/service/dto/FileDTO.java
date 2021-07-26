/*
 * Copyright (c) 2020 The Web eID Project
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.webeid.example.service.dto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import org.webeid.example.web.rest.SigningController;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

public class FileDTO {
    private static final Logger LOG = LoggerFactory.getLogger(FileDTO.class);
    private static final String EXAMPLE_FILENAME = "example-for-signing.txt";
    private static final String EXAMPLE_TEXT = "This is an example text file for testing digital signing.";

    private final String name;
    private String contentType;
    private byte[] contentBytes;

    private String uri, ocspStatus;

    public void setUri(String uri) {
        this.uri = uri;
    }

    public void setOcspStatus(String ocspStatus) {
        this.ocspStatus = ocspStatus;
    }

    public FileDTO(String name) {
        this.name = name;
    }

    public FileDTO(String name, String uri, String ocspStatus){
        this.name = name;
        this.ocspStatus = ocspStatus;
        this.uri = uri;
    }

    public FileDTO(String name, String contentType, byte[] contentBytes) {
        this.name = name;
        this.contentType = contentType;
        this.contentBytes = contentBytes;
    }

    public static FileDTO fromMultipartFile(MultipartFile file) throws IOException {
        return new FileDTO(
                Objects.requireNonNull(file.getOriginalFilename()),
                Objects.requireNonNull(file.getContentType()),
                Objects.requireNonNull(file.getBytes())
        );
    }

    public static FileDTO getExampleForSigningFromResources() throws IOException {
        try {
            return new FileDTO(
                    EXAMPLE_FILENAME,
                    MimeTypeUtils.TEXT_PLAIN_VALUE,
                    EXAMPLE_TEXT.getBytes()
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getContentBytes() {
        return contentBytes;
    }
}
