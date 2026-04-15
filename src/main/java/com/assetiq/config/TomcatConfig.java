package com.assetiq.config;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tomcat Configuration for handling large HTTP headers
 * Fixes: "Request Header Fields Too Large" error in Swagger UI
 */
@Configuration
public class TomcatConfig {

    @Bean
    public TomcatServletWebServerFactory tomcatServletWebServerFactory() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        
        factory.addConnectorCustomizers(connector -> {
            // Set max HTTP request header size to 64KB
            connector.setProperty("maxHttpHeaderSize", "65536");
            // Set max number of headers
            connector.setProperty("maxHeaderCount", "500");
        });
        
        return factory;
    }
}



