package net.geoprism.geoai.explorer.core.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import net.geoprism.geoai.explorer.core.model.ReportTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Report templates used by the report generation agent, loaded from the JSON
 * files in {@code src/main/resources/report-templates/}. Each file holds one
 * {@link ReportTemplate}; add a template by adding a file. Each file's
 * "notes" record how the template was built and what still needs review.
 */
@Service
public class ReportTemplateService
{
  public static final String                PLAN_EVAL     = "PLAN_EVAL";

  public static final String                PLAN_EVAL_234 = "PLAN_EVAL_234";

  private static final String               LOCATION      = "classpath*:report-templates/*.json";

  private final Map<String, ReportTemplate> templates;

  public ReportTemplateService()
  {
    this.templates = Collections.unmodifiableMap(load());
  }

  private static Map<String, ReportTemplate> load()
  {
    JsonMapper mapper = JsonMapper.shared();
    Map<String, ReportTemplate> map = new LinkedHashMap<>();

    try
    {
      for (Resource resource : new PathMatchingResourcePatternResolver().getResources(LOCATION))
      {
        try (InputStream stream = resource.getInputStream())
        {
          ReportTemplate template = mapper.readValue(stream, ReportTemplate.class);

          if (map.putIfAbsent(template.getId(), template) != null)
          {
            throw new IllegalStateException("Duplicate report template id [" + template.getId() + "] in " + resource);
          }
        }
      }
    }
    catch (IOException e)
    {
      throw new UncheckedIOException("Unable to load report templates from " + LOCATION, e);
    }

    return map;
  }

  public List<ReportTemplate> getAll()
  {
    return List.copyOf(this.templates.values());
  }

  public ReportTemplate get(String id)
  {
    return this.templates.get(id);
  }

}
