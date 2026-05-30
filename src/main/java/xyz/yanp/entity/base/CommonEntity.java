package xyz.yanp.entity.base;

import lombok.Data;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.util.ObjectUtils;
import xyz.yanp.global.Constants;

import javax.persistence.*;
import java.text.SimpleDateFormat;
import java.util.Date;

@Data
@MappedSuperclass
public class CommonEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "myid")
    @GenericGenerator(name = "myid", strategy = "xyz.yanp.config.SnowFlakeIdGenerator")
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "create_date", length = 32)
    private String createDate;

    @Column(name = "update_date", length = 32)
    private String updateDate;

    /**
     * 表数据行新增时，自动设值createDate、updateDate、createUser、createUserName
     */
    @PrePersist
    public void defPrePersist() {

        SimpleDateFormat sdf = new SimpleDateFormat(Constants.DEFAULT_YMDHMS);
        if (ObjectUtils.isEmpty(createDate)) {
            createDate = sdf.format(new Date());
        } else {
            // 将10-13位的时间戳转化为yyyy-MM-dd HH:mm:ss格式的字符串
            String reg = "[0-9]{10,13}";
            if (createDate.matches(reg)) {
                Date date = new Date(Long.parseLong(createDate));
                createDate = sdf.format(date);
            }
        }
        updateDate = sdf.format(new Date());
    }

    /**
     * 表数据行更新时，自动更新updateDate
     */
    @PreUpdate
    public void defPreUpdate() {

        SimpleDateFormat sdf = new SimpleDateFormat(Constants.DEFAULT_YMDHMS);
        updateDate = sdf.format(new Date());
    }
}
