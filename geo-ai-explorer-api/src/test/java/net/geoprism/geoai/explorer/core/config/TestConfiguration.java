package net.geoprism.geoai.explorer.core.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Root configuration for tests that load the core services without the web
 * Application. In production, auto-configuration comes from Application's
 * {@code @SpringBootApplication}; core-only test contexts enable it here.
 */
@Configuration
@EnableAutoConfiguration
public class TestConfiguration extends CoreConfiguration
{

}
