package net.geoprism.geoai.explorer.core.model;

import java.util.LinkedList;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReportTemplate
{
  private String        id;

  private String        name;

  @Builder.Default
  private List<Section> sections = new LinkedList<>();
}
