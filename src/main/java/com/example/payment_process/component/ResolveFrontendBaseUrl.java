package com.example.payment_process.component;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class ResolveFrontendBaseUrl {

    public String frontendBaseUrl(HttpServletRequest request) {
        // 1) Try Origin header (best)
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            return origin;
        }

        // 2) Fallback to Referer header (strip path)
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            try {
                URI uri = URI.create(referer);
                return uri.getScheme() + "://" + uri.getAuthority(); // e.g. http://127.0.0.1:57705
            } catch (IllegalArgumentException ignored) {
            }
        }

        // 3) Last fallback: server info (for non-browser clients)
        String scheme = request.getScheme();               // http / https
        String host = request.getServerName();             // 127.0.0.1 or DNS name
        int port = request.getServerPort();                // 80, 30080, etc.

        if (port == 80 || port == 443) {
            return scheme + "://" + host;
        } else {
            return scheme + "://" + host + ":" + port;
        }
    }
}
