package xyz.yanp.global;

import java.util.HashMap;
import java.util.Map;

public class TableInfo {

    public static Map<String, MyTable> name2TableMap = new HashMap<>();

    static {
        for (MyTable table : MyTable.values()) {
            name2TableMap.put(table.getTableNameInDataBase(), table);
        }
    }

    public static Class getClzByName(String name) {
        return name2TableMap.get(name).getClz();
    }

    public static MyTable getByName(String name) {
        return name2TableMap.get(name);
    }

    public enum MyTable {

        ;

        private final String tableNameInDataBase;

        private final Class clz;

        private final String belongsModule;

        private final String pks; // 主键 split with comma

        private final String uks; // 唯一键 split with comma

        MyTable(String tableNameInDataBase, Class clz, String belongsModule, String pks, String uks) {
            this.tableNameInDataBase = tableNameInDataBase;
            this.clz = clz;
            this.belongsModule = belongsModule;
            this.pks = pks;
            this.uks = uks;
        }

        public String getTableNameInDataBase() {
            return tableNameInDataBase;
        }

        public Class getClz() {
            return clz;
        }

        public String getBelongsModule() {
            return belongsModule;
        }

        public String getPks() {
            return pks;
        }

        public String getUks() {
            return uks;
        }
    }
}
