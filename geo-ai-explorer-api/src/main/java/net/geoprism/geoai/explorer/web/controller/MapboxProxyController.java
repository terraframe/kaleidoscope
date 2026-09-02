package net.geoprism.geoai.explorer.web.controller;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/mapbox")
public class MapboxProxyController {

    private static final String MAPBOX_BASE_URL = "https://api.mapbox.com";

    private static final Set<String> ALLOWED_RESPONSE_HEADERS = Set.of(
        "content-type",
//        "content-encoding", // Are we dealing with encoded responses?
        "cache-control",
        "etag",
        "last-modified",
        "expires"
    );

    private final HttpClient httpClient;
    private final String mapboxKey;

    public MapboxProxyController(@Value("${mapbox.key}") String mapboxKey) {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        this.mapboxKey = mapboxKey;
    }

    @GetMapping("/**")
    public ResponseEntity<byte[]> proxyMapboxGet(
            HttpServletRequest request,
            @RequestParam MultiValueMap<String, String> queryParams
    ) throws IOException, InterruptedException {
      
      String requestUri = request.getRequestURI();

      String mapboxPath = requestUri.replaceFirst("^/api/mapbox", "");

      URI targetUri = buildMapboxUri(mapboxPath, queryParams);

      HttpRequest outboundRequest = HttpRequest.newBuilder()
              .uri(targetUri)
              .GET()
              .build();

      HttpResponse<byte[]> mapboxResponse = httpClient.send(
              outboundRequest,
              HttpResponse.BodyHandlers.ofByteArray()
      );

      HttpHeaders responseHeaders = new HttpHeaders();

      mapboxResponse.headers().map().forEach((name, values) -> {
          if (ALLOWED_RESPONSE_HEADERS.contains(name.toLowerCase())) {
              responseHeaders.put(name, values);
          }
      });

      return ResponseEntity
              .status(mapboxResponse.statusCode())
              .headers(responseHeaders)
              .body(mapboxResponse.body());
    }

    private URI buildMapboxUri(String path, MultiValueMap<String, String> queryParams) {
        StringBuilder uri = new StringBuilder(MAPBOX_BASE_URL);
        uri.append(path);

        boolean hasQuery = false;

        for (String name : queryParams.keySet()) {
            // Never let the browser/client override your server-side token.
            if ("access_token".equalsIgnoreCase(name)) {
                continue;
            }

            for (String value : queryParams.get(name)) {
                uri.append(hasQuery ? '&' : '?');
                uri.append(urlEncode(name));
                uri.append('=');
                uri.append(urlEncode(value));
                hasQuery = true;
            }
        }

        uri.append(hasQuery ? '&' : '?');
        uri.append("access_token=");
        uri.append(urlEncode(mapboxKey));

        return URI.create(uri.toString());
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}