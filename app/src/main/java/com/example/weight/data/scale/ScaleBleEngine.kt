package com.example.weight.data.scale

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import com.example.weight.data.LocalStorageData
import com.example.weight.data.record.BodyComposition
import com.example.weight.data.record.BodyCompositionJson
import com.example.weight.data.record.Record
import com.example.weight.data.record.RecordDao
import com.example.weight.util.Gender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * icomon 体脂秤蓝牙引擎：扫描 → 连接 → 订阅 FFB2 通知 → 解析体重 → 去重入库。
 *
 * 整个流程由 [startSession] 驱动，状态经 [state] 暴露给设置页；
 * 稳定体重写入 Record 表后通过 [onRecordInserted] 回调通知（SnackBar/小组件刷新）。
 * 所有帧原样进 [rawLog]，未知格式留作协议逆向的现场证据。
 */
@SuppressLint("MissingPermission")
class ScaleBleEngine(
    private val context: Context,
    private val appScope: CoroutineScope,
    private val recordDao: RecordDao,
) {
    /** 会话状态机；UI 只读 */
    sealed interface State {
        /** 空闲，未在称重会话中 */
        data object Idle : State

        /** 正在扫描 icomon 广播 */
        data class Scanning(val secondsLeft: Int) : State

        /** 已锁定信号，正在建立 GATT 连接与订阅 */
        data object Connecting : State

        /** 已连接，等待上秤出数 */
        data class Ready(val deviceName: String) : State

        /** 收到测量中体重（实时值） */
        data class Measuring(val weightKg: Double) : State

        /** 体重已稳定，等待体脂结果帧（阻抗）或超时收尾 */
        data class Stabilized(val weightKg: Double) : State

        /** 本轮称重完成，已入库 */
        data class Done(
            val weightKg: Double,
            val fatRatio: Double? = null,
            val impedanceOhm: Double? = null,
        ) : State

        /** 失败/超时，message 为中文用户可读原因 */
        data class Failed(val message: String) : State

        /**
         * 身体档案不完整（性别/年龄未设置）：体成分公式缺输入，称重结果会系统性偏差，
         * 拒绝开始并引导用户先去设置页补全。体重仍可手动记录。
         */
        data class ProfileIncomplete(val missing: List<String>) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** 调试日志：每条为"方向+十六进制"，未知协议分析用 */
    private val _rawLog = MutableStateFlow<List<String>>(emptyList())
    val rawLog: StateFlow<List<String>> = _rawLog.asStateFlow()

    /** 稳定体重入库后的回调（UI 层刷新小组件等） */
    var onRecordInserted: ((Double) -> Unit)? = null

    /**
     * 最近一次称重的完整成分（含全指标）。弹窗模式（autoInsert=false）下由调用方
     * 在用户确认保存时取用；startSession 时清空。
     */
    var lastComposition: BodyComposition? = null
        private set

    /**
     * 弹窗读秤模式：完成时只解析并停在 Done，不自动写库——由记录弹窗在用户点保存时
     * 连同体重一起入库；设置页称重卡仍用默认的自动入库。
     */
    private var autoInsertOnDone = true

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter

    private var scanTimeoutJob: Job? = null
    private var gatt: BluetoothGatt? = null
    private var sessionActive = false

    // ---- GATT 常量（icomon 家族通用外形）----
    private val serviceUuid: UUID = UUID.fromString("0000ffb0-0000-1000-8000-00805f9b34fb")
    private val writeCharUuid: UUID = UUID.fromString("0000ffb1-0000-1000-8000-00805f9b34fb")
    private val notifyCharUuid: UUID = UUID.fromString("0000ffb2-0000-1000-8000-00805f9b34fb")
    private val cccDescriptorUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /**
     * 开始一次称重会话：扫描最长 60 秒，发现秤后连接并等待数据。
     * [autoInsert] false 时完成后不写库（记录弹窗读秤用，由调用方保存）。
     * 重复调用时先清理上一会话。
     * 性别/年龄未设置时直接进入 [State.ProfileIncomplete]：体成分公式的性别项与年龄项
     * 缺失会让全部指标系统性偏差（未设置性别默认按男性算），宁可拒绝也不出脏数据。
     */
    fun startSession(autoInsert: Boolean = true) {
        stopSession()
        autoInsertOnDone = autoInsert
        val bt = adapter
        when {
            bt == null -> { _state.value = State.Failed("此设备不支持蓝牙"); return }
            !bt.isEnabled -> { _state.value = State.Failed("请先打开手机蓝牙"); return }
        }
        val missing = buildList {
            if (Gender.entries.none { it.name == LocalStorageData.gender.value }) add("性别")
            if (LocalStorageData.age.value <= 0) add("年龄")
        }
        if (missing.isNotEmpty()) {
            log("档案缺失：${missing.joinToString("、")}，拒绝开始体成分测量")
            _state.value = State.ProfileIncomplete(missing)
            return
        }
        sessionActive = true
        stableWeightKg = null
        scaleReported.clear()
        lastComposition = null
        impedanceWaitJob?.cancel()
        impedanceWaitJob = null
        log("── 会话开始 ──")
        startScan()
        scanTimeoutJob = appScope.launch {
            repeat(SCAN_TIMEOUT_SECONDS) {
                delay(1000)
                val s = _state.value
                if (s is State.Scanning) _state.value = s.copy(secondsLeft = s.secondsLeft - 1)
            }
            if (_state.value is State.Scanning || _state.value is State.Connecting) {
                fail("60 秒内未找到秤，请确认秤已装电池并踩一下唤醒")
            }
        }
    }

    /** 结束会话并释放蓝牙资源；幂等 */
    fun stopSession() {
        sessionActive = false
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        releaseBluetooth()
        if (_state.value !is State.Done && _state.value !is State.Failed) {
            _state.value = State.Idle
        }
    }

    /** 停止扫描并关闭 GATT；幂等，可从任意线程调用 */
    private fun releaseBluetooth() {
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        runCatching { gatt?.close() }
        gatt = null
    }

    // ---------------- 扫描 ----------------

    private fun startScan() {
        _state.value = State.Scanning(secondsLeft = SCAN_TIMEOUT_SECONDS)
        val filters = listOf(
            ScanFilter.Builder().setDeviceName(DEVICE_NAME).build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            adapter?.bluetoothLeScanner?.startScan(filters, settings, scanCallback)
        } catch (e: Exception) {
            fail("扫描启动失败：${e.message ?: "未知错误"}")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!sessionActive) return
            if (_state.value !is State.Scanning) return
            log("广播 ${result.device.address} rssi=${result.rssi}")
            connect(result)
        }

        override fun onScanFailed(errorCode: Int) {
            fail("扫描失败（错误码 $errorCode）")
        }
    }

    // ---------------- 连接与订阅 ----------------

    @SuppressLint("MissingPermission")
    private fun connect(result: ScanResult) {
        _state.value = State.Connecting
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        val device = result.device
        // 缺 BLUETOOTH_CONNECT 运行时权限时 connectGatt 抛 SecurityException，兜底成可读错误而不是崩溃。
        // Context 版 connectGatt 自 API 37 起全部弃用，替代品 BluetoothGattConnectionSettings 仅 API 37+，
        // minSdk 29 下只能继续用此重载
        gatt = runCatching {
            @Suppress("DEPRECATION")
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }.getOrElse {
            fail("缺少蓝牙连接权限，请在系统设置中授权「附近的设备」")
            null
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    log("GATT 已连接，发现服务…")
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    log("GATT 断开 status=$status")
                    if (sessionActive && _state.value !is State.Done) {
                        fail("与秤的连接断开")
                    }
                    runCatching { g.close() }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("服务发现失败（status=$status）")
                return
            }
            val service = g.getService(serviceUuid)
            if (service == null) {
                val found = g.services.joinToString { it.uuid.toString().take(8) }
                fail("秤上没有 FFB0 服务（实际：$found）")
                return
            }
            val notifyChar = service.getCharacteristic(notifyCharUuid)
            if (notifyChar == null) {
                fail("秤上没有 FFB2 通知特征")
                return
            }
            g.setCharacteristicNotification(notifyChar, true)
            val ccc = notifyChar.getDescriptor(cccDescriptorUuid)
            if (ccc != null) {
                if (!writeCccDescriptor(g, ccc)) fail("订阅通知失败")
            } else {
                // 个别固件无 CCCD，直接视作可收通知
                onNotifyEnabled(g)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.characteristic.uuid == notifyCharUuid) {
                if (status == BluetoothGatt.GATT_SUCCESS) onNotifyEnabled(g)
                else fail("订阅通知失败（status=$status）")
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            log("写 ${characteristic.uuid.toString().take(8)} status=$status")
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleFrame(value)
        }
    }

    /**
     * 订阅 CCCD。API 33+ 走带 value 的新重载；旧系统回落到已弃用的 setter 写法（minSdk < 33），
     * 两分支行为一致，整函数抑制 DEPRECATION。
     */
    @Suppress("DEPRECATION")
    private fun writeCccDescriptor(gatt: BluetoothGatt, ccc: BluetoothGattDescriptor): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(ccc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            ccc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(ccc)
        }

    /**
     * 下发档案帧。API 33+ 走带 value/writeType 的新重载；旧系统回落到已弃用的 setter 写法，
     * 整函数抑制 DEPRECATION。
     */
    @Suppress("DEPRECATION")
    private fun writeProfileCommand(
        gatt: BluetoothGatt,
        char: BluetoothGattCharacteristic,
        payload: ByteArray,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                gatt.writeCharacteristic(char, payload, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            }.onFailure { log("档案写入异常：${it.message}") }
        } else {
            runCatching {
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                char.value = payload
                gatt.writeCharacteristic(char)
            }.onFailure { log("档案写入异常：${it.message}") }
        }
    }

    /**
     * 通知就绪：下发 AC 27 用户档案（秤收到档案才做 BIA 测阻抗），写特征用 no-response
     * （本机实测 write-default 被拒 status=3，同家族厨房秤项目亦用 no-response）。
     */
    private fun onNotifyEnabled(g: android.bluetooth.BluetoothGatt) {
        val name = g.device.name ?: DEVICE_NAME
        _state.value = State.Ready(deviceName = name)
        log("通知已开启，下发用户档案")
        val writeChar = g.getService(serviceUuid)?.getCharacteristic(writeCharUuid)
        if (writeChar == null) {
            log("无 FFB1 写特征，跳过档案下发（仅记录体重）")
            return
        }
        appScope.launch {
            val refWeight = runCatching { recordDao.getLastData()?.weight }.getOrNull() ?: 70.0
            val profile = IcomonFrameParser.buildAc27ProfileCommand(
                sexMale = LocalStorageData.gender.value != "FEMALE",
                age = LocalStorageData.age.value,
                heightCm = LocalStorageData.height.value.toInt(),
                refWeightKg = refWeight,
            )
            log("写档案 ${hex(profile)}")
            writeProfileCommand(g, writeChar, profile)
        }
    }

    // ---------------- 数据解析与入库 ----------------

    /** 稳定帧先到、阻抗结果帧晚约 3 秒：暂存稳定值，等阻抗或超时再入库（Binder 线程写、IO 协程读） */
    @Volatile
    private var stableWeightKg: Double? = null
    private var impedanceWaitJob: Job? = null

    /**
     * 变体 A 秤的自报成分（体脂率/肌肉率/骨量/水分分属两个 20 字节包）：跨帧暂存，
     * 稳定体重到齐后经 [BodyFatCalculator.resolve] 的秤自报路径入库。
     * Binder 线程写、finalize 的 IO 协程读，用并发容器防撕裂遍历。
     */
    private val scaleReported = ConcurrentHashMap<String, Double>()

    /** 档案快照：会话期间固定，避免称重中途改档案导致同一轮数据口径不一致 */
    private data class Profile(val sexMale: Boolean, val age: Int, val heightCm: Int)

    private fun handleFrame(payload: ByteArray) {
        // 会话已收尾（Done/Failed/手动停止）后秤仍会重发稳定帧：直接丢弃，防止状态机
        // 从终态回退到 Measuring/Stabilized 卡死，也省掉每帧的解析、日志与分配
        if (!sessionActive) return
        log("通知 ${hex(payload)}")
        val m = IcomonFrameParser.parse(payload)
        if (m == null) {
            log("↑ 未能识别")
            return
        }
        // 变体 A 自报成分先于状态机采集：体脂与体重同在第一包（isFinal），
        // 肌肉/骨量/水分在其后的第二包，两包都必须落进暂存
        var reportedSomething = false
        m.fatRatio?.let { scaleReported[FAT_KEY] = it; reportedSomething = true }
        m.muscleRatio?.let { scaleReported[MUSCLE_KEY] = it; reportedSomething = true }
        m.boneKg?.let { scaleReported[BONE_KEY] = it; reportedSomething = true }
        m.waterRatio?.let { scaleReported[WATER_KEY] = it; reportedSomething = true }
        if (reportedSomething) {
            log("自报成分：${scaleReported.entries.joinToString { "${it.key}=${it.value}" }}")
        }
        when {
            m.weightKg > 0 && !m.isFinal ->
                _state.value = State.Measuring(weightKg = round01(m.weightKg))

            m.isResultFrame -> {
                if (m.impedanceOhm != null && m.impedanceSourceOffset != 4) {
                    // 非主字段候选命中：协议假设可能不成立，留证据供真机核对
                    log("阻抗取自备选偏移 ${m.impedanceSourceOffset}=${m.impedanceOhm}Ω，非本机实测主字段")
                }
                impedanceWaitJob?.cancel()
                val weight = m.weightKg.takeIf { it > 0 } ?: stableWeightKg
                if (weight != null) {
                    finalize(weight, m.impedanceOhm, scaleReported)
                } else {
                    log("结果帧缺体重且无稳定值，忽略")
                }
            }

            m.weightKg > 0 && m.isFinal -> {
                stableWeightKg = m.weightKg
                _state.value = State.Stabilized(weightKg = round01(m.weightKg))
                // 变体 B：等阻抗结果帧；变体 A：等第二包成分。窗口内被结果帧提前收尾则作废
                impedanceWaitJob?.cancel()
                impedanceWaitJob = appScope.launch {
                    delay(IMPEDANCE_WAIT_MS)
                    log("等阻抗超时，按${if (scaleReported.isEmpty()) "纯体重" else "秤自报成分"}入库")
                    finalize(m.weightKg, null, scaleReported)
                }
            }

            else -> if (!reportedSomething && m.weightKg == 0.0 && !m.isResultFrame) {
                log("空帧忽略")
            }
        }
    }

    /**
     * 统一收尾：阻抗优先走 Sun 方程；无阻抗但有变体 A 自报体脂时按质量平衡补全；
     * 都没有则只记体重。[reported] 为空 map 时视作无自报成分。
     */
    private fun finalize(weightKg: Double, impedanceOhm: Double?, reported: Map<String, Double>) {
        if (!sessionActive) return
        sessionActive = false
        impedanceWaitJob?.cancel()
        // 数据已取齐（阻抗帧已到或自报成分窗口已过），及时断开：
        // 否则连接与通知流会一直活到用户离开页面，秤端持续耗电、手机端持续处理空帧
        releaseBluetooth()
        val rounded = round01(weightKg)
        appScope.launch {
            val profile = Profile(
                sexMale = Gender.entries.firstOrNull { it.name == LocalStorageData.gender.value } != Gender.FEMALE,
                age = LocalStorageData.age.value,
                heightCm = LocalStorageData.height.value.toInt(),
            )
            // 阻抗 + 身体档案（含腰围）→ 全套身体成分；自报体脂兜底；公式全部失效时仍保留原始阻抗备查
            val composition = BodyFatCalculator.resolve(
                sexMale = profile.sexMale,
                age = profile.age,
                heightCm = profile.heightCm,
                weightKg = rounded,
                impedanceOhm = impedanceOhm,
                scaleFatRatio = reported[FAT_KEY],
                waistCm = LocalStorageData.currentWaistCm.value.takeIf { it > 0 },
            )?.copy(
                impedance = impedanceOhm?.toInt() ?: 0,
            ) ?: impedanceOhm?.let { BodyComposition(impedance = it.toInt()) }
            lastComposition = composition

            if (autoInsertOnDone) {
                // 防重复：秤可能重发稳定帧，或上一次会话刚记过同值——2 分钟内同重量直接跳过
                val last = recordDao.getLastData()
                val duplicate = last != null &&
                    kotlin.math.abs(last.weight - rounded) < 0.05 &&
                    System.currentTimeMillis() - last.timestamp < DEDUP_WINDOW_MS
                if (!duplicate) {
                    recordDao.insert(
                        Record.create(
                            weight = rounded,
                            log = "",
                            timestamp = System.currentTimeMillis(),
                            composition = composition,
                        )
                    )
                } else {
                    log("与最近记录重复（${last.weight}kg），跳过入库")
                }
            } else {
                log("读秤完成，等待用户在弹窗中保存")
            }
            _state.value = State.Done(
                weightKg = rounded,
                fatRatio = composition?.fatRatio?.takeIf { it > 0 },
                impedanceOhm = impedanceOhm,
            )
            if (autoInsertOnDone) onRecordInserted?.invoke(rounded)
        }
    }

    private fun fail(message: String) {
        if (!sessionActive) return
        sessionActive = false
        scanTimeoutJob?.cancel()
        // 失败路径同样要停扫描、断连接：否则扫描超时后 LE scanner 仍在全占空比扫，
        // 订阅失败的 GATT 句柄被单例一直持有
        releaseBluetooth()
        _state.value = State.Failed(message)
    }

    /** 日志环形缓冲：Binder 线程与 IO 协程并发写入，攒批后整体推给 UI */
    private val logBuffer = ArrayDeque<String>(MAX_LOG_LINES)
    private val logLock = Any()
    private var logFlushJob: Job? = null

    private fun log(line: String) {
        // Log.d 在 MIUI 上默认被吞，用 info 级保证真机抓包可见
        android.util.Log.i("ScaleBle", line)
        synchronized(logLock) {
            if (logBuffer.size >= MAX_LOG_LINES) logBuffer.removeFirst()
            logBuffer.addLast(line)
            // 测量帧速率为每秒数包，逐条发射会让 UI 每帧重组整卡并整表拷贝；
            // 攒 LOG_FLUSH_INTERVAL_MS 一批再推，发射频率降一个数量级
            if (logFlushJob == null) {
                logFlushJob = appScope.launch {
                    delay(LOG_FLUSH_INTERVAL_MS)
                    synchronized(logLock) {
                        logFlushJob = null
                        _rawLog.value = logBuffer.toList()
                    }
                }
            }
        }
    }

    private fun round01(v: Double) = kotlin.math.round(v * 10) / 10

    private fun hex(p: ByteArray) = p.joinToString(" ") { "%02X".format(it) }

    companion object {
        const val DEVICE_NAME = "icomon"
        private const val SCAN_TIMEOUT_SECONDS = 60
        private const val MAX_LOG_LINES = 200

        /** rawLog 攒批推送间隔：远小于人眼感知阈值，只用于削掉逐帧发射的重组风暴 */
        private const val LOG_FLUSH_INTERVAL_MS = 200L

        /** 变体 A 自报成分在 [scaleReported] 里的键 */
        private const val FAT_KEY = "fat%"
        private const val MUSCLE_KEY = "muscle%"
        private const val BONE_KEY = "boneKg"
        private const val WATER_KEY = "water%"

        /** 防重复入库窗口：同一重量 2 分钟内只记一次 */
        private const val DEDUP_WINDOW_MS = 2 * 60 * 1000L

        /** 稳定体重帧后等待阻抗结果帧的窗口；超时仅按体重入库 */
        private const val IMPEDANCE_WAIT_MS = 5_000L
    }
}
