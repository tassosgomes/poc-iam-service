package com.platform.demo.sales.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Discovery endpoint exposing the module's declared permissions as JSON.
 *
 * <p>This endpoint is public (no authentication required) and reads
 * the {@code permissions.yaml} from the classpath at each request.
 */
@RestController
public class DiscoveryController {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    @GetMapping(value = "/.well-known/permissions", produces = MediaType.APPLICATION_JSON_VALUE)
    @SuppressWarnings("unchecked")
    public Map<String, Object> permissions() throws IOException {
        try (InputStream inputStream = new ClassPathResource("permissions.yaml").getInputStream()) {
            return YAML_MAPPER.readValue(inputStream, Map.class);
        }
    }
}
