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

package org.webeid.example.service;

import com.google.common.io.ByteStreams;
import es.gob.afirma.core.signers.asic.ASiCUtil;
import es.gob.afirma.signers.xades.asic.AOXAdESASiCSSigner;
import org.apache.commons.io.FilenameUtils;
import org.digidoc4j.*;
import org.digidoc4j.impl.asic.AsicContainer;
import org.digidoc4j.impl.asic.asice.AsicEContainer;
import org.digidoc4j.utils.TokenAlgorithmSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.webeid.example.config.YAMLConfig;
import org.webeid.example.service.dto.CertificateDTO;
import org.webeid.example.service.dto.DigestDTO;
import org.webeid.example.service.dto.FileDTO;
import org.webeid.example.service.dto.SignatureDTO;
import org.webeid.example.web.rest.SigningController;
import sun.security.provider.certpath.OCSP;

import javax.servlet.http.HttpSession;
import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
//import es.gob.afirma.signers.xades.asic.XAdesASicExtraParams;

@Service
public class SpanishSigningService {

    private static final String SESSION_ATTR_FILE = "file-to-sign";
    private static final String SESSION_ATTR_CONTAINER = "container-to-sign";
    private static final String SESSION_ATTR_DATA = "data-to-sign";
    private static final Logger LOG = LoggerFactory.getLogger(SigningController.class);
    //    private final Configuration signingConfiguration;
    private Configuration signingConfiguration;

    ObjectFactory<HttpSession> httpSessionFactory;

    private static final Logger LOGGER = LoggerFactory.getLogger(SpanishSigningService.class);

    public SpanishSigningService(ObjectFactory<HttpSession> httpSessionFactory, YAMLConfig yamlConfig) {

        this.httpSessionFactory = httpSessionFactory;
        String config = yamlConfig.getUseDigiDoc4jProdConfiguration()?"TRUE" : "FALSE";
        LOG.info("getUseDigiDoc4jProdConfiguration: " + config);
        signingConfiguration = Configuration.of(yamlConfig.getUseDigiDoc4jProdConfiguration() ?
                Configuration.Mode.PROD : Configuration.Mode.TEST);
        // Use automatic AIA OCSP URL selection from certificate for signatures.
        signingConfiguration.setPreferAiaOcsp(false);
//        signingConfiguration = new Configuration(Configuration.Mode.TEST);
        signingConfiguration.setOcspSource("http://ocspusu.cert.fnmt.es/ocspusu/OcspResponder");
    }

    private HttpSession currentSession() {
        return httpSessionFactory.getObject();
    }

    /**
     * Prepares given container {@link Container} for the signature process.
     *
     * @param certificateDTO user's X.509 certificate
     * @return data to be signed
     */
    public DigestDTO prepareContainer(CertificateDTO certificateDTO) throws CertificateException, NoSuchAlgorithmException, IOException {
        FileDTO fileDTO = FileDTO.getExampleForSigningFromResources();
        Container containerToSign = getContainerToSign(fileDTO);
        String containerName = generateContainerName(fileDTO.getName());
        X509Certificate certificate = certificateDTO.toX509Certificate();

        currentSession().setAttribute(SESSION_ATTR_CONTAINER, containerToSign);
        currentSession().setAttribute(SESSION_ATTR_FILE, fileDTO);

        LOG.info("Preparing container for signing for file '{}'", containerName);

        DataToSign dataToSign = SignatureBuilder
                .aSignature(containerToSign)
                .withSignatureProfile(SignatureProfile.LT) // AIA OCSP is supported for signatures with LT or LTA profile.
                .withSigningCertificate(certificate)
                .withSignatureDigestAlgorithm(TokenAlgorithmSupport.determineSignatureDigestAlgorithm(certificate))
                .buildDataToSign();

        currentSession().setAttribute(SESSION_ATTR_DATA, dataToSign);

        LOG.info("Successfully prepared container for signing for file '{}'", containerName);

        final String digestAlgorithm = certificateDTO.getSupportedAlgorithmNames().contains("SHA384") ?
                "SHA-384" : "SHA-256";

        //Hacemos hash del fichero, el contenedor se construye después
        final byte[] digest = MessageDigest.getInstance(digestAlgorithm).digest(fileDTO.getContentBytes());

        DigestDTO digestDTO = new DigestDTO();
        digestDTO.setHash(DatatypeConverter.printBase64Binary(digest));
        digestDTO.setAlgorithm(digestAlgorithm);

        return digestDTO;
    }

