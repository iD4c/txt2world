package xyz.yanp.vo;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import xyz.yanp.entity.base.BaseEntity;

import java.util.List;

/**
 * 2000正常响应；
 * 2001返回msg(前端会弹确认框显示msg的信息)；
 * 2002返回msg（前端会弹右上角短暂显示框显示msg的信息）；
 * 5000服务器内部错误；
 * 4000客户端错误;
 * 401未登录;
 */
public class R extends JSONObject {

    public R() {
    }

    public R putData(String k, Object v) {
        JSONObject data = this.getJSONObject("data");
        if (data == null) {
            data = new JSONObject();
            this.put("data", data);
        }

        data.put(k, v);
        return this;
    }

    public static R ok() {
        R r = new R();
        r.put("code", 2000);
        r.put("msg", "success");
        return r;
    }

    public static R okObject(JSONObject data) {
        R r = new R();
        r.put("code", 2000);
        r.put("msg", "success");

        r.put("data", data);
        return r;
    }

    public static R okObject(Object data) {
        data = JSON.toJSON(data);

        R r = new R();
        r.put("code", 2000);
        r.put("msg", "success");

        r.put("data", data);
        return r;
    }

    public static R okTable(Long totalElements, List<? extends BaseEntity> content) {
        R r = new R();
        r.put("code", 2000);
        r.put("msg", "success");

        JSONObject data = new JSONObject();
        data.put("totalElements", totalElements);
        data.put("content", content);
        r.put("data", data);
        return r;
    }

    public static R res2001(String msg) {
        R r = new R();
        r.put("code", 2001);
        r.put("msg", msg);
        return r;
    }

    public static R res2002(String msg) {
        R r = new R();
        r.put("code", 2002);
        r.put("msg", msg);
        return r;
    }

    public static R res4000(String msg) {
        R r = new R();
        r.put("code", 4000);
        r.put("msg", msg);
        return r;
    }

    public static R res401() {
        R r = new R();
        r.put("code", 401);
        r.put("msg", "未登录！");
        return r;
    }

    public static R res5000(String msg) {
        R r = new R();
        r.put("code", 5000);
        r.put("msg", msg);
        return r;
    }
}
