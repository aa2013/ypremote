package com.yp.remote.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 单次跑步记录（id = 开始时间 epoch ms，兼作主键） */
/** One workout record (id = start time in epoch ms, also used as the key). */
data class WorkoutRecord(
    val id: Long,
    val startTime: Long,
    val durationSec: Long,
    val distanceMeter: Int,
    val kcal: Int,
    val maxSpeedKmh: Float,
)

/**
 * 跑步记录存储：SharedPreferences + JSON 数组，列表恒按开始时间倒序。
 * Workout history store: SharedPreferences + JSON array, always newest first.
 */
object WorkoutStore {

    private const val PREFS_NAME = "yp_workouts"
    private const val KEY_RECORDS = "records"

    private val _records = MutableStateFlow<List<WorkoutRecord>>(emptyList())
    val records: StateFlow<List<WorkoutRecord>> = _records

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        load()
    }

    /** 新增一条记录（重复 id 以新数据覆盖，防兜底保存与正常保存叠加） */
    fun add(record: WorkoutRecord) {
        val list = (_records.value.filterNot { it.id == record.id } + record)
            .sortedByDescending { it.startTime }
        _records.value = list
        persist(list)
    }

    fun delete(id: Long) {
        val list = _records.value.filterNot { it.id == id }
        _records.value = list
        persist(list)
    }

    fun clearAll() {
        _records.value = emptyList()
        persist(emptyList())
    }

    private fun persist(list: List<WorkoutRecord>) {
        val array = JSONArray()
        list.forEach { r ->
            array.put(
                JSONObject()
                    .put("id", r.id)
                    .put("start", r.startTime)
                    .put("duration", r.durationSec)
                    .put("distance", r.distanceMeter)
                    .put("kcal", r.kcal)
                    .put("maxSpeed", r.maxSpeedKmh.toDouble()),
            )
        }
        prefs?.edit()?.putString(KEY_RECORDS, array.toString())?.apply()
    }

    private fun load() {
        val raw = prefs?.getString(KEY_RECORDS, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                WorkoutRecord(
                    id = o.getLong("id"),
                    startTime = o.getLong("start"),
                    durationSec = o.getLong("duration"),
                    distanceMeter = o.getInt("distance"),
                    kcal = o.getInt("kcal"),
                    maxSpeedKmh = o.getDouble("maxSpeed").toFloat(),
                )
            }.sortedByDescending { it.startTime }
        }.onSuccess { _records.value = it }
    }
}
