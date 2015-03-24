package morecat.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * @author Yoshimasa Tanabe
 */
@Entity
@Table(name = "media")
@Data
@EqualsAndHashCode(callSuper = false)
public class Media extends BaseEntity {

  @Column(nullable = false)
  @NotNull
  private String uuid;

  @Column(nullable = false)
  @NotNull
  private String name;

  @Lob
  @Column(nullable = false)
  @NotNull
  private byte[] content;

  /**
   * Keycloak user name
   */
  @Column(name = "author_name", nullable = false)
  @NotNull
  private String authorName;

  @PrePersist
  private void prePersist() {
    setUuid(UUID.randomUUID().toString());
  }

}
