package net.geoprism.geoai.explorer.core.config;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:application.properties", ignoreResourceNotFound = true)
@ComponentScan("net.geoprism.geoai.explorer.core")
@EnableAutoConfiguration
@AutoConfigurationPackage(basePackages = "net.geoprism.geoai.explorer.core")
public class CoreConfiguration
{

}