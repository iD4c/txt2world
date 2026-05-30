package xyz.yanp.vo.wiki;

import lombok.Data;

@Data
public class TokenStatisticsDtl {

    // 最大token消耗
    private Integer max;
    // 最小token消耗
    private Integer min;
    // 平均token消耗
    private Integer avg;
    // token消耗标准差
    private Integer sd;
}
