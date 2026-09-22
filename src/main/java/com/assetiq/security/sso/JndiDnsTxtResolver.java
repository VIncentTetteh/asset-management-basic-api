package com.assetiq.security.sso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/** The JDK's own DNS client. Replaced in tests, and by any deployment that has none. */
@Component
public class JndiDnsTxtResolver implements DnsTxtResolver {

    private static final Logger log = LoggerFactory.getLogger(JndiDnsTxtResolver.class);

    @Override
    public List<String> txtRecords(String domain) {
        List<String> records = new ArrayList<>();
        if (domain == null || domain.isBlank()) return records;
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        try {
            InitialDirContext ctx = new InitialDirContext(env);
            try {
                Attributes attrs = ctx.getAttributes(domain, new String[] {"TXT"});
                Attribute txt = attrs.get("TXT");
                if (txt == null) return records;
                for (int i = 0; i < txt.size(); i++) {
                    Object value = txt.get(i);
                    // A long TXT record arrives as several quoted chunks to join.
                    if (value != null) records.add(value.toString().replace("\"", "").trim());
                }
            } finally {
                ctx.close();
            }
        } catch (NamingException e) {
            // No record, no zone, no DNS: all of them mean "not verified".
            log.debug("TXT lookup for {} failed: {}", domain, e.getMessage());
        }
        return records;
    }
}
