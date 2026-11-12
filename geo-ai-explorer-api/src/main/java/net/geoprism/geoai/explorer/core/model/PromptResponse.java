package net.geoprism.geoai.explorer.core.model;

import lombok.Data;

@Data
public class PromptResponse
{
  private String  text;

  private Boolean ambiguous;

  private String  reasoning;

  private String  mapStatement;
}
