package xyz.yanp.util;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartFile;
import xyz.yanp.global.BusinessException;
import xyz.yanp.global.Constants;

import javax.persistence.Tuple;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaBuilder.In;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class BaseUtil {

    // 匹配末尾数字，但仅最后一位可以是字母
    private static final Pattern sufNumPtn = Pattern.compile("[0-9]+[a-z]?$");

    private static final Pattern sufNumPtn2 = Pattern.compile("(\\d+)$");

    /**
     * 随机生成数字
     */
    public static String randomCreateCode(Integer len) {

        int num = (int) Math.pow(10, len - 1);
        Integer code = (int) ((Math.random() * 9 + 1) * num);

        return code.toString();
    }

    /**
     * 对象拷贝方法，过滤为null的属性
     */
    public static void copyPropertiesIgnoreNull(Object src, Object target) {

        BeanUtils.copyProperties(src, target, getNullPropertyNames(src));
    }

    public static String toString(Object obj) {

        return (obj == null) ? "" : obj.toString();
    }

    public static String toString2(Object obj) {

        return (obj == null) ? null : obj.toString();
    }

    public static String fixLen3ToString(Object obj) {
        String res = "000";
        if (obj == null) {
            return res;
        }

        String s = res + obj;
        s = s.substring(s.length() - 3);
        return s;
    }

    public static void ensureDirExist(String dirStr) {
        File dir = new File(dirStr);
        if (!dir.exists()) {
            boolean flg = dir.mkdirs();
        }
    }

    public static void ensureFileExist(File file) {

        if (!file.exists()) {
            ensureDirExist(file.getParent());
            try {
                file.createNewFile();
            } catch (IOException e) {
                throw new BusinessException(500, "ensureFileExist时出错！");
            }
        }
    }

    public static void ensureEmptyDirExist(String dirStr) {

        File dir = new File(dirStr);

        try {
            if (dir.exists()) {
                FileUtils.deleteDirectory(dir);
            }
            dir.mkdirs();
        } catch (IOException e) {
            throw new BusinessException(500, "ensureEmptyDirExist时出错！");
        }
    }

    public static void addInPredicate(List<Predicate> pArr,
                                      Set<String> valList,
                                      Root<?> root,
                                      CriteriaBuilder cb,
                                      String attributeName) {

        if (!ObjectUtils.isEmpty(valList)) {
            In<Object> in = cb.in(root.get(attributeName));
            for (String val : valList) {
                in.value(val);
            }
            pArr.add(in);
        }
    }

    public static In getInPredicate(Set<String> valList,
                                    Root<?> root,
                                    CriteriaBuilder cb,
                                    String attributeName) {

        if (!ObjectUtils.isEmpty(valList)) {
            In<Object> in = cb.in(root.get(attributeName));
            for (String val : valList) {
                in.value(val);
            }
            return in;
        }
        return null;
    }

    public static <T, E> Set<E> getColSet(List<T> list, Function<T, E> mapper) {

        return list.stream().map(mapper).collect(Collectors.toSet());
    }

    public static <T, E> List<E> getColList(List<T> list, Function<T, E> mapper) {

        return list.stream().map(mapper).collect(Collectors.toList());
    }

    public static <T, E> Map<E, List<T>> group2Map(List<T> list, Function<T, E> mapper) {

        return list.stream().collect(Collectors.groupingBy(mapper, Collectors.toList()));
    }

    public static <T, K, V> Map<K, V> toMap(List<T> list,
                                            Function<? super T, ? extends K> keyMapper,
                                            Function<? super T, ? extends V> valueMapper) {

        return list.stream().collect(Collectors.toMap(keyMapper, valueMapper, (o1, o2) -> o2));
    }

    public static <T> List<T> filter(List<T> list, java.util.function.Predicate<T> predicate) {

        return list.stream().filter(predicate).collect(Collectors.toList());
    }

    public static String getCSV(Collection<String> list) {

        if (ObjectUtils.isEmpty(list)) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (String str : list) {
            sb.append(str);
            sb.append(",");
        }
        String str = sb.toString();
        return str.substring(0, str.length() - 1);
    }

    public static <T> T findNextItem(List<T> list, Function<T, Boolean> findCurrentMapper) {

        boolean findCurrentFlg = false;
        T nextItem = null;
        for (T item : list) {
            if (findCurrentFlg) {
                nextItem = item;
                break;
            }

            if (findCurrentMapper.apply(item)) {
                findCurrentFlg = true;
            }
        }
        return nextItem;
    }

    public static <T> T findLastItem(List<T> list, Function<T, Boolean> findCurrentMapper) {

        int currentIdx = 0;
        int idx = -1;
        for (T item : list) {
            idx++;
            if (findCurrentMapper.apply(item)) {
                currentIdx = idx;
            }
        }
        int lastIdx = currentIdx - 1;
        if (lastIdx >= 0) {
            return list.get(lastIdx);
        } else {
            return null;
        }
    }

    public static <T> T orElse(T t, T elseVal) {
        return t == null ? elseVal : t;
    }

    public static String getCSV(Map<String, String> pCodes) {

        if (pCodes == null || pCodes.size() == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (String str : pCodes.keySet()) {
            sb.append(str);
            sb.append(",");
        }
        String str = sb.toString();
        return str.substring(0, str.length() - 1);
    }

    /**
     * 将字符串的值list转为基本型数组
     */
    public static <T> T[] parse(Class<T> clz, List<String> values) {
        if (ObjectUtils.isEmpty(values))
            return null;

        if (clz == String.class)
            return (T[]) values.toArray(new String[0]);


        T[] valuesTrans = (T[]) Array.newInstance(clz, values.size());
        for (int i = 0; i < values.size(); i++) {
            valuesTrans[i] = (T) parse(clz, values.get(i));
        }

        return valuesTrans;
    }

    /**
     * 将字符串的值转为基本型
     */
    public static <T> T parse(Class<T> clz, String val) {
        if (val == null)
            return null;

        if (clz == String.class)
            return (T) val;

        if (clz != Short.class && clz != Integer.class && clz != Long.class && clz != Float.class
            && clz != Double.class && clz != Boolean.class)
            throw new RuntimeException("参数错误，只能为Short、Integer、Long、Float、Double、Boolean、String！");

        Object invoke;
        try {
            Method declaredMethod = clz.getDeclaredMethod("valueOf", String.class);
            invoke = declaredMethod.invoke(null, val);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            log.error("将字符串的值转为基本型时出错！", e);
            throw new RuntimeException("将字符串的值转为基本型时出错");
        }
        return (T) invoke;
    }

    public static <K, V> void putIf(Map<K, V> map, K k, V newV, Consumer<V> mappingFunction) {
        V v;
        if ((v = map.get(k)) == null) {
            map.put(k, newV);
        } else {
            mappingFunction.accept(v);
        }
    }

    public static String getTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat(Constants.DEFAULT_YMDHMS_2);
        return sdf.format(new Date());
    }

    public static void openFileByFullPath(String fullPath) {
        Runtime runtime = Runtime.getRuntime();
        String cmd = "rundll32 url.dll FileProtocolHandler file://" + fullPath + "";
        try {
            Process process = runtime.exec(cmd);
            int exitValue = process.waitFor();
            if (exitValue == 0) {
                // ok,do nothing
            }
        } catch (final Exception e) {
            log.error("", e);
        }
    }

    /**
     * 对象拷贝时调用，替换null属性
     */
    public static String[] getNullPropertyNames(Object source) {

        final BeanWrapper src = new BeanWrapperImpl(source);
        java.beans.PropertyDescriptor[] pds = src.getPropertyDescriptors();

        Set<String> emptyNames = new HashSet<>();
        for (java.beans.PropertyDescriptor pd : pds) {
            Object srcValue = src.getPropertyValue(pd.getName());
            if (srcValue == null)
                emptyNames.add(pd.getName());
        }
        String[] result = new String[emptyNames.size()];
        return emptyNames.toArray(result);
    }

    public static String getFullPath(String parentPath, String rankInBros) {
        return ObjectUtils.isEmpty(parentPath) ? rankInBros : parentPath + "-" + rankInBros;
    }

    public static void deleteDiskFile(String fullPath) {
        File file = new File(fullPath);
        if (file.exists()) {
            try {
                Files.delete(file.toPath());
            } catch (IOException e) {
                log.error("删除文件时出错！", e);
                throw new BusinessException(500, "删除文件时出错！");
            }
        }
    }

    /**
     * 用srcObj中的对应字段的值覆盖distObj，跳过srcObj中值为null的字段
     */
    public static <T> T copyFieldVal1(Object srcObj, Object distObj, Class<T> clz) {
        JSONObject srcJsonObject = (JSONObject) JSONObject.toJSON(srcObj);
        JSONObject distJSONObject = (JSONObject) JSONObject.toJSON(distObj);
        for (Map.Entry<String, Object> entry : srcJsonObject.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            distJSONObject.put(key, value);
        }

        return distJSONObject.toJavaObject(clz);
    }

    /**
     * 用srcObj中的对应字段的值覆盖distObj，不跳过srcObj中值为null的字段
     */
    public static <T> T copyFieldVal2(Object srcObj, Object distObj, Class<T> clz) {
        JSONObject jsonObject = (JSONObject) JSONObject.toJSON(srcObj);
        JSONObject distJSONObject = (JSONObject) JSONObject.toJSON(distObj);
        for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            distJSONObject.put(key, value);
        }

        return distJSONObject.toJavaObject(clz);
    }

    /**
     * 用srcObj中的对应字段的值覆盖distObj，不跳过srcObj中值为null的字段
     */
    public static <T> T copyFieldVal3(Object srcObj, Class<T> clz) throws IllegalAccessException, InstantiationException {
        JSONObject jsonObject = (JSONObject) JSONObject.toJSON(srcObj);
        JSONObject distJSONObject = (JSONObject) JSONObject.toJSON(clz.newInstance());
        for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            distJSONObject.put(key, value);
        }

        return distJSONObject.toJavaObject(clz);
    }

    public static String checkOS() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "windows";
        } else if (os.contains("nix") || os.contains("nux")) {
            return "linux";
        } else {
            return "other";
        }
    }

    public static Object initValIfNull(Object obj) {
        return obj == null ? "" : obj;
    }

    private static boolean onlyContain(Collection<String> c, String... keys) {
        List<String> keyList = Arrays.asList(keys);
        for (String str : c) {
            if (!keyList.contains(str)) {
                return false;
            }
        }
        return true;
    }

    public static int getPrefixBlankLen(String no) {
        String subPrefixBlank = no.replaceAll("^ +", "");
        return no.length() - subPrefixBlank.length();
    }

    public static <T> List<T> tuples2EntityList(List<Tuple> tuples, Class<T> clz, Set<String> queryFields) {
        List<T> resultList = new ArrayList<>();
        if (ObjectUtils.isEmpty(tuples)) {
            return resultList;
        }
        for (Tuple tuple : tuples) {
            JSONObject jsonObject = new JSONObject();
            for (String queryField : queryFields) {
                jsonObject.put(queryField, tuple.get(queryField));
            }
            T resItem = jsonObject.toJavaObject(clz);
            resultList.add(resItem);
        }
        return resultList;
    }

    public static List normalizeList(Object obj) {
        List res = null;
        if (obj instanceof Map) {
            res = Arrays.asList(obj);
        } else if (obj instanceof List) {
            res = (List) obj;
        }
        return res;
    }

    public static String getSuffixNumStr(String str) {

        Matcher matcher = sufNumPtn2.matcher(str);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    public static void copyFile(File srcFile, File distFile) {
        try {
            FileUtils.copyFile(srcFile, distFile);
        } catch (IOException e) {
            e.printStackTrace();
            log.error("复制文件失败=====BaseUtil.java=====copyFile===={}", e);
        }
    }

    public static void copyDirectory(File srcDir, File distDir) {
        try {
            FileUtils.copyDirectory(srcDir, distDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getUploadFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        // 文件名check
        if (ObjectUtils.isEmpty(originalFilename)) {
            throw new BusinessException(400, "文件名不能为空！");
        }
        String[] split = originalFilename.split("\\\\");
        String filename = split[split.length - 1];
        return filename;
    }

    public static String readFileToString(File file) {
        try {
            return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(500, "读取文件时出错！");
        }
    }

    public static void writeStringToFile(File file, String s) {
        try {
            FileUtils.writeStringToFile(file, s, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(500, "写入文件时出错！");
        }
    }

    public static String filenameChgSuffix(String filename, String newSuffix) {
        String filenamePrefix = filenamePrefix(filename);

        return filenamePrefix + newSuffix;
    }

    public static String filenamePrefix(String filename) {
        int lastIndexOf = filename.lastIndexOf(".");
        return filename.substring(0, lastIndexOf);
    }

    public static void fileDelete(File f) {
        try {
            FileUtils.delete(f);
        } catch (IOException e) {
            log.error("删除文件时出错！", e);
        }
    }

    public static LocalDateTime dateTimePlus(String ymdhms) {
        // 定义日期时间的格式
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Constants.DEFAULT_YMDHMS);

        // 将字符串转换为 LocalDateTime 对象
        LocalDateTime dateTime = LocalDateTime.parse(ymdhms, formatter);

        return dateTime.plusHours(4);
    }

    public static String dateTimePlusStr(String ymdhms) {
        // 定义日期时间的格式
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Constants.DEFAULT_YMDHMS);

        LocalDateTime newDateTime = dateTimePlus(ymdhms);

        return newDateTime.format(formatter);
    }

    public static String currentTimeStr() {
        LocalDateTime currentDateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Constants.DEFAULT_YMDHMS);
        return currentDateTime.format(formatter);
    }

    public static LocalDateTime currentTime() {
        return LocalDateTime.now();
    }

    public static boolean validateTokenActive(String lastActiveTime) {
        LocalDateTime maxValid = dateTimePlus(lastActiveTime);
        LocalDateTime now = currentTime();
        return !now.isAfter(maxValid);
    }

    public static String getIpAddr(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }

        // 处理多级代理的情况，获取第一个IP
        if (ipAddress != null && ipAddress.length() > 15 && ipAddress.contains(",")) {
            ipAddress = ipAddress.substring(0, ipAddress.indexOf(","));
        }

        return ipAddress;
    }
}
