package net.geoprism.geoai.explorer.core.model;

import java.util.LinkedList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionData
{
  private String     templateId;

  private Long       projectId;

  private String     name;

  private List<Long> factIds = new LinkedList<>();

  private String     notes;
}
