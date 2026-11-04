package net.geoprism.geoai.explorer.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "project")
public class Project
{

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long   id;

  @Column(name = "name", nullable = false, unique = true)
  private String name;
}
