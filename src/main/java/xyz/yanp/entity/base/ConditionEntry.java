package xyz.yanp.entity.base;

import lombok.Data;

import java.util.List;

@Data
public class ConditionEntry {

    private String entryType; // join、normal、exclude，默认normal

    // normal
    private String subject; // 主语

    private String copula; // 系动词

    private List<String> values; // 宾语
    // normal end

    // join
    private String tableName; // join表名

    private List<ConditionEntry> sonConditions; // join表的筛选条件

    private String slaveTableKey;  // join表的key,用于和主表联结

    private String masterTableKey;  // 主表中的key,用于和join表联结
    // join end

    // common
    private String logicWithPrevious; // 与上一个entry的逻辑关系，默认and

    private Boolean close; // 条件的右括号

    private String closeLogic; // 与前一个括号的逻辑关系，默认and

    private Boolean ignoreFlg; // 忽略这个Entry的flag
    // common end
}
