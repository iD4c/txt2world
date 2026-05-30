package xyz.yanp.entity.base;

import lombok.Data;

import javax.persistence.Transient;
import java.util.List;
import java.util.Set;

@Data
public class BaseEntity {

    @Transient
    private Integer pageNo;

    @Transient
    private Integer pageSize;

    @Transient
    private String sampleCopula; // Sample字段间的系动词，默认equal

    @Transient
    private String sampleFieldLogic; // Sample字段间的逻辑关系，默认and

    @Transient
    private List<SampleOrder> orders; // 结果集的排序

    @Transient
    private List<ConditionEntry> conditions; // 其他条件（不方便用Sample字段表示的条件）

    @Transient
    private String sampleExtraLogic; // Sample字段条件集合和额外条件集合之间的逻辑，默认and

    @Transient
    private Set<String> queryForeignFields; // 需要查询哪些外部字段（带ForeignField注解的字段）

    @Transient
    private Set<String> queryFields; // 指定查询本entity哪些字段
}
