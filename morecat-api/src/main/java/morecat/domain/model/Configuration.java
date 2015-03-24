package morecat.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.validator.constraints.NotEmpty;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

/**
 * @author tanabe
 */
@Entity
@Table(name = "configurations")
@Data
@EqualsAndHashCode(callSuper = false)
public class Configuration extends BaseEntity {

  @Column(name = "blog_name", nullable = false)
  @NotEmpty
  private String blogName;

  @Column(name = "blog_description", nullable = false)
  @NotNull
  private String blogDescription;

  @Column(nullable = false)
  @NotNull
  private boolean publicity;

}