    public byte[] generateContainer(byte[] signatureBytes, byte[] data, Properties xParams){
        try{
            final Properties extraParams = AOXAdESASiCSSigner.setASiCProperties(xParams, data);
            extraParams.put("keepKeyInfoUnsigned", Boolean.TRUE.toString()); //$NON-NLS-1$
            return ASiCUtil.createSContainer(
                    signatureBytes,
                    data,
                    ASiCUtil.ENTRY_NAME_XML_SIGNATURE,
                    extraParams.getProperty("asicsFilename")
            );
        }catch (IOException e){
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Signs a {@link Container} using given {@link SignatureDTO}.
     * Container to sign is taken from the current session.
     *
     * @param signatureDTO signature DTO
     * @return fileDTO
     */
    public FileDTO signContainer(SignatureDTO signatureDTO) {
        try {
            FileDTO fileDTO = (FileDTO) Objects.requireNonNull(currentSession().getAttribute(SESSION_ATTR_FILE));
//            Container containerToSign = (Container) Objects.requireNonNull(currentSession().getAttribute(SESSION_ATTR_CONTAINER));
            DataToSign dataToSign = (DataToSign) Objects.requireNonNull(currentSession().getAttribute(SESSION_ATTR_DATA));
            LOG.info("DATA TO SIGN");
            LOG.info("ocsp source: " + dataToSign.getConfiguration().getOcspSource());

            //OCSP REQUEST
            CertificateFactory fac = null;

            fac = CertificateFactory.getInstance("X509");
            FileInputStream is = new FileInputStream("D:\\Documentos\\uma\\tfg\\web_eid_modified\\web-eid-spring-boot-example-latest\\web-eid-spring-boot-example\\src\\main\\resources\\certs\\AC_FNMT_Usuarios.cer");
            X509Certificate isCert = (X509Certificate) fac.generateCertificate(is);
            is = new FileInputStream("D:\\Documentos\\uma\\tfg\\web_eid_modified\\web-eid-spring-boot-example-latest\\web-eid-spring-boot-example\\src\\main\\resources\\certs\\OCSP_AC_FNMT_Usuarios.cer");
            X509Certificate respCert = (X509Certificate) fac.generateCertificate(is);

            OCSP.RevocationStatus status = OCSP.check(
                    dataToSign.getSignatureParameters().getSigningCertificate(),
                    isCert,
                    new URI(signingConfiguration.getOcspSource()),
                    respCert,
                    null);

            LOG.info("CERT: " + dataToSign.getSignatureParameters().getSigningCertificate().getSubjectDN().getName());
            LOG.info("STATUS: " + status.getCertStatus().toString());

            byte[] signatureBytes = DatatypeConverter.parseBase64Binary(signatureDTO.getBase64Signature());

            byte[] container = this.generateContainer(signatureBytes, dataToSign.getDataToSign(), null);
            currentSession().setAttribute(SESSION_ATTR_CONTAINER, container);

            LOG.info("NAME: " + fileDTO.getName());

            return new FileDTO(generateContainerName(fileDTO.getName()));
        }
//        catch (OCSPRequestFailedException oe){
//            LOG.error("Expected");
//            return new FileDTO(generateContainerName(fileDTO.getName()));
//        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException("Signing of container caused an error");
        }catch (IOException ie){
            ie.printStackTrace();
            throw new RuntimeException("Signing of container caused an error");
        }catch (CertificateException e) {
            e.printStackTrace();
            throw new RuntimeException("Signing of container caused an error");
        } catch (CertPathValidatorException e) {
            e.printStackTrace();
            throw new RuntimeException("Signing of container caused an error");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            throw new RuntimeException("Signing of container caused an error");
        }catch (Exception ex) {
            LOG.error("Signing of container caused an error", ex);
            throw new RuntimeException("Signing of container caused an error");
        }
    }

    public String getContainerName() {
        FileDTO fileDTO = (FileDTO) Objects.requireNonNull(currentSession().getAttribute(SESSION_ATTR_FILE));
        return generateContainerName(fileDTO.getName());
    }

    public ByteArrayResource getSignedContainerAsResource() throws IOException {
        FileDTO fileDTO = (FileDTO) Objects.requireNonNull(currentSession().getAttribute(SESSION_ATTR_FILE));
        DataToSign data = (DataToSign) Objects.requireNonNull(currentSession().getAttribute(SESSION_ATTR_DATA));
        byte [] containerBytes = (byte[]) Objects.requireNonNull(currentSession().getAttribute(SESSION_ATTR_CONTAINER));
        LOGGER.debug("Saving container as stream");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);

        ZipEntry entry = new ZipEntry(fileDTO.getName());
        entry.setSize(fileDTO.getContentBytes().length);
        zos.putNextEntry(entry);
        zos.write(fileDTO.getContentBytes());
        zos.closeEntry();

        entry = new ZipEntry("container.zip");
        entry.setSize(containerBytes.length);
        zos.putNextEntry(entry);
        zos.write(containerBytes);
        zos.closeEntry();

        entry = new ZipEntry("data");
        entry.setSize(data.getDataToSign().length);
        zos.putNextEntry(entry);
        zos.write(data.getDataToSign());
        zos.closeEntry();

        zos.close();
        InputStream inputStream = new ByteArrayInputStream(baos.toByteArray());
        return new ByteArrayResource(ByteStreams.toByteArray(inputStream));
    }

    private Container getContainerToSign(FileDTO fileDTO) {
        LOG.info("Creating container for file '{}'", fileDTO.getName());

        final DataFile dataFile = new DataFile(fileDTO.getContentBytes(), fileDTO.getName(), fileDTO.getContentType());
        return ContainerBuilder
                .aContainer(Container.DocumentType.ASICE)
                .withDataFile(dataFile)
                .withConfiguration(signingConfiguration)
                .build();
    }

    private String generateContainerName(String fileName) {
        return FilenameUtils.removeExtension(fileName) + ".zip";
    }

    /*  Here is an example method that demonstrates how to handle file uploads.
     *  See also SigningController.upload().
     *
     * Creates a {@link Container} using given name and files.
     *
     * @param fileDTO container file
     * @return new {@link Container} instance
    public FileDTO createContainer(FileDTO fileDTO) {

        Container containerToSign = getContainerToSign(fileDTO);

        currentSession().setAttribute(SESSION_ATTR_CONTAINER, containerToSign);
        currentSession().setAttribute(SESSION_ATTR_FILE, fileDTO);

        FileDTO newFileDTO = new FileDTO(fileDTO.getName());
        return newFileDTO;
    }
    */
    /* When using uploaded files, retrieve file information and container from the session in prepareContainer().

    FileDTO fileDTO = (FileDTO) Objects.requireNonNull(currentSession().getAttribute(SESSION_ATTR_FILE));
    Container containerToSign = (Container) Objects.requireNonNull(currentSession().getAttribute(SESSION_ATTR_CONTAINER));
     */
}
