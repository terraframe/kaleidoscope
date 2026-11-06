package net.geoprism.geoai.explorer.web.config;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer, AsyncConfigurer
{
  private static final Logger logger = LoggerFactory.getLogger(WebConfiguration.class);

  @Override
  public void addCorsMappings(CorsRegistry registry)
  {
    // Spring allows only GET, HEAD and POST unless told otherwise; the API
    // also uses PUT and DELETE (e.g. deleting a fact).
    registry.addMapping("/**").allowedOrigins("*").allowedMethods("GET", "HEAD", "POST", "PUT", "DELETE");
  }

  @Override
  @Bean(name = "taskExecutor")
  public Executor getAsyncExecutor()
  {
    logger.debug("Creating Async Task Executor");

    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(10);
    executor.setQueueCapacity(1000);
    executor.setThreadNamePrefix("async");
    return executor;
  }

  // ---------------> Use this task executor also for async rest methods
  @Bean
  protected WebMvcConfigurer webMvcConfigurer()
  {
    return new WebMvcConfigurer()
    {
      @Override
      public void configureAsyncSupport(AsyncSupportConfigurer configurer)
      {
        configurer.setTaskExecutor(getTaskExecutor());
      }
    };
  }

  @Bean
  protected ConcurrentTaskExecutor getTaskExecutor()
  {
    return new ConcurrentTaskExecutor(this.getAsyncExecutor());
  }

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler()
  {
    return new SimpleAsyncUncaughtExceptionHandler();
  }
}