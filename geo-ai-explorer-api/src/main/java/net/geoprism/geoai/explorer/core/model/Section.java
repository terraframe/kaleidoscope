package net.geoprism.geoai.explorer.core.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Section
{
  private String name;

  private String summary;

  private String example;
}
