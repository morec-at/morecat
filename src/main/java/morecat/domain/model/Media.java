package morecat.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
  name = "media",
  uniqueConstraints = @UniqueConstraint(columnNames = {"uuid", "created_time"})
)
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
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

  @Column(name = "author_name", nullable = false)
  @NotNull
  private String authorName;

  @Column(name = "created_time", nullable = false)
  @NotNull
  private LocalDateTime createdTimeStamp;

  public Media(String uuid, String name, String authorName, LocalDateTime createdTimeStamp) {
    this.uuid = uuid;
    this.name = name;
    this.authorName = authorName;
    this.createdTimeStamp = createdTimeStamp;
  }

  @PrePersist
  private void prePersist() {
    setUuid(UUID.randomUUID().toString());
    setCreatedTimeStamp(LocalDateTime.now());
  }

}
