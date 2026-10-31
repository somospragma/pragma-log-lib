package co.com.pragma.logging.core;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Resolves the value of the "host" field (FLD-12); call it once, at startup. */
public final class HostName {

    private HostName() {
    }

    /** HOSTNAME (set by Kubernetes and most shells), COMPUTERNAME on Windows, then the local host name; {@code null} if unknown. */
    public static String resolve() {
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
