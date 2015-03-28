package morecat.domain.repository;

import morecat.domain.model.BaseEntity;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.transaction.Transactional;
import java.util.List;

/**
 * @author Yoshimasa Tanabe
 *
 * @see <a href="https://github.com/nagaseyasuhito/camellia/blob/master/src/main/java/com/github/nagaseyasuhito/camellia/dao/BaseDao.java">BaseDAO - camellia</a>
 */
public abstract class BaseRepository<T extends BaseEntity> {

  @FunctionalInterface
  protected interface Query<T, R> {
    CriteriaQuery<R> execute(CriteriaBuilder builder, CriteriaQuery<R> query, Root<T> root);
  }

  @FunctionalInterface
  protected interface EntityQuery<T> extends Query<T, T> {}

  @PersistenceContext
  private EntityManager em;

  protected abstract Class<T> getEntityClass();

  protected T getSingleResult(EntityQuery<T> query) {
    return createQuery(query, getEntityClass()).getSingleResult();
  }

  protected <R> R getSingleResult(Query<T, R> query, Class<R> resultClass) {
    return createQuery(query, resultClass).getSingleResult();
  }

  protected List<T> getResultList(EntityQuery<T> query) {
    return createQuery(query, getEntityClass()).getResultList();
  }

  protected List<T> getResultList(EntityQuery<T> query, int firstResult, int maxResults) {
    return createQuery(query, getEntityClass()).setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
  }

  private <R> TypedQuery<R> createQuery(Query<T, R> query, Class<R> resultClass) {
    CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
    CriteriaQuery<R> criteriaQuery = criteriaBuilder.createQuery(resultClass);
    Root<T> root = criteriaQuery.from(getEntityClass());

    return em.createQuery(query.execute(criteriaBuilder, criteriaQuery, root));
  }

  public List<T> findAll() {
    return getResultList((builder, query, root) ->
        query.select(root)
    );
  }

  public long count() {
    return this.getSingleResult((builder, query, root) ->
        query.select(builder.count(root)), Long.class
    );
  }

  @Transactional
  public T save(T entity) {
    if (entity.getId() == null) {
      em.persist(entity);
      return entity;
    } else {
      return em.merge(entity);
    }
  }

  @Transactional
  public void delete(Long id) {
    delete(em.find(getEntityClass(), id));
  }

  @Transactional
  private void delete(T entity) {
    em.remove(entity);
  }

  @Transactional
  public void deleteAll() {
    findAll().forEach(entity -> delete(entity));
  }

}
