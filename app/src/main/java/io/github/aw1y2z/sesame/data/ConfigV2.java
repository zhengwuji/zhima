package io.github.aw1y2z.sesame.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.entity.UserEntity;
import io.github.aw1y2z.sesame.util.*;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class ConfigV2 {

    private static final String TAG = ConfigV2.class.getSimpleName();

    public static final ConfigV2 INSTANCE = new ConfigV2();

    @JsonIgnore
    private boolean init;

    private final Map<String, ModelFields> modelFieldsMap = new ConcurrentHashMap<>();

    /**
     * 上次成功落盘的 JSON 内容（key 为 userId，空串代表默认配置）。
     * <p>用于跳过"内容没变"的重复写盘与滚动备份：原实现在任务热路径上每次保存都要
     * 读整份文件 + 全量序列化 + 全文比较，并且成功后再做一次全量滚动备份。
     */
    private static final Map<String, String> LAST_SAVED_JSON = new ConcurrentHashMap<>();

    public void setModelFieldsMap(Map<String, ModelFields> newModels) {
        modelFieldsMap.clear();
        Map<String, ModelConfig> modelConfigMap = ModelTask.getModelConfigMap();
        if (newModels == null) {
            newModels = new HashMap<>();
        }
        for (ModelConfig modelConfig : modelConfigMap.values()) {
            String modelCode = modelConfig.getCode();
            ModelFields newModelFields = new ModelFields();
            ModelFields configModelFields = modelConfig.getFields();
            ModelFields modelFields = newModels.get(modelCode);
            if (modelFields != null) {
                for (ModelField<?> configModelField : configModelFields.values()) {
                    ModelField<?> modelField = modelFields.get(configModelField.getCode());
                    try {
                        if (modelField != null) {
                            Object value = modelField.getValue();
                            if (value != null) {
                                configModelField.setObjectValue(value);
                            }
                        }
                    } catch (Exception e) {
                        Log.printStackTrace(e);
                    }
                    newModelFields.addField(configModelField);
                }
            } else {
                for (ModelField<?> configModelField : configModelFields.values()) {
                    newModelFields.addField(configModelField);
                }
            }
            modelFieldsMap.put(modelCode, newModelFields);
        }
    }

    public Boolean hasModelFields(String modelCode) {
        return modelFieldsMap.containsKey(modelCode);
    }

    public ModelFields getModelFields(String modelCode) {
        return modelFieldsMap.get(modelCode);
    }

    public void removeModelFields(String modelCode) {
        modelFieldsMap.remove(modelCode);
    }

    /*public void addModelFields(String modelCode, ModelFields modelFields) {
        modelFieldsMap.put(modelCode, modelFields);
    }*/

    public Boolean hasModelField(String modelCode, String fieldCode) {
        ModelFields modelFields = modelFieldsMap.get(modelCode);
        if (modelFields == null) {
            return false;
        }
        return modelFields.containsKey(fieldCode);
    }

    /*public ModelField getModelField(String modelCode, String fieldCode) {
        ModelFields modelFields = modelFieldsMap.get(modelCode);
        if (modelFields == null) {
            return null;
        }
        return modelFields.get(fieldCode);
    }*/

    /*public void removeModelField(String modelCode, String fieldCode) {
        ModelFields modelFields = getModelFields(modelCode);
        if (modelFields == null) {
            return;
        }
        modelFields.remove(fieldCode);
    }*/

    /*public Boolean addModelField(String modelCode, ModelField modelField) {
        ModelFields modelFields = getModelFields(modelCode);
        if (modelFields == null) {
            return false;
        }
        modelFields.put(modelCode, modelField);
        return true;
    }*/

    /*@SuppressWarnings("unchecked")
    public <T extends ModelField> T getModelFieldExt(String modelCode, String fieldCode) {
        return (T) getModelField(modelCode, fieldCode);
    }*/

    public static synchronized Boolean isModify(String userId) {
        String formatted = INSTANCE.toSaveStr();
        if (formatted == null) {
            return true;
        }
        // 先在内存里比：与上次成功落盘的内容一致 → 直接判定"无改动"，
        // 省掉原先每次都要做的一次整文件读取 + 全量序列化
        String key = StringUtil.isEmpty(userId) ? "" : userId;
        if (formatted.equals(LAST_SAVED_JSON.get(key))) {
            return false;
        }
        String json = null;
        File configV2File;
        if (StringUtil.isEmpty(userId)) {
            configV2File = FileUtil.getDefaultConfigV2File();
        } else {
            configV2File = FileUtil.getConfigV2File(userId);
        }
        if (configV2File.exists()) {
            json = FileUtil.readFromFile(configV2File);
        }
        if (json != null) {
            return !formatted.equals(json);
        }
        return true;
    }

    /**
     * 保存配置。
     *
     * <p>性能：原实现每次都要「读整份文件 + 全量序列化 + 全文比较」，保存成功后还要无条件做一次
     * 滚动备份（又一次全量写），而 {@code MessageUtil}、{@code AntForestV2}、{@code Status}
     * 等任务热路径都会调用它，造成明显的写放大与耗电。现在：
     * <ul>
     *     <li>内容与上次成功落盘的一致时，既不写盘也不备份，直接返回（force 参数此时已无意义）；</li>
     *     <li>写盘改走 {@link FileUtil#write2FileAtomic}（临时文件 + rename），避免半截 JSON；</li>
     *     <li>只有内容真的变了才滚动备份。</li>
     * </ul>
     *
     * @param userId 账号标识，空表示默认配置
     * @param force 保留以兼容既有调用方；内容一致时不再有"强制写盘"语义
     */
    public static Boolean save(String userId, Boolean force) {
        String json = INSTANCE.toSaveStr();
        if (json == null) {
            Log.error("配置序列化失败，跳过保存: " + userId);
            return false;
        }
        String key = StringUtil.isEmpty(userId) ? "" : userId;
        if (json.equals(LAST_SAVED_JSON.get(key))) {
            return true;
        }
        boolean success;
        if (StringUtil.isEmpty(userId)) {
            userId = "默认";
            success = FileUtil.write2FileAtomic(json, FileUtil.getDefaultConfigV2File());
        } else {
            success = FileUtil.write2FileAtomic(json, FileUtil.getConfigV2File(userId));
        }

        if (success) {
            LAST_SAVED_JSON.put(key, json);
            // ========== 内容确实变化才滚动备份 ==========
            FileUtil.backupConfigV2WithRolling(userId);
        }

        Log.record("保存配置: " + userId);
        return success;
    }
    
    public static synchronized ConfigV2 load(String userId) {
        Log.i(TAG, "开始加载配置");
        String userName = "";
        File configV2File = null;
        try {
            if (StringUtil.isEmpty(userId)) {
                configV2File = FileUtil.getDefaultConfigV2File();
                userName = "默认";
            } else {
                configV2File = FileUtil.getConfigV2File(userId);
                UserEntity userEntity = UserIdMap.get(userId);
                if (userEntity == null) {
                    userName = userId;
                } else {
                    userName = userEntity.getShowName();
                }
            }
            Log.record("加载配置: " + userName);
            if (configV2File.exists()) {
                String json = FileUtil.readFromFile(configV2File);
                JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(json);
                String formatted = INSTANCE.toSaveStr();
                if (formatted != null && !formatted.equals(json)) {
                    Log.i(TAG, "格式化配置: " + userName);
                    FileUtil.write2File(formatted, configV2File);
                }
            } else {
                File defaultConfigV2File = FileUtil.getDefaultConfigV2File();
                if (defaultConfigV2File.exists()) {
                    String json = FileUtil.readFromFile(defaultConfigV2File);
                    JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(json);
                    Log.i(TAG, "复制新配置: " + userName);
                    FileUtil.write2File(json, configV2File);
                } else {
                    INSTANCE.setModelFieldsMap(null);
                    unload();
                    Log.i(TAG, "初始新配置: " + userName);
                    FileUtil.write2File(INSTANCE.toSaveStr(), configV2File);
                }
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            Log.i(TAG, "重置配置: " + userName);
            INSTANCE.setModelFieldsMap(null);
            unload();
            if (configV2File != null) {
                FileUtil.write2File(INSTANCE.toSaveStr(), configV2File);
            }
        }
        INSTANCE.setInit(true);
        // 预热"上次落盘内容"缓存：刚加载完的配置不应被判为"有改动"而立刻写盘
        LAST_SAVED_JSON.put(StringUtil.isEmpty(userId) ? "" : userId, INSTANCE.toSaveStr());
        Log.i(TAG, "加载配置结束");
        return INSTANCE;
    }

    public static synchronized void unload() {
        for (ModelFields modelFields : INSTANCE.modelFieldsMap.values()) {
            for (ModelField<?> modelField : modelFields.values()) {
                if (modelField != null) {
                    modelField.reset();
                }
            }
        }
    }

    public String toSaveStr() {
        return JsonUtil.toFormatJsonString(this);
    }

}
