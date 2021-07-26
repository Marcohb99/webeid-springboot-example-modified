package org.webeid.example.service;

import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;
import okhttp3.OkHttpClient;
import org.bouncycastle.cert.ocsp.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.webeid.security.exceptions.*;
import org.webeid.security.util.OcspUrls;
import org.webeid.security.validator.ocsp.OcspRequestBuilder;
import org.webeid.security.validator.ocsp.OcspUtils;
import org.webeid.security.validator.validators.SubjectCertificateTrustedValidator;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class CertificateValidationService {

    private static final Logger LOG = LoggerFactory.getLogger(CertificateValidationService.class);
    private CertStore trustedCACertificateCertStore;
    private Set<TrustAnchor> trustedCACertificateAnchors;
    private Duration ocspRequestTimeout = Duration.ofSeconds(5L);
    private Supplier<OkHttpClient> httpClientSupplier;
    private Collection<URI> nonceDisabledOcspUrls;
    private SubjectCertificateTrustedValidator trustValidator;
    private X509Certificate certificate;

    public CertificateValidationService() {
    }

    private void initialize() throws JceException {
        if(this.httpClientSupplier == null)
            this.httpClientSupplier = Suppliers.memoize(() -> {
                return (new OkHttpClient.Builder()).connectTimeout(ocspRequestTimeout).callTimeout(ocspRequestTimeout).build();
            });

        this.nonceDisabledOcspUrls = Sets.newHashSet(OcspUrls.ESTEID_2015);
        HashSet<X509Certificate> caCerts = new HashSet(Arrays.asList(this.loadTrustedCACertificatesFromResources()));

        if(this.trustedCACertificateAnchors == null)
            this.trustedCACertificateAnchors = (Set) caCerts.stream().map((cert) -> {
                return new TrustAnchor(cert, (byte[]) null);
            }).collect(Collectors.toSet());

        if(this.trustValidator == null)
            this.trustValidator = new SubjectCertificateTrustedValidator(
                    trustedCACertificateAnchors,
                    trustedCACertificateCertStore
            );
        if(this.trustedCACertificateCertStore == null)
            try {
                this.trustedCACertificateCertStore = CertStore.getInstance("Collection", new CollectionCertStoreParameters(caCerts));
            } catch (GeneralSecurityException var3) {
                throw new JceException(var3);
            }
    }

    public void validateCertificateNotRevoked(X509Certificate certificate) throws TokenValidationException {
        initialize();
        try {
            this.certificate = certificate;
            URI uri = OcspUtils.ocspUri(certificate);
            if (uri == null) {
                throw new UserCertificateRevocationCheckFailedException("The CA/certificate doesn't have an OCSP responder");
            } else {
                boolean ocspNonceDisabled = this.nonceDisabledOcspUrls.contains(uri);
                if (ocspNonceDisabled) {
                    LOG.debug("Disabling OCSP nonce extension");
                }
                OCSPReq request = (new OcspRequestBuilder())
                        .certificate(certificate)
                        .enableOcspNonce(!ocspNonceDisabled)
                        .issuer(Objects.requireNonNull(this.validateCertificateTrusted()))
                        .build();
                LOG.debug("Sending OCSP request");
                OCSPResp response = OcspUtils.request(uri, request, this.httpClientSupplier.get());
                if (response.getStatus() != 0) {
                    throw new UserCertificateRevocationCheckFailedException("Response status: " + response.getStatus());
                } else {
                    BasicOCSPResp basicResponse = (BasicOCSPResp) response.getResponseObject();
                    SingleResp first = basicResponse.getResponses()[0];
                    CertificateStatus status = first.getCertStatus();
                    if (status instanceof RevokedStatus) {
                        RevokedStatus revokedStatus = (RevokedStatus) status;
                        throw revokedStatus.hasRevocationReason() ? new UserCertificateRevokedException("Revocation reason: " + revokedStatus.getRevocationReason()) : new UserCertificateRevokedException();
                    } else if (status instanceof UnknownStatus) {
                        throw new UserCertificateRevokedException("Unknown status");
                    } else if (status == null) {
                        LOG.debug("OCSP check result is GOOD");
                    } else {
                        throw new UserCertificateRevokedException("Status is neither good, revoked nor unknown");
                    }
                }
            }
        } catch (OCSPException | IOException | CertificateEncodingException var11) {
            throw new UserCertificateRevocationCheckFailedException(var11);
        }
    }

    private X509Certificate[] loadTrustedCACertificatesFromResources() {
        List<X509Certificate> caCertificates = new ArrayList<>();

        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");

            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

            Resource[] resources = resolver.getResources("/certs/*.crt");

            for (Resource resource : resources) {
                X509Certificate caCertificate = (X509Certificate) certFactory.generateCertificate(resource.getInputStream());
                caCertificates.add(caCertificate);
            }

            resources = resolver.getResources("/certs/spain_certs/*.cer");

            for (Resource resource : resources) {
                X509Certificate caCertificate = (X509Certificate) certFactory.generateCertificate(resource.getInputStream());
                caCertificates.add(caCertificate);
            }

        } catch (CertificateException | IOException e) {
            throw new RuntimeException("Error initializing trusted CA certificates.", e);
        }

        return caCertificates.toArray(new X509Certificate[0]);
    }

    private X509Certificate validateCertificateTrusted() throws UserCertificateNotTrustedException, JceException {
        X509CertSelector selector = new X509CertSelector();
        selector.setCertificate(certificate);

        try {
            PKIXBuilderParameters pkixBuilderParameters = new PKIXBuilderParameters(this.trustedCACertificateAnchors, selector);
            pkixBuilderParameters.setRevocationEnabled(false);
            pkixBuilderParameters.addCertStore(this.trustedCACertificateCertStore);
            CertPathBuilder certPathBuilder = CertPathBuilder.getInstance(CertPathBuilder.getDefaultType());
            PKIXCertPathBuilderResult result = (PKIXCertPathBuilderResult)certPathBuilder.build(pkixBuilderParameters);
            return result.getTrustAnchor().getTrustedCert();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException var7) {
            throw new JceException(var7);
        } catch (CertPathBuilderException var8) {
            LOG.trace("Error verifying signer's certificate {}: {}", certificate.getSubjectDN(), var8);
            throw new UserCertificateNotTrustedException();
        }
    }
}
