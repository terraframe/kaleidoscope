package net.geoprism.geoai.explorer.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Section
{
  private String  name;

  private String  summary;

  private String  example;

  /**
   * Whether the section is expected in every report built from the template.
   * Optional sections may be omitted when no relevant discoveries are assigned
   * or when they do not apply to the study type.
   */
  @Builder.Default
  private boolean required = true;
}
