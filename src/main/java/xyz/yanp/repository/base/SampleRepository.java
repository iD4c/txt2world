package xyz.yanp.repository.base;

import xyz.yanp.entity.base.BaseEntity;

import java.util.List;

public interface SampleRepository {

	<T extends BaseEntity> List<T> sample(T t);

	<T extends BaseEntity> Long sampleCount(T t);

	<T extends BaseEntity> List<Object> distinct(T t, String field);

	void batchUpdate(List<?> list);
}
