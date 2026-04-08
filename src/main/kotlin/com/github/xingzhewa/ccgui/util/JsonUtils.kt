package com.github.xingzhewa.ccgui.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import java.io.InputStreamReader
import java.io.StringReader

/**
 * JSON 工具类
 *
 * 提供 Gson 的封装，简化 JSON 序列化/反序列化操作
 *
 * 扩展埋点:
 *   - 后续可添加日期格式配置
 *   - 后续可添加自定义序列化器
 */
object JsonUtils {

    /**
     * 默认 Gson 实例
     * 使用标准配置，支持 null 值
     */
    val gson: Gson = GsonBuilder()
        .serializeNulls()
        .setPrettyPrinting()
        .create()

    /**
     * 紧凑格式 Gson 实例（无缩进）
     * 用于网络传输等需要小体积的场景
     */
    val compactGson: Gson = GsonBuilder()
        .serializeNulls()
        .create()

    // ==================== 解析方法 ====================

    /**
     * 解析 JSON 字符串为 JsonObject
     *
     * @param json JSON 字符串
     * @return JsonObject，解析失败返回 null
     */
    fun parseObject(json: String): JsonObject? {
        return try {
            JsonParser.parseString(json)?.asJsonObject
        } catch (e: Exception) {
            logger<JsonUtils>().warn("Failed to parse JSON object: ${e.message}")
            null
        }
    }

    /**
     * 解析 JSON 字符串为 JsonArray
     *
     * @param json JSON 字符串
     * @return JsonArray，解析失败返回 null
     */
    fun parseArray(json: String): JsonArray? {
        return try {
            JsonParser.parseString(json)?.asJsonArray
        } catch (e: Exception) {
            logger<JsonUtils>().warn("Failed to parse JSON array: ${e.message}")
            null
        }
    }

    /**
     * 解析 JSON 字符串为 JsonElement
     *
     * @param json JSON 字符串
     * @return JsonElement，解析失败返回 null
     */
    fun parseElement(json: String): JsonElement? {
        return try {
            JsonParser.parseString(json)
        } catch (e: Exception) {
            logger<JsonUtils>().warn("Failed to parse JSON element: ${e.message}")
            null
        }
    }

    /**
     * 从 InputStream 解析 JSON
     *
     * @param inputStream 输入流
     * @param charset 字符集，默认 UTF-8
     * @return JsonElement，解析失败返回 null
     */
    fun parseFromStream(inputStream: java.io.InputStream, charset: java.nio.charset.Charset = Charsets.UTF_8): JsonElement? {
        return try {
            InputStreamReader(inputStream, charset).use { reader ->
                JsonParser.parseReader(reader)
            }
        } catch (e: Exception) {
            logger<JsonUtils>().warn("Failed to parse JSON from stream: ${e.message}")
            null
        }
    }

    // ==================== 反序列化方法 ====================

    /**
     * 将 JSON 字符串反序列化为指定类型
     *
     * @param json JSON 字符串
     * @param clazz 目标类型
     * @return 目标类型实例，解析失败返回 null
     */
    fun <T> fromJson(json: String, clazz: Class<T>): T? {
        return try {
            gson.fromJson(json, clazz)
        } catch (e: Exception) {
            logger<JsonUtils>().warn("Failed to deserialize JSON: ${e.message}")
            null
        }
    }

    /**
     * 将 JSON 字符串反序列化为指定类型（带泛型）
     *
     * @param json JSON 字符串
     * @param typeOfT 泛型类型标记
     * @return 目标类型实例，解析失败返回 null
     */
    fun <T> fromJson(json: String, typeOfT: TypeToken<T>): T? {
        return try {
            gson.fromJson(json, typeOfT.type)
        } catch (e: Exception) {
            logger<JsonUtils>().warn("Failed to deserialize JSON: ${e.message}")
            null
        }
    }

    /**
     * 将 JsonElement 反序列化为指定类型
     *
     * @param element JsonElement
     * @param clazz 目标类型
     * @return 目标类型实例
     */
    fun <T> fromJson(element: JsonElement, clazz: Class<T>): T? {
        return try {
            gson.fromJson(element, clazz)
        } catch (e: Exception) {
            logger<JsonUtils>().warn("Failed to deserialize JsonElement: ${e.message}")
            null
        }
    }

    /**
     * 将 JsonReader 反序列化为指定类型
     *
     * @param reader JsonReader
     * @param clazz 目标类型
     * @return 目标类型实例
     */
    fun <T> fromJson(reader: JsonReader, clazz: Class<T>): T? {
        return try {
            gson.fromJson(reader, clazz)
        } catch (e: Exception) {
            logger<JsonUtils>().warn("Failed to deserialize from JsonReader: ${e.message}")
            null
        }
    }

    // ==================== 序列化方法 ====================

    /**
     * 将对象序列化为 JSON 字符串（带缩进）
     *
     * @param obj 目标对象
     * @return JSON 字符串
     */
    fun toJson(obj: Any): String {
        return gson.toJson(obj)
    }

    /**
     * 将对象序列化为紧凑格式 JSON 字符串（无缩进）
     *
     * @param obj 目标对象
     * @return JSON 字符串
     */
    fun toCompactJson(obj: Any): String {
        return compactGson.toJson(obj)
    }

    // ==================== 辅助方法 ====================

    /**
     * 将 Map 转换为 JsonObject
     *
     * @param map 原始 Map
     * @return JsonObject
     */
    fun toJsonObject(map: Map<String, Any?>): JsonObject {
        val jsonObject = JsonObject()
        map.forEach { (key, value) ->
            jsonObject.add(key, toJsonElement(value))
        }
        return jsonObject
    }

    /**
     * 将 List 转换为 JsonArray
     *
     * @param list 原始 List
     * @return JsonArray
     */
    fun toJsonArray(list: List<Any?>): JsonArray {
        val jsonArray = JsonArray()
        list.forEach { item ->
            jsonArray.add(toJsonElement(item))
        }
        return jsonArray
    }

    /**
     * 将任意值转换为 JsonElement
     *
     * @param value 任意值
     * @return JsonElement
     */
    fun toJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull.INSTANCE
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Char -> JsonPrimitive(value)
            is JsonElement -> value
            is Collection<*> -> toJsonArray(value.mapNotNull { it })
            is Array<*> -> toJsonArray(value.mapNotNull { it }.toList())
            else -> gson.toJsonTree(value)
        }
    }

    /**
     * 检查字符串是否为有效的 JSON
     *
     * @param json JSON 字符串
     * @return true 表示有效
     */
    fun isValidJson(json: String): Boolean {
        return try {
            JsonParser.parseString(json)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 创建空的 JsonObject
     *
     * @return JsonObject
     */
    fun emptyObject(): JsonObject = JsonObject()

    /**
     * 创建空的 JsonArray
     *
     * @return JsonArray
     */
    fun emptyArray(): JsonArray = JsonArray()

    /**
     * 深拷贝 JsonObject
     *
     * @param obj 原始对象
     * @return 拷贝后的对象
     */
    fun deepCopy(obj: JsonObject): JsonObject {
        return gson.fromJson(gson.toJson(obj), JsonObject::class.java)
    }

    /**
     * 深拷贝 JsonArray
     *
     * @param array 原始数组
     * @return 拷贝后的数组
     */
    fun deepCopy(array: JsonArray): JsonArray {
        return gson.fromJson(gson.toJson(array), JsonArray::class.java)
    }
}
