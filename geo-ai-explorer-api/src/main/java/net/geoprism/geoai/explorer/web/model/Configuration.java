package net.geoprism.geoai.explorer.web.model;

import java.util.List;
import java.util.Map;

import lombok.Data;
import net.geoprism.geoai.explorer.core.model.ReportTemplate;
import net.geoprism.geoai.explorer.core.model.Style;
import net.geoprism.geoai.explorer.core.model.VectorLayer;

@Data
public class Configuration
{
  private Map<String, Style> styles;

  private List<VectorLayer>  layers;

  private List<ReportTemplate> reportTemplates;

}
