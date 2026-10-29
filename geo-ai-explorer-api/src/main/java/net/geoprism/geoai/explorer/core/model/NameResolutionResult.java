package net.geoprism.geoai.explorer.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NameResolutionResult
{
  private String uri;

  private String type;

  private String code;

  private String label;
}
