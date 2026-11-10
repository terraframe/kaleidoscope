package net.geoprism.geoai.explorer.core.model;

import lombok.Data;

@Data
public class PromptResponse
{
  private String  text;

  private Boolean mapIt;

  private Boolean ambiguous;

  private String  reasoning;

  private String  mapStatement;
}
