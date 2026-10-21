package com.servicepros;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ServiceProsApplication {

    public static void main(String[] args) {
        useWindowsCertificateStore();
        SpringApplication.run(ServiceProsApplication.class, args);
    }

    /**
     * On Windows, trust the same certificates Windows trusts. Networks that inspect HTTPS install their own root
     * certificate there, and without this Java rejects calls to Twilio / Google ("PKIX path building failed").
     * Skipped when a trust store is configured explicitly (-Djavax.net.ssl.trustStore / trustStoreType).
     */
    private static void useWindowsCertificateStore() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
        if (windows && System.getProperty("javax.net.ssl.trustStore") == null
                && System.getProperty("javax.net.ssl.trustStoreType") == null) {
            System.setProperty("javax.net.ssl.trustStoreType", "Windows-ROOT");
        }
    }
}
