package net.geoprism.geoai.explorer.core.dto;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
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

  @Size(max = 255, message = "name must be at most 255 characters")
  private String       name;

  private String       description;

  private String       text;

  private List<String> statements;

  private String       mapStatement;

  private Long         projectId;

  @Size(max = 255, message = "projectName must be at most 255 characters")
  private String       projectName;

  @JsonIgnore
  @AssertTrue(message = "projectName is required when projectId is not provided")
  public boolean isProjectSpecified()
  {
    return projectId != null || StringUtils.isNotBlank(projectName);
  }
}
