package xyz.yanp.repository.base;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import xyz.yanp.annotation.ForeignField;
import xyz.yanp.entity.base.BaseEntity;
import xyz.yanp.entity.base.ConditionEntry;
import xyz.yanp.entity.base.SampleOrder;
import xyz.yanp.global.BusinessException;
import xyz.yanp.global.TableInfo;
import xyz.yanp.util.BaseUtil;

import javax.persistence.EntityManager;
import javax.persistence.Transient;
import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import javax.persistence.criteria.CriteriaBuilder.In;
import java.lang.reflect.Field;
import java.util.*;
import java.util.Map.Entry;

@Slf4j
@Repository
public class SampleRepositoryImpl implements SampleRepository {

    private final EntityManager em;

    @Autowired
    public SampleRepositoryImpl(EntityManager em) {
        this.em = em;
    }

    /**
     * internal implementation( page, sort )
     */
    @Override
    public <T extends BaseEntity> List<T> sample(T t) {

        Class<T> clz = (Class<T>) t.getClass();

        CriteriaBuilder cb = em.getCriteriaBuilder();

        Set<String> queryFields = t.getQueryFields();
        CriteriaQuery query;
        if (ObjectUtils.isEmpty(queryFields)) {
            query = cb.createQuery(clz);
        } else {
            query = cb.createTupleQuery();
        }

        Root<T> root = query.from(clz);

        query(t, query, root);

        // 排序
        List<SampleOrder> orders = t.getOrders();
        List<Order> buildOrders = new ArrayList<>();
        if (!ObjectUtils.isEmpty(orders)) {
            for (SampleOrder sampleOrder : orders) {
                String direction = BaseUtil.orElse(sampleOrder.getDirection(), "asc");
                String order = sampleOrder.getOrder();
                if ("desc".equals(direction)) {
                    buildOrders.add(cb.desc(root.get(order)));
                } else if ("asc".equals(direction)) {
                    buildOrders.add(cb.asc(root.get(order)));
                } else {
                    throw new RuntimeException("Order direction error!");
                }
            }
        }

        if (!ObjectUtils.isEmpty(buildOrders)) {
            query.orderBy(buildOrders.toArray(new Order[0]));
        }

        // 指定查询本entity的哪些字段
        if (!ObjectUtils.isEmpty(queryFields)) {
            List<Selection<?>> selections = new ArrayList<>();

            // 根据传入的字段名列表构建查询的 Selection
            for (String field : queryFields) {
                selections.add(root.get(field).alias(field));
            }

            // 将 Selections 添加到查询中
            query.multiselect(selections);
        }

        // 分页
        TypedQuery createQuery = em.createQuery(query);
        Integer pageNo = t.getPageNo();
        Integer pageSize = t.getPageSize();
        if (pageNo != null && pageSize != null) {
            createQuery.setFirstResult((pageNo - 1) * pageSize);
            createQuery.setMaxResults(pageSize);
        }

        List<T> resultList;
        if (ObjectUtils.isEmpty(queryFields)) {
            resultList = createQuery.getResultList();
        } else {
            List<Tuple> tuples = createQuery.getResultList();
            resultList = BaseUtil.tuples2EntityList(tuples, clz, queryFields);
        }

        setForeignField(clz, t, resultList);

        return resultList;
    }

    @Override
    public <T extends BaseEntity> Long sampleCount(T t) {

        Class<T> clz = (Class<T>) t.getClass();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<T> root = query.from(clz);

        query(t, query, root);

        query.select(cb.count(root));

        TypedQuery<Long> createQuery = em.createQuery(query);
        List<Long> totals = createQuery.getResultList();
        long total = 0L;

        for (Long element : totals) {
            total += element == null ? 0 : element;
        }

        return total;
    }

    @Override
    public <T extends BaseEntity> List<Object> distinct(T t, String field) {

        Class<T> clz = (Class<T>) t.getClass();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object> query = cb.createQuery(Object.class);
        Root<T> root = query.from(clz);

        query(t, query, root);

        query.select(root.get(field)).distinct(true);
        TypedQuery<Object> createQuery = em.createQuery(query);
        return createQuery.getResultList();
    }

    @Override
    @Transactional
    public void batchUpdate(List<?> list) {
        int count = 0;
        for (Object obj : list) {
            em.merge(obj);
            count++;
            if (count % 50 == 0 || count == list.size()) {
                try {
                    em.flush();
                } catch (Exception e) {
                    log.error("数据批量保存时出错！", e);
                    throw new BusinessException(500, "数据批量保存时出错！");
                } finally {
                    em.clear();
                }
            }
        }
    }

