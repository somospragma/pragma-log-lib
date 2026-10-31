package co.com.pragma.logging.logback;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Resolves the value of the "host" field once, at encoder start (FLD-12). */
final class HostName {

    private HostName() {
    }

    /** HOSTNAME (set by Kubernetes and most shells), COMPUTERNAME on Windows, then the local host name. */
    static String resolve() {
        for (String variable : new String[] {"HOSTNAME", "COMPUTERNAME"}) {
            String value = System.getenv(variable);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
