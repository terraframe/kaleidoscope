package net.geoprism.geoai.explorer.core.config;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:application.properties", ignoreResourceNotFound = true)
@ComponentScan("net.geoprism.geoai.explorer.core")
@AutoConfigurationPackage(basePackages = "net.geoprism.geoai.explorer.core")
public class CoreConfiguration
{

}