    private <T extends BaseEntity> void query(T t, CriteriaQuery query, Root<T> root) {
        // 所有带Transient注解的字段都忽略Sample字段间的条件编辑
        Set<String> ignoreFields = new HashSet<>();
        Class<T> clz = (Class<T>) t.getClass();
        for (Field f : clz.getDeclaredFields()) {
            Transient annotation = f.getAnnotation(Transient.class);
            if (annotation != null) {
                ignoreFields.add(f.getName());
            }
        }
        for (Field f : BaseEntity.class.getDeclaredFields()) {
            Transient annotation = f.getAnnotation(Transient.class);
            if (annotation != null) {
                ignoreFields.add(f.getName());
            }
        }

        // 生成的JSONObject会去除null字段
        JSONObject jsonObj = JSON.parseObject(JSON.toJSONString(t));

        // 条件编辑
        // Sample字段间的系动词
        String sampleCopula = BaseUtil.orElse(t.getSampleCopula(), "equal");
        // entity字段的条件语句编辑
        List<Predicate> entityPs = new ArrayList<>();
        for (Entry<String, Object> entry : jsonObj.entrySet()) {
            String k = entry.getKey();
            Object v = entry.getValue();

            if (ignoreFields.contains(k)) {
                continue;
            }
            if (v instanceof Comparable) {
                Predicate predicate = getPredicateByCopula(root, k, sampleCopula, (Comparable) v);
                if (predicate != null) {
                    entityPs.add(predicate);
                }
            }
        }
        Predicate generalP = null;
        if (!ObjectUtils.isEmpty(entityPs)) {
            // entity对象字段间逻辑关系
            String logicWithSampleField = BaseUtil.orElse(t.getSampleFieldLogic(), "and");
            generalP = logicLink(logicWithSampleField, entityPs.toArray(new Predicate[0]));
        }

        // 额外条件
        List<ConditionEntry> conditions = t.getConditions();
        Predicate extraP = conditionEdit(root, conditions);

        // where
        String sampleExtraLogic = BaseUtil.orElse(t.getSampleExtraLogic(), "and");
        Predicate finalP = logicLink(sampleExtraLogic, generalP, extraP);
        if (finalP != null) {
            query.where(finalP);
        }
    }

    private <T extends BaseEntity> void setForeignField(Class<T> clz, T t, List<T> resultList) {
        if (ObjectUtils.isEmpty(resultList)) {
            return;
        }

        Set<String> queryForeignFields = t.getQueryForeignFields();
        if (ObjectUtils.isEmpty(queryForeignFields)) {
            return;
        }
        for (Field f : clz.getDeclaredFields()) {
            ForeignField annotation = f.getAnnotation(ForeignField.class);
            if (annotation != null && queryForeignFields != null && queryForeignFields.contains(f.getName())) {
                String masterTableKey = annotation.value();
                Class slaveTableClz = annotation.slaveTable();
                String slaveTableKey = annotation.slaveTableKey();
                String slaveTableField = annotation.slaveTableField();
                String sampleQueryConditionField = annotation.sampleQueryConditionField();

                List slaveList = new ArrayList();

                // 判断子表查询的条件的描述方式，以sampleQuery描述还是只有masterTableKey和slaveTableKey
                if ("".equals(sampleQueryConditionField)) {
                    // 查询从表
                    Set<String> colSet = BaseUtil.getColSet(resultList, item -> {
                        JSONObject itemJsonObj = (JSONObject) JSONObject.toJSON(item);
                        String o = (String) itemJsonObj.get(masterTableKey);
                        return o;
                    });

                    BaseEntity sampleEntity = getSampleEntity(slaveTableKey, "in",
                        new ArrayList<>(colSet), slaveTableClz);
                    List<BaseEntity> list = this.sample(sampleEntity);
                    slaveList = list;
                } else {
                    for (Field toFindField : clz.getDeclaredFields()) {
                        String name = toFindField.getName();
                        if (sampleQueryConditionField.equals(name)) {
                            toFindField.setAccessible(true);
                            BaseEntity o = null;
                            try {
                                o = (BaseEntity) toFindField.get(t);
                            } catch (IllegalAccessException e) {
                                continue;
                            }
                            List sampleQueryRes = this.sample(o);
                            slaveList = sampleQueryRes;
                        }
                    }
                }

                Map map;
                // 如果slaveTableField为空，则直接将从表为主表字段赋值，而不用具体取从表某个字段
                if (ObjectUtils.isEmpty(slaveTableField)) {
                    // 判断该字段的类型是不是list
                    Class<?> fType = f.getType();
                    if (List.class.isAssignableFrom(fType)) {
                        map = BaseUtil.group2Map(slaveList, item -> {
                            JSONObject itemJsonObj = (JSONObject) JSONObject.toJSON(item);
                            return itemJsonObj.get(slaveTableKey);
                        });
                    } else {
                        map = BaseUtil.toMap(slaveList, item -> {
                            JSONObject itemJsonObj = (JSONObject) JSONObject.toJSON(item);
                            return itemJsonObj.get(slaveTableKey);
                        }, item -> item);
                    }
                } else {
                    map = BaseUtil.toMap(slaveList, item -> {
                        JSONObject itemJsonObj = (JSONObject) JSONObject.toJSON(item);
                        return BaseUtil.initValIfNull(itemJsonObj.get(slaveTableKey));
                    }, item -> {
                        JSONObject itemJsonObj = (JSONObject) JSONObject.toJSON(item);
                        return BaseUtil.initValIfNull(itemJsonObj.get(slaveTableField));
                    });
                }

                f.setAccessible(true);
                for (T result : resultList) {
                    JSONObject resultJsonObj = (JSONObject) JSONObject.toJSON(result);
                    Object o = resultJsonObj.get(masterTableKey);
                    Object value = map.get(o);
                    if (value != null) {
                        try {
                            f.set(result, value);
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                            log.error("级联设值时出错！", e);
                            throw new BusinessException(500, "级联设值时出错！");
                        }
                    }
                }
            }
        }
    }

