package xyz.yanp.annotation;


import xyz.yanp.entity.base.CommonEntity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ForeignField {

    String value() default ""; // masterTableKey

    Class slaveTable() default CommonEntity.class;

    String slaveTableKey() default "";

    String slaveTableField() default "";

    String sampleQueryConditionField() default "";

    String countFlg() default "0";
}
