package xyz.yanp.entity.wiki;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.yanp.entity.base.CommonEntity;

import javax.persistence.Column;
import javax.persistence.Entity;

@Entity
@Data
@EqualsAndHashCode(callSuper = false)
public class WikiProject extends CommonEntity {

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false)
    private Integer chunkSize;

    @Column(nullable = false)
    private Integer chapterNum;

    // 可选值：解析中、已解析、解析失败
    @Column(nullable = false, length = 20)
    private String parseStatus;
}
