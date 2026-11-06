package net.geoprism.geoai.explorer.core.dto;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FactDTO
{
  private Long         id;

  private String       name;

  private String       description;

  private String       text;

  private List<String> statements;

  private Long         projectId;

  private String       projectName;

  @JsonIgnore
  @AssertTrue(message = "projectName is required when projectId is not provided")
  public boolean isProjectSpecified()
  {
    return projectId != null || StringUtils.isNotBlank(projectName);
  }
}
