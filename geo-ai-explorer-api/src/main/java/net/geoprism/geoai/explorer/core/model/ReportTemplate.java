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

  /**
   * Citation of the federal regulation (or agency guidance) that defines the
   * requirements this template's sections satisfy, e.g. "33 CFR Part 234,
   * §234.9 Evaluate effects of alternatives".
   */
  private String        regulation;

  /**
   * Link to the authoritative text of {@link #regulation}.
   */
  private String        regulationUrl;

  @Builder.Default
  private List<Section> sections = new LinkedList<>();
}
