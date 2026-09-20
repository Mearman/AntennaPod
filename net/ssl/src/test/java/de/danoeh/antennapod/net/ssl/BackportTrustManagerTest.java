package de.danoeh.antennapod.net.ssl;

import org.junit.Before;
import org.junit.Test;

import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BackportTrustManagerTest {
    private CertificateFactory certificateFactory;
    private X509TrustManager trustManager;

    @Before
    public void setUp() throws CertificateException {
        certificateFactory = CertificateFactory.getInstance("X.509");
        trustManager = BackportTrustManager.create();
    }

    private X509Certificate parse(String pem) throws CertificateException {
        return (X509Certificate) certificateFactory.generateCertificate(
                new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
    }

    private List<String> backportPems() {
        List<String> pems = new ArrayList<>();
        pems.add(BackportCaCerts.COMODO);
        pems.add(BackportCaCerts.SECTIGO_USER_TRUST);
        pems.add(BackportCaCerts.SECTIGO_ROOT_E46);
        pems.add(BackportCaCerts.LETSENCRYPT_ISRG);
        pems.add(BackportCaCerts.GLOBALSIGN_R6);
        return pems;
    }

    @Test
    public void testCreateReturnsTrustManagerCombiningBackportedAndSystemRoots() {
        assertNotNull(trustManager);
        assertTrue(trustManager instanceof CompositeX509TrustManager);
    }

    @Test
    public void testEveryBackportedCertificateIsACertificateAuthority() throws CertificateException {
        for (String pem : backportPems()) {
            assertTrue(parse(pem).getBasicConstraints() >= 0);
        }
    }

    @Test
    public void testAcceptedIssuersIncludeEveryBackportedRoot() throws CertificateException {
        List<String> acceptedSubjects = new ArrayList<>();
        for (X509Certificate issuer : trustManager.getAcceptedIssuers()) {
            acceptedSubjects.add(issuer.getSubjectX500Principal().getName());
        }
        for (String pem : backportPems()) {
            assertTrue(acceptedSubjects.contains(parse(pem).getSubjectX500Principal().getName()));
        }
    }

    @Test
    public void testBackportedRootsAreTrustedAsServerCertificates() throws CertificateException {
        for (String pem : backportPems()) {
            X509Certificate root = parse(pem);
            trustManager.checkServerTrusted(new X509Certificate[] {root}, root.getPublicKey().getAlgorithm());
        }
    }
}
