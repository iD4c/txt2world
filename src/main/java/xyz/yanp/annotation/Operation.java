package xyz.yanp.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Operation {
	/**
	 * 方法描述,可使用占位符获取参数:{{xxx}}
	 */
	String value() default "" ;

	String type() default "操作日志";

}
