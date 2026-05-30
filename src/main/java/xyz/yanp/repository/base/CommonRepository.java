package xyz.yanp.repository.base;

import org.springframework.data.repository.NoRepositoryBean;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@NoRepositoryBean
public interface CommonRepository<T> extends CoreRepository<T, Serializable> {

    void deleteByIdIn(Collection<String> ids);

    List<T> findByIdIn(Collection<String> ids);

    int countByIdIn(Set<String> ids);
}