    private static BaseEntity getSampleEntity(String subject, String copula, List<String> values, Class clz) {
        JSONObject condition = new JSONObject();
        condition.put("subject", subject);
        condition.put("copula", copula);
        condition.put("values", values);
        JSONArray conditions = new JSONArray();
        conditions.add(condition);

        String jsonString = null;
        try {
            jsonString = JSON.toJSONString(clz.newInstance(), SerializerFeature.WriteMapNullValue);
        } catch (Exception e) {
            log.error("entity对象有问题！", e);
        }
        JSONObject jsonObject = JSON.parseObject(jsonString);
        for (String s : jsonObject.keySet()) {
            jsonObject.put(s, null);
        }
        jsonObject.put("conditions", conditions);
        BaseEntity sampleEntity = (BaseEntity) JSON.toJavaObject(jsonObject, clz);
        return sampleEntity;
    }

    private Predicate checkPlaceHolderPredicate(String copula) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        if ("alwaysTrue".equals(copula)) {
            return cb.conjunction();
        }

        if ("alwaysFalse".equals(copula)) {
            return cb.disjunction();
        }

        return null;
    }

    private <T> Predicate getPredicateByCopula(Root<T> root, String subject, String copula, List<String> values) {
        Predicate checkRes = checkPlaceHolderPredicate(copula);
        if (checkRes != null) {
            return checkRes;
        }

        Path<Object> predicateKey = root.get(subject);
        Class<?> javaType = predicateKey.getJavaType();
        // 将字符串的值list转为所需的基本型数组
        Comparable[] vArr = (Comparable[]) BaseUtil.parse(javaType, values);
        return getPredicateByCopula(root, subject, copula, vArr);
    }

    private <T> Predicate getPredicateByCopula(Root<T> root, String subject, String copula, Comparable... v) {
        if (ObjectUtils.isEmpty(copula)) {
            throw new BusinessException(500, "谓词不能为空！");
        }

        CriteriaBuilder cb = em.getCriteriaBuilder();

        Predicate checkRes = checkPlaceHolderPredicate(copula);
        if (checkRes != null) {
            return checkRes;
        }

        if ("in".equals(copula) && v.length == 1) {
            // 如果谓词是in，且值只有一个，则优化为equal
            copula = "equal";
        }

        Path predicateKey = root.get(subject);

        switch (copula) {
            case "<":
                return cb.lessThan(predicateKey, v[0]);
            case ">=":
                return cb.greaterThanOrEqualTo(predicateKey, v[0]);
            case "<=":
                return cb.lessThanOrEqualTo(predicateKey, v[0]);
            case ">":
                return cb.greaterThan(predicateKey, v[0]);
            case "notEqual":
                return cb.notEqual(predicateKey, v[0]);
            case "like":
                return cb.like(predicateKey, BaseUtil.toString(v[0]));
            case "notLike":
                return cb.notLike(predicateKey, BaseUtil.toString(v[0]));
            case "equal":
                return cb.equal(predicateKey, v[0]);
            case "isNull":
                return cb.isNull(predicateKey);
            case "isNotNull":
                return cb.isNotNull(predicateKey);
            case "in":
                In<Object> in = cb.in(predicateKey);
                for (Comparable<?> val : v) {
                    in.value(BaseUtil.toString(val));
                }
                return in;
            default:
                return null;
        }
    }

    private Predicate logicLink(String logic, Predicate... ps) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        List<Predicate> exceptNull = new ArrayList<>();
        for (Predicate p : ps) {
            if (p != null) {
                exceptNull.add(p);
            }
        }
        if (ObjectUtils.isEmpty(exceptNull)) {
            return null;
        }
        Predicate[] restrictions = exceptNull.toArray(new Predicate[0]);

        if ("and".equals(logic)) {
            return cb.and(restrictions);
        } else if ("or".equals(logic)) {
            return cb.or(restrictions);
        } else {
            throw new RuntimeException("logic error!");
        }
    }

    private <T extends BaseEntity> Predicate conditionEdit(Root<T> root,
                                                           List<ConditionEntry> conditionEntrys) {
        Predicate conditionL = null;
        Predicate conditionR = null;
        if (!ObjectUtils.isEmpty(conditionEntrys)) {
            for (int i = 0; i < conditionEntrys.size(); i++) {

                ConditionEntry conditionEntry = conditionEntrys.get(i);

                // 如果ignoreFlg被置为true，则忽略这个Entry
                if (conditionEntry.getIgnoreFlg() != null && conditionEntry.getIgnoreFlg()) {
                    continue;
                }

                Predicate currentP;

                String entryType = BaseUtil.orElse(conditionEntry.getEntryType(), "normal");
                if ("normal".equals(entryType)) {
                    String subject = conditionEntry.getSubject();
                    String copula = conditionEntry.getCopula();
                    List<String> values = BaseUtil.orElse(conditionEntry.getValues(), new ArrayList<>());
                    currentP = getPredicateByCopula(root, subject, copula, values);
                } else if ("join".equals(entryType)) {
                    currentP = conditionJoin2P(root, conditionEntry);
                } else {
                    throw new RuntimeException("EntryType error!");
                }

                Boolean close = conditionEntry.getClose();
                if (conditionR == null) {
                    conditionR = currentP;
                    // 处理条件之间的括号
                    if (close != null) {
                        conditionL = closeProc(conditionL, conditionR, conditionEntry.getCloseLogic());
                        conditionR = null;
                    }
                    continue;
                }

                // 处理与前一个条件的逻辑
                String logicWithPrevious = BaseUtil.orElse(conditionEntry.getLogicWithPrevious(), "and");
                conditionR = logicLink(logicWithPrevious, conditionR, currentP);

                // 处理条件之间的括号
                if (close != null) {
                    conditionL = closeProc(conditionL, conditionR, conditionEntry.getCloseLogic());
                    conditionR = null;
                    continue;
                }

                // 如果最后一个没有括号并且前面有过括号处理，那最后conditionR需要和conditionL括号处理
                if (i == conditionEntrys.size() - 1 && conditionL != null) {
                    conditionL = closeProc(conditionL, conditionR, conditionEntry.getCloseLogic());
                    conditionR = null;
                    continue;
                }
            }
        }
        return BaseUtil.orElse(conditionL, conditionR);
    }

    /**
     * 把join的需求转化为in(subquery)
     */
    private <T extends BaseEntity> Predicate conditionJoin2P(Root<T> root, ConditionEntry conditionEntry) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        String tableName = conditionEntry.getTableName();
        List<ConditionEntry> sonConditions = conditionEntry.getSonConditions();
        String slaveTableKey = conditionEntry.getSlaveTableKey();
        String masterTableKey = conditionEntry.getMasterTableKey();

        Class slaveTableClz = TableInfo.getClzByName(tableName);
        CriteriaQuery<Tuple> tupleQuery = cb.createTupleQuery();
        Subquery subquery = tupleQuery.subquery(slaveTableClz);
        Root slaveTableRoot = subquery.from(slaveTableClz);

        Predicate predicate = conditionEdit(slaveTableRoot, sonConditions);
        if (predicate == null) {
            predicate = cb.conjunction();
        }

        // 查询
        // 只用select 主表用来in的字段
        subquery.select(slaveTableRoot.get(slaveTableKey)).distinct(true);
        subquery.where(predicate);
        In<Object> in = cb.in(root.get(masterTableKey));
        in.value(subquery);

        return in;
    }

    private Predicate closeProc(Predicate conditionL, Predicate conditionR, String closeLogic) {
        if (conditionL == null) {
            return conditionR;
        } else {
            String closeLogicSafe = BaseUtil.orElse(closeLogic, "and");
            return logicLink(closeLogicSafe, conditionL, conditionR);
        }
    }
}

