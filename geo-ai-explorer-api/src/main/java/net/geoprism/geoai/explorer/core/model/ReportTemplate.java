package net.geoprism.geoai.explorer.core.model;

import java.util.LinkedList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

  /**
   * Review notes for people maintaining the template: how it was built, known
   * gaps, and decisions that need confirming. Read from the template file but
   * never serialized, so they are not sent to the report generation agent.
   */
  @Builder.Default
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private List<String>  notes    = new LinkedList<>();
}
