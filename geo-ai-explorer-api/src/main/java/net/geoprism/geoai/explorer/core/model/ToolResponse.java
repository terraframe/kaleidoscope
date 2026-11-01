package net.geoprism.geoai.explorer.core.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ToolResponse
{
  private String name;

  private String text;

  private String statement;
}
